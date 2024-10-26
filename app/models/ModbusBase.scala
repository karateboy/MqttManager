package models
import play.api._
import ModelHelper._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory

case class ModbusConfig(slaveID: Option[Int], monitorTypes: Option[List[String]])
case class ModelConfig(model: String, monitorTypeIDs: List[String])
case class ModbusModelConfig(model: String, mtAddrMap: Map[String, Int])
case class InputReg(addr: Int, desc: String, unit: String)
case class HoldingReg(addr: Int, desc: String, unit: String)
case class DiscreteInputReg(addr: Int, desc: String)
case class CoilReg(addr: Int, desc: String)
case class ModelReg(inputRegs: List[InputReg], holdingRegs: List[HoldingReg],
                    modeRegs: List[DiscreteInputReg], warnRegs: List[DiscreteInputReg], coilRegs: List[CoilReg])
case class ModelRegValue(inputRegs: List[(InstrumentStatusType, Float)], holdingRegs: List[(InstrumentStatusType, Float)],
                         modeRegs: List[(InstrumentStatusType, Boolean)], warnRegs: List[(InstrumentStatusType, Boolean)])

abstract class ModbusBase(modelConfig: ModbusModelConfig) extends DriverOps {
  implicit val cfgReads = Json.reads[ModbusConfig]
  implicit val cfgWrites = Json.writes[ModbusConfig]
  import Protocol.ProtocolParam

  def getModel = modelConfig.model
  def getModelConfig = modelConfig
  def readModelSetting = {
    val model = modelConfig.model
    val driverConfig = ConfigFactory.load(model)
    import java.util.ArrayList

    val inputRegList = {
      val inputRegAnyList = driverConfig.getAnyRefList(s"$model.Input.reg")
      for {
        i <- 0 to inputRegAnyList.size() - 1
        reg = inputRegAnyList.get(i)
        v = reg.asInstanceOf[ArrayList[Any]]
      } yield {
        InputReg(v.get(0).asInstanceOf[Int], v.get(1).asInstanceOf[String], v.get(2).asInstanceOf[String])
      }
    }

    val holdingRegList = {
      val holdingRegAnyList = driverConfig.getAnyRefList(s"$model.Holding.reg")
      for {
        i <- 0 to holdingRegAnyList.size() - 1
        reg = holdingRegAnyList.get(i)
        v = reg.asInstanceOf[ArrayList[Any]]
      } yield {
        HoldingReg(v.get(0).asInstanceOf[Int], v.get(1).asInstanceOf[String], v.get(2).asInstanceOf[String])
      }
    }

    val modeRegList = {
      val modeRegAnyList = driverConfig.getAnyRefList(s"$model.DiscreteInput.mode")
      for {
        i <- 0 to modeRegAnyList.size() - 1
        reg = modeRegAnyList.get(i)
        v = reg.asInstanceOf[ArrayList[Any]]
      } yield {
        DiscreteInputReg(v.get(0).asInstanceOf[Int], v.get(1).asInstanceOf[String])
      }
    }

    val warnRegList = {
      val warnRegAnyList = driverConfig.getAnyRefList(s"$model.DiscreteInput.warning")
      for {
        i <- 0 to warnRegAnyList.size() - 1
        reg = warnRegAnyList.get(i)
        v = reg.asInstanceOf[ArrayList[Any]]
      } yield {
        DiscreteInputReg(v.get(0).asInstanceOf[Int], v.get(1).asInstanceOf[String])
      }
    }

    val coilRegList = {
      val coilRegAnyList = driverConfig.getAnyRefList(s"$model.Coil.reg")
      for {
        i <- 0 to coilRegAnyList.size() - 1
        reg = coilRegAnyList.get(i)
        v = reg.asInstanceOf[ArrayList[Any]]
      } yield {
        CoilReg(v.get(0).asInstanceOf[Int], v.get(1).asInstanceOf[String])
      }
    }

    ModelReg(inputRegList.toList, holdingRegList.toList, modeRegList.toList, warnRegList.toList, coilRegList.toList)
  }

  override def verifyParam(json: String) = {
    val param = validateParam(json)
    //Append monitor Type into config
    val slaveID = param.slaveID.getOrElse(1)
    val mt = modelConfig.mtAddrMap.keys.toList
    val newParam = ModbusConfig(Some(slaveID), Some(mt))
    Json.toJson(newParam).toString()
  }

  override def getMonitorTypes(param: String): List[String] = {
    val config = validateParam(param)
    if (config.monitorTypes.isDefined)
      config.monitorTypes.get
    else
      List.empty[String]
  }

  def validateParam(json: String) = {
    val ret = Json.parse(json).validate[ModbusConfig]
    ret.fold(
      error => {
        logger.error(JsError.toJson(error).toString())
        throw new Exception(JsError.toJson(error).toString())
      },
      param => param)
  }

  //Seperate calibration from modbus...
  override def getCalibrationTime(param: String) = {
    None
  }
}