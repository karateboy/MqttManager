package models

import com.github.nscala_time.time.Imports._
import models.ModelHelper._
import play.api.libs.json._

import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class CalibrationJSON(monitorType: String, startTime: Long, endTime: Long, zero_val: Option[Double],
                           span_std: Option[Double], span_val: Option[Double])

case class Calibration(monitorType: String, startTime: Date, endTime: Date, zero_val: Option[Double],
                       span_std: Option[Double], span_val: Option[Double]) {
  def zero_dev = zero_val.map(Math.abs)

  def span_dev =
    for (span <- span_val; std <- span_std)
      yield Math.abs(span_val.get - span_std.get)

  def span_dev_ratio = for (s_dev <- span_dev; std <- span_std)
    yield s_dev / std * 100

  def toJSON = {
    CalibrationJSON(monitorType, startTime.getTime, endTime.getTime, zero_val,
      span_std, span_val)
  }
}

import javax.inject._

@Singleton
class CalibrationOp @Inject()(mongoDB: MongoDB, monitorTypeOp: MonitorTypeOp) {

  val collectionName = "calibration"
  val collection = mongoDB.database.getCollection(collectionName)

  import org.mongodb.scala._
  import org.mongodb.scala.model.Indexes._

  def init() {
    for (colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(collectionName)) {
        val f = mongoDB.database.createCollection(collectionName).toFuture()
        f.failed.foreach(errorHandler)
        f.foreach(
          _ => {
            val cf = collection.createIndex(ascending("monitorType", "startTime", "endTime")).toFuture()
            cf.failed.foreach(errorHandler)
          }
        )
      }
    }
  }

  init
  implicit val reads = Json.reads[Calibration]
  implicit val writes = Json.writes[Calibration]
  implicit val jsonWrites = Json.writes[CalibrationJSON]

  def toDocument(cal: Calibration): Document = {
    import org.mongodb.scala.bson._
    Document("monitorType" -> cal.monitorType, "startTime" -> cal.startTime,
      "endTime" -> cal.endTime, "zero_val" -> cal.zero_val,
      "span_std" -> cal.span_std, "span_val" -> cal.span_val)
  }

  private def toCalibration(doc: Document) = {
    import org.mongodb.scala.bson.BsonDouble
    def doublePf: PartialFunction[org.mongodb.scala.bson.BsonValue, Double] = {
      case t: BsonDouble =>
        t.getValue
    }

    val startTime = new DateTime(doc.get("startTime").get.asDateTime().getValue)
    val endTime = new DateTime(doc.get("endTime").get.asDateTime().getValue)
    val monitorType = (doc.get("monitorType").get.asString().getValue)
    val zero_val = doc.get("zero_val").collect(doublePf)

    val span_std = doc.get("span_std").collect(doublePf)
    val span_val = doc.get("span_val").collect(doublePf)
    Calibration(monitorType, startTime, endTime, zero_val, span_std, span_val)
  }

  def calibrationReport(start: DateTime, end: DateTime): Seq[Calibration] = {
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Sorts._

    val f = collection.find(and(gte("startTime", start.toDate()), lt("startTime", end.toDate()))).sort(ascending("startTime")).toFuture()
    val docs = waitReadyResult(f)
    docs.map {
      toCalibration
    }
  }

  def calibrationReportFuture(start: DateTime, end: DateTime): Future[Seq[Calibration]] = {
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Sorts._

    val f = collection.find(and(gte("startTime", start.toDate), lt("startTime", end.toDate))).sort(ascending("startTime")).toFuture()
    for (docs <- f)
      yield docs.map {
        toCalibration
      }
  }

  def calibrationReportFuture(start: DateTime): Future[Seq[Calibration]] = {
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Sorts._

    val f = collection.find(gte("startTime", start.toDate)).sort(ascending("startTime")).toFuture()
    for (docs <- f)
      yield docs.map {
        toCalibration
      }
  }

  def calibrationReport(mt: String, start: DateTime, end: DateTime) = {
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Sorts._

    val f = collection.find(and(equal("monitorType", mt), gte("startTime", start.toDate), lt("startTime", end.toDate))).sort(ascending("startTime")).toFuture()
    val docs = waitReadyResult(f)
    docs.map {
      toCalibration
    }
  }

  def calibrationMonthly(monitorType: String, start: DateTime): Map[String, Calibration] = {
    val end = start + 1.month
    val report = List.empty[Calibration]
    val pairs =
      for {r <- report} yield {
        r.startTime.toString -> r
      }
    Map(pairs: _*)
  }

  def insert(cal: Calibration): Unit = {
    import ModelHelper._
    val f = collection.insertOne(toDocument(cal)).toFuture()
    f.failed.foreach {
      case ex: Exception =>
        logException(ex)
    }
    //f map {_=> ForwardManager.forwardCalibration}
  }

  def getZeroCalibrationStyle(cal: Calibration): String = {
    val styleOpt =
      for {
        zero_val <- cal.zero_val
        zd_internal <- monitorTypeOp.map(cal.monitorType).zd_internal
        zd_law <- monitorTypeOp.map(cal.monitorType).zd_law
      } yield if (zero_val > zd_law)
        "red"
      else if (zero_val > zd_internal)
        "blue"
      else
        ""

    styleOpt.getOrElse("")
  }

  def getSpanCalibrationStyle(cal: Calibration): String = {
    val styleOpt =
      for {
        span_dev_ratio <- cal.span_dev_ratio
        span_dev_internal <- monitorTypeOp.map(cal.monitorType).span_dev_internal
        span_dev_law <- monitorTypeOp.map(cal.monitorType).span_dev_law
      } yield if (span_dev_ratio > span_dev_law)
        "red"
      else if (span_dev_ratio > span_dev_internal)
        "blue"
      else
        ""

    styleOpt.getOrElse("")
  }

  def getResultStyle(cal: Calibration): String = {
    val zeroStyleOpt =
      for {
        zero_val <- cal.zero_val
        zd_internal <- monitorTypeOp.map(cal.monitorType).zd_internal
        zd_law <- monitorTypeOp.map(cal.monitorType).zd_law
      } yield if (zero_val > zd_law)
        "red"
      else if (zero_val > zd_internal)
        "blue"
      else
        ""

    val spanStyleOpt =
      for {
        span_dev_ratio <- cal.span_dev_ratio
        span_dev_internal <- monitorTypeOp.map(cal.monitorType).span_dev_internal
        span_dev_law <- monitorTypeOp.map(cal.monitorType).span_dev_law
      } yield if (span_dev_ratio > span_dev_law)
        "danger"
      else if (span_dev_ratio > span_dev_internal)
        "info"
      else
        ""

    val styleOpt =
      for (zeroStyle <- zeroStyleOpt; spanStyle <- spanStyleOpt)
        yield if (zeroStyle == "danger" || spanStyle == "danger")
          "danger"
        else if (zeroStyle == "info" || spanStyle == "info")
          "info"
        else
          ""

    styleOpt.getOrElse("")
  }

  def getResult(cal: Calibration) = {
    val zeroResultOpt =
      for {
        zero_val <- cal.zero_val
        zd_internal <- monitorTypeOp.map(cal.monitorType).zd_internal
        zd_law <- monitorTypeOp.map(cal.monitorType).zd_law
      } yield if (zero_val > zd_law)
        false
      else
        true

    val spanResultOpt =
      for {
        span_dev_ratio <- cal.span_dev_ratio
        span_dev_internal <- monitorTypeOp.map(cal.monitorType).span_dev_internal
        span_dev_law <- monitorTypeOp.map(cal.monitorType).span_dev_law
      } yield if (span_dev_ratio > span_dev_law)
        false
      else
        true

    val resultOpt =
      if (zeroResultOpt.isDefined) {
        if (spanResultOpt.isDefined)
          Some(zeroResultOpt.get && spanResultOpt.get)
        else
          Some(zeroResultOpt.get)
      } else {
        if (spanResultOpt.isDefined)
          Some(spanResultOpt.get)
        else
          None
      }

    val resultStrOpt = resultOpt map { v => if (v) "成功" else "失敗" }

    resultStrOpt.getOrElse("-")
  }
}