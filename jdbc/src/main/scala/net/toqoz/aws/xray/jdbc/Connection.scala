package net.toqoz.aws.xray.jdbc

import java.net.{ URI, URISyntaxException }
import java.sql.{
  Blob,
  Clob,
  DatabaseMetaData,
  NClob,
  SQLWarning,
  SQLXML,
  Savepoint,
  Struct,
  Array => JavaArray,
  CallableStatement => ICallableStatement,
  Connection => IConnection,
  PreparedStatement => IPreparedStatement,
  Statement => IStatement
}
import java.util.{ Properties, Map => JavaMap }
import java.util.concurrent.Executor

import collection.JavaConverters._
import collection.mutable.{ Map => MutableMap }
import com.typesafe.scalalogging.LazyLogging

class Connection(conn: IConnection) extends IConnection with LazyLogging {
  private val DEFAULT_DATABASE_NAME = "database"

  // ****************
  // java.sql.Wrapper
  // ****************
  override def unwrap[T](iface: Class[T]): T = conn.unwrap(iface)
  override def isWrapperFor(iface: Class[_]): Boolean = conn.isWrapperFor(iface)

  // *******************
  // java.sql.Connection
  // *******************
  override def createStatement(): Statement = wrapStatement(conn.createStatement())
  override def prepareStatement(sql: String): PreparedStatement = wrapPreparedStatement(conn.prepareStatement(sql), sql)
  override def prepareCall(sql: String): CallableStatement = wrapCallableStatement(conn.prepareCall(sql), sql)
  override def nativeSQL(sql: String): String = conn.nativeSQL(sql)
  override def setAutoCommit(autoCommit: Boolean): Unit = conn.setAutoCommit(autoCommit)
  override def getAutoCommit: Boolean = conn.getAutoCommit
  override def commit(): Unit = conn.commit()
  override def rollback(): Unit = conn.rollback()
  override def close(): Unit = conn.close()
  override def isClosed: Boolean = conn.isClosed
  // Advanced features:
  override def getMetaData: DatabaseMetaData = conn.getMetaData
  override def setReadOnly(readOnly: Boolean): Unit = conn.setReadOnly(readOnly)
  override def isReadOnly: Boolean = conn.isReadOnly
  override def setCatalog(catalog: String): Unit = conn.setCatalog(catalog)
  override def getCatalog: String = conn.getCatalog
  override def setTransactionIsolation(level: Int): Unit = conn.setTransactionIsolation(level)
  override def getTransactionIsolation: Int = conn.getTransactionIsolation
  override def getWarnings: SQLWarning = conn.getWarnings
  override def clearWarnings(): Unit = conn.clearWarnings()
  // 2.0
  override def createStatement(resultSetType: Int, resultSetConcurrency: Int): IStatement =
    wrapStatement(conn.createStatement(resultSetType, resultSetConcurrency))
  override def prepareStatement(sql: String, resultSetType: Int, resultSetConcurrency: Int): IPreparedStatement =
    wrapPreparedStatement(conn.prepareStatement(sql, resultSetType, resultSetConcurrency), sql)
  override def prepareCall(sql: String, resultSetType: Int, resultSetConcurrency: Int): ICallableStatement =
    wrapCallableStatement(conn.prepareCall(sql, resultSetType, resultSetConcurrency), sql)
  override def getTypeMap: JavaMap[String, Class[_]] = conn.getTypeMap
  override def setTypeMap(map: JavaMap[String, Class[_]]): Unit = conn.setTypeMap(map)
  // 3.0
  override def setHoldability(holdability: Int): Unit = conn.setHoldability(holdability)
  override def getHoldability: Int = conn.getHoldability
  override def setSavepoint(): Savepoint = conn.setSavepoint()
  override def setSavepoint(name: String): Savepoint = conn.setSavepoint(name)
  override def rollback(savepoint: Savepoint): Unit = conn.rollback(savepoint)
  override def releaseSavepoint(savepoint: Savepoint): Unit = conn.releaseSavepoint(savepoint)
  override def createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): IStatement =
    wrapStatement(conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability))
  override def prepareStatement(sql: String, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): IPreparedStatement =
    wrapPreparedStatement(conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql)
  override def prepareCall(sql: String, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): ICallableStatement =
    wrapCallableStatement(conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql)
  override def prepareStatement(sql: String, autoGeneratedKeys: Int): IPreparedStatement =
    wrapPreparedStatement(conn.prepareStatement(sql, autoGeneratedKeys), sql)
  override def prepareStatement(sql: String, columnIndexes: Array[Int]): IPreparedStatement =
    wrapPreparedStatement(conn.prepareStatement(sql, columnIndexes), sql)
  override def prepareStatement(sql: String, columnNames: Array[String]): IPreparedStatement =
    wrapPreparedStatement(conn.prepareStatement(sql, columnNames), sql)
  override def createClob(): Clob = conn.createClob()
  override def createBlob(): Blob = conn.createBlob()
  override def createNClob(): NClob = conn.createNClob()
  override def createSQLXML(): SQLXML = conn.createSQLXML()
  override def isValid(timeout: Int): Boolean = conn.isValid(timeout)
  override def setClientInfo(name: String, value: String): Unit = conn.setClientInfo(name, value)
  override def setClientInfo(properties: Properties): Unit = conn.setClientInfo(properties)
  override def getClientInfo: Properties = conn.getClientInfo
  override def getClientInfo(name: String): String = conn.getClientInfo(name)
  override def createArrayOf(typeName: String, elements: Array[AnyRef]): JavaArray = conn.createArrayOf(typeName, elements)
  override def createStruct(typeName: String, attributes: Array[AnyRef]): Struct = conn.createStruct(typeName, attributes)
  // 4.1
  override def setSchema(schema: String): Unit = conn.setSchema(schema)
  override def getSchema: String = conn.getSchema
  override def abort(executor: Executor): Unit = conn.abort(executor)
  override def setNetworkTimeout(executor: Executor, milliseconds: Int): Unit = conn.setNetworkTimeout(executor, milliseconds)
  override def getNetworkTimeout: Int = conn.getNetworkTimeout

  private def wrapStatement(s: IStatement): Statement = s match {
    case stmt: Statement => stmt
    case stmt =>
      logger.debug("Instantiating new statement proxy.")
      val metadata = getMetaData
      new Statement(stmt, subsegmentName(metadata), MutableMap(additionalParams(metadata): _*).asJava)
  }

  private def wrapPreparedStatement(s: IPreparedStatement, sql: String): PreparedStatement = s match {
    case stmt: PreparedStatement => stmt
    case stmt =>
      logger.debug("Instantiating new statement proxy.")
      val metadata = getMetaData
      new PreparedStatement(stmt, subsegmentName(metadata), sql, MutableMap(ADDITIONAL_PARAMS_PREPARE_STMT ++ additionalParams(metadata): _*).asJava)
  }

  private def wrapCallableStatement(s: ICallableStatement, sql: String): CallableStatement = s match {
    case stmt: CallableStatement => stmt
    case stmt =>
      val metadata = getMetaData

      logger.debug("Instantiating new statement proxy.")
      new CallableStatement(stmt, subsegmentName(metadata), sql, MutableMap(ADDITIONAL_PARAMS_PREPARE_CALL ++ additionalParams(metadata): _*).asJava)
  }

  private def subsegmentName(metadata: DatabaseMetaData): String = {
    try {
      val normalizedURI = new URI(new URI(metadata.getURL).getSchemeSpecificPart)
      conn.getCatalog + "@" + normalizedURI.getHost
    } catch {
      case e: URISyntaxException =>
        logger.warn("Unable to parse database URI. Falling back to default '" + DEFAULT_DATABASE_NAME + "' for subsegment name.", e)
        DEFAULT_DATABASE_NAME
    }
  }

  private val ADDITIONAL_PARAMS_PREPARE_STMT = Seq("preparation" -> "statement")
  private val ADDITIONAL_PARAMS_PREPARE_CALL = Seq("preparation" -> "call")
  private def additionalParams(from: DatabaseMetaData): Seq[(String, AnyRef)] =
    Seq(
      "url" -> from.getURL,
      "user" -> from.getUserName,
      "driver_version" -> from.getDriverVersion,
      "database_type" -> from.getDatabaseProductName,
      "database_version" -> from.getDatabaseProductVersion
    )
}
