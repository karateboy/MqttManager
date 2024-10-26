package controllers

import models.ImageOp
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Camera @Inject()(imageOp: ImageOp,
                       security: Security,
                       cc: ControllerComponents) extends AbstractController(cc) {

  def getImage(id: String): Action[AnyContent] = security.Authenticated.async {
    import org.mongodb.scala.bson._

    val f = imageOp.getImage(new ObjectId(id))
    for (ret <- f) yield {
      Ok(ret.content).as("image/jpeg")
    }
  }

}
