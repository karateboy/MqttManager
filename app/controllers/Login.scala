package controllers

import models.{Ability, GroupOp, User, UserOp}
import play.api.libs.json._
import play.api.mvc._

case class Credential(user: String, password: String)
import models.Group

import javax.inject._

case class UserData(user: User, group: Group)

/**
 * @author user
 */
class Login @Inject()
(userOp: UserOp,
 groupOp: GroupOp,
 security: Security,
 cc: ControllerComponents) extends AbstractController(cc) {
  implicit val credentialReads: Reads[Credential] = Json.reads[Credential]

  def authenticate: Action[JsValue] = Action(parse.json) {
    implicit request =>
      val credential = request.body.validate[Credential]
      credential.fold(
        error => {
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error)))
        },
        crd => {
          val userOpt = userOp.getUserByEmail(crd.user)
          if (userOpt.isEmpty || userOpt.get.password != crd.password) {
            Results.Unauthorized(Json.obj("ok" -> false, "msg" -> "密碼或帳戶錯誤"))
          } else {
            implicit val writes = Json.writes[User]
            implicit val w3 = Json.writes[Ability]
            implicit val w1 = Json.writes[Group]
            implicit val w2 = Json.writes[UserData]

            val user = userOpt.get
            val userGroup = {
              user.group.getOrElse({
                if (user.isAdmin)
                  Group.PLATFORM_ADMIN
                else
                  Group.PLATFORM_USER
              })
            }
            val userInfo = UserInfo(user._id, user.name, userGroup, user.isAdmin)
            val group = groupOp.getGroupByID(userGroup).get
            Ok(Json.obj("ok" -> true, "userData" -> UserData(user, group))).withSession(security.setUserinfo(request, userInfo))
          }
        })
  }

  def isLogin: Action[AnyContent] = security.Authenticated {
    Ok(Json.obj("ok" -> true))
  }

  def logout: Action[AnyContent] = Action {
    Ok("").withNewSession
  }
}