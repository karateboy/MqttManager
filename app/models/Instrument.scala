package models

import models.Protocol.ProtocolParam
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries.fromCodecs
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.mongodb.scala.result.UpdateResult
import play.api.libs.json._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
case class InstrumentInfo(_id: String, instType: String, state: String,
                          protocol: String, protocolParam: String, monitorTypes: String,
                          calibrationTime: Option[String], inst:Instrument, group: Option[String])

case class InstrumentStatusType(key:String, addr:Int, desc:String, unit:String)

case class Instrument(_id: String, instType: String,
                      protocol: ProtocolParam, var param: String, active: Boolean,
                      state: String,
                      statusType:Option[List[InstrumentStatusType]],
                      group: Option[String]
                     ) {
}

import models.ModelHelper._
import org.mongodb.scala._

@Singleton
class InstrumentOp @Inject() (mongoDB: MongoDB) {
  implicit val ipRead: Reads[InstrumentStatusType] = Json.reads[InstrumentStatusType]
  implicit val reader: Reads[Instrument] = Json.reads[Instrument]
  implicit val ipWrite: OWrites[InstrumentStatusType] = Json.writes[InstrumentStatusType]
  implicit val writer: OWrites[Instrument] = Json.writes[Instrument]
  implicit val infoWrites: OWrites[InstrumentInfo] = Json.writes[InstrumentInfo]

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.model._

  private val codecRegistry = fromRegistries(fromProviders(classOf[Instrument], classOf[InstrumentStatusType],
    classOf[ProtocolParam]),
    fromCodecs(Protocol.CODEC2, Protocol.CODEC3, Protocol.CODEC4), DEFAULT_CODEC_REGISTRY)
  val colName = "instruments"
  val collection: MongoCollection[Instrument] = mongoDB.database.getCollection[Instrument](colName).withCodecRegistry(codecRegistry)



  def init(): Unit = {
    for(colNames <- mongoDB.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(colName)) {
        val f = mongoDB.database.createCollection(colName).toFuture()
        f.failed.foreach(errorHandler)
      }
    }
  }
  init()

  import org.mongodb.scala.model.Filters._
  def upsertInstrument(inst: Instrument): Boolean = {
    import org.mongodb.scala.model.ReplaceOptions
    val f = collection.replaceOne(equal("_id", inst._id), inst, ReplaceOptions().upsert(true)).toFuture()
    waitReadyResult(f)
    true
  }

  def getInstrumentList: Seq[Instrument] = {
    val f = collection.find().toFuture()

    waitReadyResult(f)
  }

  def getDoInstrumentList(): Future[Seq[Instrument]] = {
    val f = collection.find(Filters.in("instType", InstrumentType.DoInstruments)).toFuture()
    f.failed.foreach(errorHandler)
    f
  }

  def getGroupDoInstrumentList(groupID: String): Future[Seq[Instrument]] = {
    val filter = Filters.and(Filters.equal("group", groupID), Filters.in("instType", InstrumentType.DoInstruments:_*))
    val f = collection.find(filter).toFuture()
    f.failed.foreach(errorHandler)
    f
  }
  def getInstrument(id: String): Seq[Instrument] = {
    val f = collection.find(equal("_id", id)).toFuture()
    waitReadyResult(f)
  }

  def getInstrumentFuture(id: String):Future[Instrument] = {
    val f = collection.find(equal("_id", id)).first().toFuture()
    f.failed.foreach(errorHandler())
    f
  }

  def getAllInstrumentFuture: Future[Seq[Instrument]] = {
    val f = collection.find().toFuture()
    f.failed.foreach(errorHandler())
    f
  }

  def delete(id: String): Boolean = {
    val f = collection.deleteOne(equal("_id", id)).toFuture()
    val ret = waitReadyResult(f)
    ret.getDeletedCount != 0
  }

  def activate(id: String): Future[UpdateResult] = {
    import org.mongodb.scala.model.Updates._
    val f = collection.updateOne(equal("_id", id), set("active", true)).toFuture()
    f.failed.foreach({
      case ex:Exception=>
        ModelHelper.logException(ex)
    })
    f
  }

  def deactivate(id: String): Future[UpdateResult] = {
    import org.mongodb.scala.model.Updates._
    val f = collection.updateOne(equal("_id", id), set("active", false)).toFuture()
    f.failed.foreach({
      case ex:Exception=>
        ModelHelper.logException(ex)
    })
    f
  }

  def setState(id:String, state:String): Future[UpdateResult] = {
    import org.mongodb.scala.model.Updates._    
    val f = collection.updateOne(equal("_id", id), set("state", state)).toFuture()
    f.failed.foreach({
      case ex:Exception=>
        ModelHelper.logException(ex)
    })
    f
  }
  
  def updateStatusType(id:String, status:List[InstrumentStatusType]): Future[UpdateResult] = {
    import org.mongodb.scala.bson.BsonArray
    import org.mongodb.scala.model.Updates._
    val bArray = new BsonArray
    
    val statusDoc = status.map{ s => bArray.add(Document(Json.toJson(s).toString).toBsonDocument)}
    
    val f = collection.updateOne(equal("_id", id), set("statusType", bArray)).toFuture()
    f.failed.foreach({
      case ex:Exception=>
        ModelHelper.logException(ex)
    })
    f
  }
  
  def getStatusTypeMap(id:String) = {
    val instList = getInstrument(id)
    if(instList.length != 1)
      throw new Exception("no such Instrument")
    
    val inst = instList(0)
    
    val statusTypeOpt = inst.statusType
    if(statusTypeOpt.isEmpty)
      Map.empty[String, String]
    else{
      val statusType = statusTypeOpt.get 
      val kv =
        for(kv <- statusType)
          yield
          kv.key -> kv.desc
      
      Map(kv:_*)
    }
  }
}