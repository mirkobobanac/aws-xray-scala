package net.toqoz.aws.xray.jdbc

import java.lang.reflect.InvocationTargetException
import java.sql.{ ResultSet, SQLWarning, Connection => IConnection, Statement => IStatement }
import java.util.{ Map => JavaMap }

import com.amazonaws.xray.AWSXRay
import com.amazonaws.xray.entities.{ Namespace, Subsegment }
import com.typesafe.scalalogging.LazyLogging
import net.toqoz.aws.xray.config.CaptureQueryConfig

class Statement(stmt: IStatement, subsegmentName: String, additionalParams: JavaMap[String, AnyRef]) extends IStatement with LazyLogging {
  // ****************
  // java.sql.Wrapper
  // ****************
  override def unwrap[T](iface: Class[T]): T = stmt.unwrap(iface)
  override def isWrapperFor(iface: Class[_]): Boolean = stmt.isWrapperFor(iface)

  // ******************
  // java.sql.Statement
  // ******************
  override def executeQuery(sql: String): ResultSet = tracing(sql, () => stmt.executeQuery(sql))
  override def executeUpdate(sql: String): Int = tracing(sql, () => stmt.executeUpdate(sql))
  override def close(): Unit = stmt.close()
  //----------------------------------------------------------------------
  override def getMaxFieldSize: Int = stmt.getMaxFieldSize
  override def setMaxFieldSize(max: Int): Unit = stmt.setMaxFieldSize(max)
  override def getMaxRows: Int = stmt.getMaxRows
  override def setMaxRows(max: Int): Unit = stmt.setMaxRows(max)
  override def setEscapeProcessing(enable: Boolean): Unit = stmt.setEscapeProcessing(enable)
  override def getQueryTimeout: Int = stmt.getQueryTimeout
  override def setQueryTimeout(seconds: Int): Unit = stmt.setQueryTimeout(seconds)
  override def cancel(): Unit = stmt.cancel()
  override def getWarnings: SQLWarning = stmt.getWarnings
  override def clearWarnings(): Unit = stmt.clearWarnings()
  override def setCursorName(name: String): Unit = stmt.setCursorName(name)
  //----------------------- Multiple Results --------------------------
  override def execute(sql: String): Boolean = tracing(sql, () => stmt.execute(sql))
  override def getResultSet: ResultSet = stmt.getResultSet
  override def getUpdateCount: Int = stmt.getUpdateCount
  override def getMoreResults: Boolean = stmt.getMoreResults
  //--------------------------JDBC 2.0-----------------------------
  override def setFetchDirection(direction: Int): Unit = stmt.setFetchDirection(direction)
  override def getFetchDirection: Int = stmt.getFetchDirection
  override def setFetchSize(rows: Int): Unit = stmt.setFetchSize(rows)
  override def getFetchSize: Int = stmt.getFetchSize
  override def getResultSetConcurrency: Int = stmt.getResultSetConcurrency
  override def getResultSetType: Int = stmt.getResultSetType
  override def addBatch(sql: String): Unit = stmt.addBatch(sql)
  override def clearBatch(): Unit = stmt.clearBatch()
  override def executeBatch: Array[Int] = tracing("<none>", () => stmt.executeBatch())
  override def getConnection: IConnection = stmt.getConnection
  //--------------------------JDBC 3.0-----------------------------
  override def getMoreResults(current: Int): Boolean = stmt.getMoreResults
  override def getGeneratedKeys: ResultSet = stmt.getGeneratedKeys
  override def executeUpdate(sql: String, autoGeneratedKeys: Int): Int = tracing(sql, () => stmt.executeUpdate(sql, autoGeneratedKeys))
  override def executeUpdate(sql: String, columnIndexes: Array[Int]): Int = tracing(sql, () => stmt.executeUpdate(sql, columnIndexes))
  override def executeUpdate(sql: String, columnNames: Array[String]): Int = tracing(sql, () => stmt.executeUpdate(sql, columnNames))
  override def execute(sql: String, autoGeneratedKeys: Int): Boolean = tracing(sql, () => stmt.execute(sql, autoGeneratedKeys))
  override def execute(sql: String, columnIndexes: Array[Int]): Boolean = tracing(sql, () => stmt.execute(sql, columnIndexes))
  override def execute(sql: String, columnNames: Array[String]): Boolean = tracing(sql, () => stmt.execute(sql, columnNames))
  override def getResultSetHoldability: Int = stmt.getResultSetHoldability
  override def isClosed: Boolean = stmt.isClosed
  override def setPoolable(poolable: Boolean): Unit = stmt.setPoolable(poolable)
  override def isPoolable: Boolean = stmt.isPoolable
  //--------------------------JDBC 4.1 -----------------------------
  override def closeOnCompletion(): Unit = stmt.closeOnCompletion()
  override def isCloseOnCompletion: Boolean = stmt.isCloseOnCompletion

  protected def tracing[T](sql: String, fn: () => T): T = beginSubsegment(subsegmentName) match {
    case Some(subsegment) =>
      try {
        // c.f. https://github.com/aws/aws-xray-sdk-java/issues/28
        if (CaptureQueryConfig.isEnabled) {
          additionalParams.put("sanitized_query", sql)
        }
        subsegment.putAllSql(additionalParams)
        subsegment.setNamespace(Namespace.REMOTE.toString)
        fn()
      } catch {
        case t: Throwable =>
          subsegment.addException(t)
          if (t.isInstanceOf[InvocationTargetException] && t.getCause != null) throw t.getCause
          else throw t
      } finally {
        AWSXRay.endSubsegment()
      }
    case _ =>
      // AWS_XRAY_CONTEXT_MISSING=LOG_ERROR && Failed to getSegment
      logger.warn(s"Failed to begin subsegment($subsegmentName)")
      fn()
  }

  private def beginSubsegment(name: String): Option[Subsegment] = Option(AWSXRay.beginSubsegment(name))
}