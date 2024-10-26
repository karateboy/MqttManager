package models
import play.api.libs.json._
import models.ModelHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import org.mongodb.scala.model._
import org.mongodb.scala.bson._
import org.mongodb.scala.result.UpdateResult

import javax.inject._
import scala.concurrent.Future

@Singleton
class SysConfig @Inject()(mongoDB: MongoDB){
  val ColName = "sysConfig"
  val collection = mongoDB.database.getCollection(ColName)

  val valueKey = "value"
  val MonitorTypeVer = "Version"
  val CleanH2SOver150 = "CleanH2SOver150"


  val defaultConfig = Map(
    MonitorTypeVer -> Document(valueKey -> 1),
    CleanH2SOver150 -> Document(valueKey -> false)
  )

  def init(): Unit = {
    for(colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(ColName)) {
        val f = mongoDB.database.createCollection(ColName).toFuture()
        f.failed.foreach(errorHandler)
        waitReadyResult(f)
      }
    }
    val values = Seq.empty[String]
    val idSet = values

    //Clean up unused
    val f1 = collection.deleteMany(Filters.not(Filters.in("_id", idSet.toList: _*))).toFuture()
    f1.failed.foreach(errorHandler)
    val updateModels =
      for ((k, defaultDoc) <- defaultConfig) yield {
        UpdateOneModel(
          Filters.eq("_id", k),
          Updates.setOnInsert(valueKey, defaultDoc(valueKey)), UpdateOptions().upsert(true))
      }

    val f2 = collection.bulkWrite(updateModels.toList, BulkWriteOptions().ordered(false)).toFuture()

    import scala.concurrent._
    val f = Future.sequence(List(f1, f2))
    waitReadyResult(f)
  }
  init()

  def upsert(_id: String, doc: Document): Future[UpdateResult] = {
    val uo = new ReplaceOptions().upsert(true)
    val f = collection.replaceOne(Filters.equal("_id", _id), doc, uo).toFuture()
    f.failed.foreach(errorHandler)
    f
  }

  def get(_id: String): Future[BsonValue] = {
    val f = collection.find(Filters.eq("_id", _id)).headOption()
    f.failed.foreach(errorHandler)
    for (ret <- f) yield {
      val doc = ret.getOrElse(defaultConfig(_id))
      doc("value")
    }
  }

  def set(_id: String, v: BsonValue): Future[UpdateResult] = upsert(_id, Document(valueKey -> v))
}