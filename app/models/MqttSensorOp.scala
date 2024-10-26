package models
import models.ModelHelper.errorHandler
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success


case class Sensor(id:String, topic: String, monitor: String, group:String)
object MqttSensor {
  implicit val write = Json.writes[Sensor]
  implicit val read = Json.reads[Sensor]
}

@Singleton
class MqttSensorOp @Inject()(mongoDB: MongoDB, groupOp: GroupOp) {
  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  val ColName = "sensors"
  private val codecRegistry = fromRegistries(fromProviders(classOf[Sensor]), DEFAULT_CODEC_REGISTRY)
  val collection: MongoCollection[Sensor] = mongoDB.database.getCollection[Sensor](ColName).withCodecRegistry(codecRegistry)

  import org.mongodb.scala.model._
  collection.createIndex(Indexes.descending("id"), IndexOptions().unique(true))
  collection.createIndex(Indexes.descending("group"))
  collection.createIndex(Indexes.descending("topic"))
  collection.createIndex(Indexes.descending("monitor"))

  def getSensorList(group:String): Future[Seq[Sensor]] = {
    val f = collection.find(Filters.eq("group", group)).toFuture()
    f.failed.foreach(errorHandler())
    f
  }

  def getAllSensorList = {
    val f = collection.find(Filters.exists("_id")).toFuture()
    f.failed.foreach(errorHandler())
    f
  }

  def getSensorMap: Future[Map[String, Sensor]] = {
    for(sensorList <- getAllSensorList) yield {
      val pairs =
        for(sensor<-sensorList) yield
          sensor.id -> sensor

      pairs.toMap
    }
  }

  def getSensor(id:String): Future[Seq[Sensor]] = {
    val f = collection.find(Filters.equal("id", id)).toFuture()
    f.failed.foreach(errorHandler())
    f
  }

  def upsert(sensor:Sensor): Future[UpdateResult] ={
    val f = collection.replaceOne(Filters.equal("id", sensor.id), sensor, ReplaceOptions().upsert(true)).toFuture()
    f.failed.foreach(errorHandler)
    groupOp.addMonitor(sensor.group, sensor.monitor)
      .failed.foreach(errorHandler())
    f
  }

  def delete(id:String): Future[DeleteResult] = {
    val f = collection.deleteOne(Filters.equal("id", id)).toFuture()
    f.failed.foreach(errorHandler)
    f
  }

  def deleteByMonitor(monitor:String): Future[DeleteResult] = {
    val f = collection.deleteOne(Filters.equal("monitor", monitor)).toFuture()
    f.failed.foreach(errorHandler)
    f
  }
}
