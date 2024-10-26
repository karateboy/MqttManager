package models
import play.api._
import com.github.nscala_time.time.Imports._
import models.ModelHelper._
import models._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json
import scala.collection.JavaConverters._
import scala.language.implicitConversions

case class AlarmConfig(enable:Boolean, statusFilter:Seq[String])
object AlarmConfig {
  val defaultConfig: AlarmConfig = AlarmConfig(enable = false, Seq.empty[String])
  implicit val acRead: Reads[AlarmConfig] = Json.reads[AlarmConfig]
  implicit val acWrite: OWrites[AlarmConfig] = Json.writes[AlarmConfig]
  
  import org.mongodb.scala.bson._
  implicit object TransformAlarmConfig extends BsonTransformer[AlarmConfig] {
    def apply(config: AlarmConfig): BsonDocument = {
      Document("enable"->config.enable, "statusFilter"->config.statusFilter).toBsonDocument
    }
  }
  implicit def toAlarmConfig(doc:BsonDocument): Option[AlarmConfig] ={
    if(doc.get("enable").isBoolean && doc.get("statusFilter").isArray){
      val enable = doc.get("enable").asBoolean().getValue
      val bsonStatusFilter = doc.get("statusFilter").asArray().getValues
      val statusFilter = bsonStatusFilter.asScala.map { x => x.asString().getValue }
      Some(AlarmConfig(enable, statusFilter))
    }else{
      None
    }
  }
}