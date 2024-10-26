package models
import play.api._
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MongoDB @Inject() (config: Configuration){
  import org.mongodb.scala._

  val url: String = config.get[String]("my.mongodb.url")
  private val dbName = config.get[String]("my.mongodb.db")
  
  private val mongoClient: MongoClient = MongoClient(url)
  val database: MongoDatabase = mongoClient.getDatabase(dbName);

  
  def cleanup(): Unit ={
    mongoClient.close()
  }
}