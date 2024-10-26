package models

import models.ModelHelper._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global

object StatusType extends Enumeration {
  val Internal = Value("0")
  val Auto = Value("A")
  val Manual = Value("M")
  def map = Map(Internal -> "系統", Auto -> "自動註記", Manual -> "人工註記")
}

case class MonitorStatus(_id: String, desp: String) {
  def getTagInfo(tag: String) = {
    val id = tag.substring(1)
    val t = tag.charAt(0)
    if (t == '0')
      TagInfo(StatusType.Internal, None, id)
    else if (t == 'm' || t == 'M') {
      TagInfo(StatusType.Manual, Some(t), id)
    } else if (t.isLetter)
      TagInfo(StatusType.Auto, Some(t), id)
    else
      throw new Exception("Unknown type:" + t)
  }

  val info = getTagInfo(_id)


}

case class TagInfo(statusType: StatusType.Value, auditRule: Option[Char], id: String) {
  override def toString = {
    if ((statusType == StatusType.Auto || statusType == StatusType.Manual)
      && auditRule.isDefined)
      auditRule.get + id
    else
      statusType + id
  }
}

object MonitorStatus {
  val NormalStat = "010"
  val OverNormalStat = "011"
  val BelowNormalStat = "012"
  val ZeroCalibrationStat = "020"
  val SpanCalibrationStat = "021"
  val CalibrationDeviation = "022"
  val CalibrationResume = "026"
  val InvalidDataStat = "030"
  val MaintainStat = "031"
  val ExceedRangeStat = "032"

  def getTagInfo(tag: String) = {
    val id = tag.substring(1)
    val t = tag.charAt(0)
    if (t == '0')
      TagInfo(StatusType.Internal, None, id)
    else if (t == 'm' || t == 'M') {
      TagInfo(StatusType.Manual, Some(t), id)
    } else if (t.isLetter)
      TagInfo(StatusType.Auto, Some(t), id)
    else
      throw new Exception("Unknown type:" + t)
  }

  def getCssClassStr(tag: String, overInternal: Boolean = false, overLaw: Boolean = false) = {
    val info = getTagInfo(tag)
    val statClass =
      info.statusType match {
        case StatusType.Internal =>
        {
          if (isValid(tag))
            ""
          else if (isCalbration(tag))
            "calibration_status"
          else if (isMaintenance(tag))
            "maintain_status"
          else
            "abnormal_status"
        }
        case StatusType.Auto =>
          "auto_audit_status"
        case StatusType.Manual =>
          "manual_audit_status"
      }

    val fgClass =
      if (overLaw)
        "over_law_std"
      else if (overInternal)
        "over_internal_std"
      else
        "normal"

    if(statClass != "")
      Seq(statClass, fgClass)
    else
      Seq(fgClass)
  }

  def switchTagToInternal(tag: String) = {
    val info = getTagInfo(tag)
    '0' + info.id
  }

  def isValid(s: String) = {
    val tagInfo = getTagInfo(s)
    val VALID_STATS = List(NormalStat, OverNormalStat, BelowNormalStat).map(getTagInfo)

    tagInfo.statusType match {
      case StatusType.Internal =>
        VALID_STATS.contains(getTagInfo(s))
      case StatusType.Auto =>
        if (tagInfo.auditRule.isDefined && tagInfo.auditRule.get.isLower)
          true
        else
          false
      case StatusType.Manual =>
        if (tagInfo.auditRule.isDefined && tagInfo.auditRule.get.isLower)
          true
        else
          false
    }
  }

  def isCalbration(s: String) = {
    val CALBRATION_STATS = List(ZeroCalibrationStat, SpanCalibrationStat,
      CalibrationDeviation,CalibrationResume).map(getTagInfo)

    CALBRATION_STATS.contains(getTagInfo(s))
  }

  def isMaintenance(s: String) = {
    getTagInfo(MaintainStat) == getTagInfo(s)
  }

  def isError(s: String) = {
    !(isValid(s) || isCalbration(s) || isMaintenance(s))
  }

}
import javax.inject._
@Singleton
class MonitorStatusOp @Inject()(mongoDB: MongoDB){
  implicit val reads = Json.reads[MonitorStatus]
  implicit val writes = Json.writes[MonitorStatus]
  val collectionName = "status"
  val collection = mongoDB.database.getCollection(collectionName)

  import MonitorStatus._

  val defaultStatus = List(
    MonitorStatus(NormalStat, "正常"),
    MonitorStatus(OverNormalStat, "超過預設高值"),
    MonitorStatus(BelowNormalStat, "低於預設低值"),
    MonitorStatus(ZeroCalibrationStat, "零點偏移校正"),
    MonitorStatus(SpanCalibrationStat, "全幅偏移校正"),
    MonitorStatus(CalibrationDeviation, "校正偏移"),
    MonitorStatus(CalibrationResume, "校正恢復"),
    MonitorStatus(InvalidDataStat, "無效數據"),
    MonitorStatus(MaintainStat, "維修、保養"),
    MonitorStatus(ExceedRangeStat, "超過量測範圍"))

  import org.mongodb.scala._
  def toDocument(ms: MonitorStatus) = {
    Document(Json.toJson(ms).toString())
  }
  def toMonitorStatus(doc: Document) = {
    Json.parse(doc.toJson()).validate[MonitorStatus].asOpt.get
  }

  def init() {
    def insertDefaultStatus {
      val f = collection.insertMany(defaultStatus.map { toDocument }).toFuture()
      f.failed.foreach(errorHandler)
      f.foreach(_ => refreshMap())
    }

    for(colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(collectionName)) {
        val f = mongoDB.database.createCollection(collectionName).toFuture()
        f.failed.foreach(errorHandler)
        f.foreach(_ => insertDefaultStatus)
      }
    }
  }
  init()

  private def msList: Seq[MonitorStatus] = {
    val f = collection.find().toFuture()
    f.failed.foreach(errorHandler)
    waitReadyResult(f).map { toMonitorStatus }
  }

  def update(tag: String, desp: String) = {
    refreshMap
  }

  private def refreshMap() = {
    _map = Map(msList.map { s => s.info.toString() -> s }: _*)
    _map
  }
  private var _map: Map[String, MonitorStatus] = refreshMap

  def map(key: String) = {
    _map.getOrElse(key, {
      val tagInfo = getTagInfo(key)
      tagInfo.statusType match {
        case StatusType.Auto =>
          val ruleId = tagInfo.auditRule.get.toLower
          MonitorStatus(key, s"自動註記:${ruleId}")
        case StatusType.Manual =>
          MonitorStatus(key, "人工註記")
        case StatusType.Internal =>
          MonitorStatus(key, "未知:" + key)
      }
    })
  }

  def getExplainStr(tag: String) = {
    val tagInfo = getTagInfo(tag)
    if (tagInfo.statusType == StatusType.Auto) {
      val t = tagInfo.auditRule.get
      "自動註記"
    } else {
      val ms = map(tag)
      ms.desp
    }
  }

}
