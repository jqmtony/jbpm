/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.glaf.matrix.cycle.bean;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.glaf.core.context.ContextFactory;
import com.glaf.core.domain.ColumnDefinition;
import com.glaf.core.domain.Database;
import com.glaf.core.domain.TableDefinition;
import com.glaf.core.jdbc.BulkInsertBean;
import com.glaf.core.jdbc.DBConnectionFactory;
import com.glaf.core.jdbc.QueryHelper;
import com.glaf.core.service.IDatabaseService;
import com.glaf.core.util.DBUtils;
import com.glaf.core.util.DateUtils;
import com.glaf.core.util.JdbcUtils;
import com.glaf.core.util.LowerLinkedMap;
import com.glaf.core.util.ParamUtils;
import com.glaf.core.util.QueryUtils;
import com.glaf.core.util.UUID32;
import com.glaf.matrix.cycle.domain.LoopSqlToTable;
import com.glaf.matrix.cycle.service.LoopSqlToTableService;
import com.glaf.matrix.data.domain.TableColumn;
import com.glaf.matrix.data.service.ITableService;
import com.glaf.matrix.util.SysParams;

public class LoopSqlToTableBean {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	public boolean execute(String syncId, Map<String, Object> params) {
		IDatabaseService databaseService = ContextFactory.getBean("databaseService");
		ITableService tableService = ContextFactory.getBean("tableService");
		LoopSqlToTableService loopSqlToTableService = ContextFactory
				.getBean("com.glaf.matrix.cycle.service.loopSqlToTableService");
		LoopSqlToTable app = loopSqlToTableService.getLoopSqlToTable(syncId);
		if (app == null || app.getLocked() != 0) {
			return false;
		}
		QueryHelper helper = new QueryHelper();

		Map<String, Object> parameter = new HashMap<String, Object>();
		SysParams.putInternalParams(parameter);
		if (params != null && !params.isEmpty()) {
			parameter.putAll(params);
		}

		Map<String, String> keyMap = new HashMap<String, String>();

		Database srcDatabase = null;
		Database targetDatabase = null;
		Connection srcConn = null;
		Connection targetConn = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;
		try {
			srcDatabase = databaseService.getDatabaseById(Long.parseLong(app.getSourceDatabaseId()));
			targetDatabase = databaseService.getDatabaseById(Long.parseLong(app.getTargetDatabaseId()));

			if (srcDatabase != null) {
				srcConn = DBConnectionFactory.getConnection(srcDatabase.getName());
			}

			if (targetDatabase != null) {
				targetConn = DBConnectionFactory.getConnection(targetDatabase.getName());
				logger.debug("targetConn:" + targetConn);
			} else {
				targetConn = DBConnectionFactory.getConnection();
			}

			if (targetConn != null) {
				List<ColumnDefinition> columns = null;
				boolean tableExists = false;
				if (DBUtils.tableExists(targetConn, app.getTargetTableName())) {
					tableExists = true;
					columns = DBUtils.getColumnDefinitions(targetConn, app.getTargetTableName());
				} else {
					columns = new ArrayList<ColumnDefinition>();
				}
				JdbcUtils.close(targetConn);

				Set<String> columnNames = new HashSet<String>();
				for (ColumnDefinition col : columns) {
					columnNames.add(col.getColumnName().trim().toLowerCase());
				}

				TableDefinition tableDefinition = new TableDefinition();
				tableDefinition.setTableName(app.getTargetTableName());
				tableDefinition.setColumns(columns);

				ColumnDefinition idColumn = new ColumnDefinition();
				idColumn.setColumnName("EX_ID_");// 主键
				idColumn.setJavaType("String");
				idColumn.setLength(200);
				tableDefinition.setIdColumn(idColumn);

				ColumnDefinition syncIdColumn = new ColumnDefinition();
				syncIdColumn.setColumnName("EX_SYNCID_");// syncId
				syncIdColumn.setJavaType("String");
				syncIdColumn.setLength(50);
				tableDefinition.addColumn(syncIdColumn);

				ColumnDefinition databaseIdColumn = new ColumnDefinition();
				databaseIdColumn.setColumnName("EX_DATABASEID_");// databaseId
				databaseIdColumn.setJavaType("Long");
				tableDefinition.addColumn(databaseIdColumn);

				ColumnDefinition discriminatorColumn = new ColumnDefinition();
				discriminatorColumn.setColumnName("EX_DISCRIMINATOR_");// discriminator
				discriminatorColumn.setJavaType("String");
				discriminatorColumn.setLength(10);
				tableDefinition.addColumn(discriminatorColumn);

				ColumnDefinition mappingColumn = new ColumnDefinition();
				mappingColumn.setColumnName("EX_MAPPING_");// mapping
				mappingColumn.setJavaType("String");
				mappingColumn.setLength(50);
				tableDefinition.addColumn(mappingColumn);

				ColumnDefinition dateColumn = new ColumnDefinition();
				dateColumn.setColumnName("EX_DATE_");// date
				dateColumn.setJavaType("String");
				dateColumn.setLength(50);
				tableDefinition.addColumn(dateColumn);

				String sql = app.getSql();
				if (!DBUtils.isLegalQuerySql(sql)) {
					throw new RuntimeException(" SQL statement illegal ");
				}

				if (targetDatabase != null) {
					targetConn = DBConnectionFactory.getConnection(targetDatabase.getName());
					logger.debug("targetConn:" + targetConn);
				} else {
					targetConn = DBConnectionFactory.getConnection();
				}

				Date startDate = app.getStartDate();
				Date stopDate = app.getStopDate();

				if (stopDate == null) {
					stopDate = new Date();
				}

				String sourceDatabaseType = DBConnectionFactory.getDatabaseType(srcConn);
				parameter.put("loop_date_var", DateUtils.getYearMonthDay(stopDate));
				if (StringUtils.equals(sourceDatabaseType, "oracle")) {
					parameter.put("loop_date_start_var", DateUtils.getDate(stopDate) + " 00:00:00");
					parameter.put("loop_date_end_var", DateUtils.getDate(stopDate) + " 23:59:59");
				} else {
					parameter.put("loop_date_start_var", DateUtils.getDate(stopDate) + " 00:00:00");
					parameter.put("loop_date_end_var", DateUtils.getDate(stopDate) + " 23:59:59");
				}

				/**
				 * 通过查询确定字段信息
				 */
				sql = QueryUtils.replaceSQLVars(sql, parameter);
				logger.debug("query sql:" + sql);
				List<ColumnDefinition> cols = helper.getColumns(srcConn, sql, parameter);
				JdbcUtils.close(srcConn);

				for (ColumnDefinition col : cols) {
					if (StringUtils.equals(col.getJavaType(), "String")) {
						col.setLength(1000);
					}
					tableDefinition.addColumn(col);
				}

				tableExists = DBUtils.tableExists(targetConn, tableDefinition.getTableName());

				targetConn.setAutoCommit(false);
				if (!tableExists) {
					DBUtils.createTable(targetConn, tableDefinition);
					for (ColumnDefinition col : cols) {
						TableColumn tbCol = new TableColumn();
						tbCol.setTitle(col.getTitle());
						tbCol.setColumnName(col.getColumnName());
						tbCol.setJavaType(col.getJavaType());
						tbCol.setLength(col.getLength());
						tbCol.setTableId("loop_sql_table_" + syncId);
						tbCol.setId("loop_sql_table_" + syncId + "_" + col.getColumnName());
						tableService.saveColumn("loop_sql_table_" + syncId, tbCol);
					}
				} else {
					DBUtils.alterTable(targetConn, tableDefinition);
					for (ColumnDefinition col : cols) {
						TableColumn tbCol = new TableColumn();
						tbCol.setTitle(col.getTitle());
						tbCol.setColumnName(col.getColumnName());
						tbCol.setJavaType(col.getJavaType());
						tbCol.setLength(col.getLength());
						tbCol.setTableId("loop_sql_table_" + syncId);
						tbCol.setId("loop_sql_table_" + syncId + "_" + col.getColumnName());
						tableService.saveColumn("loop_sql_table_" + syncId, tbCol);
					}
				}

				if (!StringUtils.equals(app.getDeleteFetch(), "Y")) {
					psmt = targetConn.prepareStatement("select ex_id_ from " + app.getTargetTableName()
							+ " where ex_syncid_ = '" + app.getId() + "' ");
					rs = psmt.executeQuery();
					while (rs.next()) {
						String key = rs.getString(1);
						keyMap.put(key, key);
					}
					JdbcUtils.close(psmt);
					JdbcUtils.close(rs);
				}

				targetConn.commit();
				JdbcUtils.close(targetConn);

				logger.debug("columns:" + columns);

				Calendar calendar = Calendar.getInstance();
				if (startDate == null) {
					if (app.getRecentlyDay() > 1 && app.getRecentlyDay() < 365) {
						startDate = new Date(System.currentTimeMillis() - DateUtils.DAY * app.getRecentlyDay());
					} else {
						startDate = new Date(System.currentTimeMillis() - DateUtils.DAY * 30);
					}
				}

				if (startDate != null && startDate.getTime() < stopDate.getTime()) {
					calendar.setTime(startDate);
					String primaryKey = app.getPrimaryKey();
					if (StringUtils.isNotEmpty(primaryKey)) {
						primaryKey = primaryKey.trim().toLowerCase();
					}

					while (calendar.getTime().getTime() < stopDate.getTime()) {
						calendar.add(Calendar.DAY_OF_YEAR, 1);// 每次增加一天
						parameter.put("loop_date_var", DateUtils.getYearMonthDay(calendar.getTime()));
						if (StringUtils.equals(sourceDatabaseType, "oracle")) {
							parameter.put("loop_date_start_var", DateUtils.getDate(calendar.getTime()) + " 00:00:00");
							parameter.put("loop_date_end_var", DateUtils.getDate(calendar.getTime()) + " 23:59:59");
						} else {
							parameter.put("loop_date_start_var", DateUtils.getDate(calendar.getTime()) + " 00:00:00");
							parameter.put("loop_date_end_var", DateUtils.getDate(calendar.getTime()) + " 23:59:59");
						}
						logger.debug("date start:" + DateUtils.getDate(calendar.getTime()));
						this.saveDaily(srcDatabase, targetDatabase, app, parameter, tableDefinition, calendar.getTime(),
								keyMap);
					}
				}
			}
			return true;
		} catch (Exception ex) {
			// ex.printStackTrace();
			logger.error("execute sync error", ex);
			throw new RuntimeException(ex);
		} finally {
			JdbcUtils.close(rs);
			JdbcUtils.close(psmt);
			JdbcUtils.close(srcConn);
			JdbcUtils.close(targetConn);
		}
	}

	protected void saveDaily(Database srcDatabase, Database targetDatabase, LoopSqlToTable app,
			Map<String, Object> parameter, TableDefinition tableDefinition, Date date, Map<String, String> keyMap) {
		Map<String, Map<String, Object>> dataListMap = new HashMap<String, Map<String, Object>>();
		List<Map<String, Object>> insertList = new ArrayList<Map<String, Object>>();
		QueryHelper helper = new QueryHelper();
		List<Map<String, Object>> resultList = null;
		LowerLinkedMap rowMap = null;
		Connection srcConn = null;
		Connection targetConn = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;
		String primaryKey = app.getPrimaryKey();
		String sql = app.getSql();
		try {
			if (srcDatabase != null) {
				srcConn = DBConnectionFactory.getConnection(srcDatabase.getName());
			}

			sql = QueryUtils.replaceSQLVars(sql, parameter);
			logger.debug("query sql:" + sql);
			if (StringUtils.equals(app.getSkipError(), "Y")) {
				try {
					resultList = helper.getResultList(srcConn, sql, parameter);
				} catch (java.lang.Throwable ex) {
				}
			} else {
				resultList = helper.getResultList(srcConn, sql, parameter);
			}
			JdbcUtils.close(srcConn);

			if (resultList != null && !resultList.isEmpty()) {
				for (Map<String, Object> dataMap : resultList) {
					rowMap = new LowerLinkedMap();
					rowMap.putAll(dataMap);
					if (StringUtils.isNotEmpty(primaryKey)) {
						String key = ParamUtils.getString(rowMap, primaryKey);
						if (keyMap.get(app.getId() + "_" + key) == null) {
							rowMap.put("ex_id_", app.getId() + "_" + key);
							rowMap.put("ex_syncid_", app.getId());
							rowMap.put("ex_databaseid_", srcDatabase.getId());
							rowMap.put("ex_discriminator_", srcDatabase.getDiscriminator());
							rowMap.put("ex_mapping_", srcDatabase.getMapping());
							rowMap.put("ex_date_", DateUtils.getDate(date));
							keyMap.put(app.getId() + "_" + key, srcDatabase.getId() + "_" + key);
							insertList.add(rowMap);
						}
					} else {
						rowMap.put("ex_id_", app.getId() + "_" + UUID32.getUUID());
						rowMap.put("ex_syncid_", app.getId());
						rowMap.put("ex_databaseid_", srcDatabase.getId());
						rowMap.put("ex_discriminator_", srcDatabase.getDiscriminator());
						rowMap.put("ex_mapping_", srcDatabase.getMapping());
						rowMap.put("ex_date_", DateUtils.getDate(date));
						insertList.add(rowMap);
					}
				}

				if (insertList.size() > 0) {
					if (targetDatabase != null) {
						targetConn = DBConnectionFactory.getConnection(targetDatabase.getName());
						logger.debug("targetConn:" + targetConn);
					} else {
						targetConn = DBConnectionFactory.getConnection();
					}
					BulkInsertBean insertBean = new BulkInsertBean();
					targetConn.setAutoCommit(false);
					if (StringUtils.equals(app.getDeleteFetch(), "Y")) {
						String day = DateUtils.getDate(date);
						String delSQL = " delete from " + app.getTargetTableName() + " where ex_syncid_ = '"
								+ app.getId() + "' " + " and ex_date_ = '" + day + "' ";
						DBUtils.executeSchemaResource(targetConn, delSQL);
						logger.debug("delete sql:" + delSQL);

					}
					insertBean.bulkInsert(null, targetConn, tableDefinition, insertList);
					targetConn.commit();
					JdbcUtils.close(targetConn);
				}
				insertList.clear();
				dataListMap.clear();
				insertList = null;
				dataListMap = null;
			}

		} catch (Exception ex) {
			// ex.printStackTrace();
			logger.error("execute sync error", ex);
			throw new RuntimeException(ex);
		} finally {
			JdbcUtils.close(rs);
			JdbcUtils.close(psmt);
			JdbcUtils.close(srcConn);
			JdbcUtils.close(targetConn);
		}
	}

}
