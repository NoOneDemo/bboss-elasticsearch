package org.frameworkset.elasticsearch.client.estodb;
/**
 * Copyright 2008 biaoping.yin
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.frameworkset.common.poolman.DBUtil;
import com.frameworkset.common.poolman.NestedSQLException;
import com.frameworkset.common.poolman.StatementInfo;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.client.TaskCommand;
import org.frameworkset.elasticsearch.client.TaskFailedException;
import org.frameworkset.elasticsearch.client.context.ImportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * <p>Description: </p>
 * <p></p>
 * <p>Copyright (c) 2018</p>
 * @Date 2019/3/1 11:32
 * @author biaoping.yin
 * @version 1.0
 */
public class TaskCommandImpl implements TaskCommand<List<List<ES2DBDataTran.Param>>, String> {
	private String sql;

	public TaskCommandImpl(String sql, ImportContext importContext, List<List<ES2DBDataTran.Param>> datas) {
		this.sql = sql;
//		this.batchsize = importContext.getStoreBatchSize();
		this.importContext = importContext;
		this.datas = datas;
	}

	public ImportContext getImportContext() {
		return importContext;
	}

	public void setImportContext(ImportContext db2ESImportContext) {
		this.importContext = db2ESImportContext;
	}

	private ImportContext importContext;





	public List<List<ES2DBDataTran.Param>> getDatas() {
		return datas;
	}


	private List<List<ES2DBDataTran.Param>> datas;
	private int tryCount;



	public void setDatas(List<List<ES2DBDataTran.Param>> datas) {
		this.datas = datas;
	}
	private static Logger logger = LoggerFactory.getLogger(TaskCommandImpl.class);

	private void debugDB(String name){
		DBUtil.debugStatus(name);

//		java.util.List<AbandonedTraceExt> traceobjects = DBUtil.getGoodTraceObjects(name);
//		for(int i = 0; traceobjects != null && i < traceobjects.size() ; i ++){
//			AbandonedTraceExt abandonedTraceExt = traceobjects.get(i);
//			if(abandonedTraceExt.getStackInfo() != null)
//				logger.info(abandonedTraceExt.getStackInfo());
//		}
//		logger.info("{}",traceobjects);
	}
	public String execute(){
		String data = null;
		if(this.importContext.getMaxRetry() > 0){
			if(this.tryCount >= this.importContext.getMaxRetry())
				throw new TaskFailedException("task execute failed:reached max retry times "+this.importContext.getMaxRetry());
		}
		this.tryCount ++;
		long start = System.currentTimeMillis();

		StatementInfo stmtInfo = null;
		PreparedStatement statement = null;
		Connection con_ = null;
		int batchsize = importContext.getStoreBatchSize();
		try {

//		GetCUDResult CUDResult = null;
			String dbname = importContext.getDbConfig().getDbName();
//			logger.info("DBUtil.getConection(dbname)");
//			debugDB(dbname);
			con_ = DBUtil.getConection(dbname);
			stmtInfo = new StatementInfo(dbname,
					null,
					false,
					con_,
					false);
			stmtInfo.init();


			statement = stmtInfo
					.prepareStatement(sql);
			if(batchsize <= 1 ) {//如果batchsize被设置为0或者1直接一次性批处理所有记录
				for(List<ES2DBDataTran.Param> record:datas){
					for(int i = 0;i < record.size(); i ++)
					{
						ES2DBDataTran.Param param = record.get(i);
						statement.setObject(param.getIndex(),param.getValue());
					}
					try {
						statement.addBatch();
					}
					catch (SQLException e){
						throw new NestedSQLException(record.toString(),e);
					}
				}
				statement.executeBatch();
			}
			else
			{
				int point = batchsize - 1;
				int count = 0;
				for(List<ES2DBDataTran.Param> record:datas) {
					for (int i = 0; i < record.size(); i++) {
						ES2DBDataTran.Param param = record.get(i);
						statement.setObject(param.getIndex(), param.getValue());
					}
					statement.addBatch();
					if ((count > 0 && count % point == 0)) {
						statement.executeBatch();
						statement.clearBatch();
						count = 0;
						continue;
					}
					count++;
				}
				if(count > 0)
					statement.executeBatch();
			}

		}
		catch(BatchUpdateException error)
		{
			if(stmtInfo != null) {
				try {
					stmtInfo.errorHandle(error);
				} catch (SQLException ex) {
					throw new ElasticSearchException(sql,error);
				}
			}
			throw new ElasticSearchException(sql,error);
		}
		catch (Exception e) {
			if(stmtInfo != null) {

				try {
					stmtInfo.errorHandle(e);
				} catch (SQLException ex) {
					throw new ElasticSearchException(sql,e);
				}
			}
			throw new ElasticSearchException(sql,e);

		} finally {
			if(stmtInfo != null)
				stmtInfo.dofinally();
			if(con_ != null){
				try {
					con_.close();
				}
				catch (Exception e){

				}
			}
//			logger.info("stmtInfo.dofinally()");
//			debugDB(importContext.getDbConfig().getDbName());
			stmtInfo = null;


		}
		return data;
	}

	public int getTryCount() {
		return tryCount;
	}


}
