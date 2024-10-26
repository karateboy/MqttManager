package models
import play.api._
import akka.actor._
import ModelHelper._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Adam4017Collector {
  import Protocol.ProtocolParam

  case class PrepareCollect(id: String, com: Int, param: List[Adam4017Param])
  case object Collect

  var count = 0
  def start(id: String, protocolParam: ProtocolParam, param: List[Adam4017Param])(implicit context: ActorContext) = {
    val collector = context.actorOf(Props[Adam4017Collector], name = "Adam4017Collector" + count)
    count += 1
    assert(protocolParam.protocol == Protocol.Serial())
    val com = protocolParam.comPort.get
    collector ! PrepareCollect(id, com, param)
    collector
  }

  trait Factory {
    def apply(id: String, protocol: ProtocolParam, param:  List[Adam4017Param]): Actor
  }
}

import javax.inject._
class Adam4017Collector @Inject()(monitorTypeOp: MonitorTypeOp, system: ActorSystem, instrumentOp: InstrumentOp) extends Actor {
  import Adam4017Collector._
  import java.io.BufferedReader
  import java.io._

  var instId: String = _
  var cancelable: Cancellable = _
  var comm: SerialComm = _
  var paramList: List[Adam4017Param] = _

  def decode(str: String)(param: Adam4017Param) = {
    val ch = str.substring(1).split("(?=[+-])", 8)
    if (ch.length != 8)
      throw new Exception("unexpected format:" + str)

    import java.lang._
    val values = ch.map { Double.valueOf(_) }
    val dataPairList =
      for {
        cfg <- param.ch.zipWithIndex
        (chCfg, idx) = cfg if chCfg.enable
        rawValue = values(idx)
        mt <- chCfg.mt
        mtMin <- chCfg.mtMin
        mtMax <- chCfg.mtMax
        max <- chCfg.max
        min <- chCfg.min
      } yield {
        val v = mtMin + (mtMax - mtMin) / (max - min) * (values(idx) - min)
        val status = if (MonitorTypeCollectorStatus.map.contains(mt))
          MonitorTypeCollectorStatus.map(mt)
        else {
          if (chCfg.repairMode.getOrElse(false))
            MonitorStatus.MaintainStat
          else
            collectorState
        }
        val rawMt = monitorTypeOp.getRawMonitorType(mt)
        List(MonitorTypeData(mt, v, status), MonitorTypeData(rawMt, rawValue, status))
      }
    val dataList = dataPairList.flatMap { x => x }
    context.parent ! ReportData(dataList.toList)
  }

  import scala.concurrent.Future
  import scala.concurrent.blocking

  var collectorState = MonitorStatus.NormalStat
  def receive = {
    case PrepareCollect(id, com, param) =>
      Future {
        blocking {
          try {
            instId = id
            paramList = param
            comm = SerialComm.open(com)
            cancelable = system.scheduler.scheduleOnce(Duration(3, SECONDS), self, Collect)
          } catch {
            case ex: Exception =>
              logger.error(ex.getMessage, ex)
              logger.info("Try again 1 min later...")
              //Try again
              cancelable = system.scheduler.scheduleOnce(Duration(1, MINUTES), self, PrepareCollect(id, com, param))
          }

        }
      }.failed.foreach(errorHandler)

    case Collect =>
      Future {
        blocking {
          import com.github.nscala_time.time.Imports._
          val os = comm.os
          val is = comm.is
          try {
            for (param <- paramList) {
              val readCmd = s"#${param.addr}\r"
              os.write(readCmd.getBytes)
              var strList = comm.getLine
              val startTime = DateTime.now
              while (strList.length == 0) {
                val elapsedTime = new Duration(startTime, DateTime.now)
                if (elapsedTime.getStandardSeconds > 1) {
                  throw new Exception("Read timeout!")
                }
                strList = comm.getLine
              }

              for (str <- strList) {
                decode(str)(param)
              }
            }

          } catch (errorHandler)
          finally {
            cancelable = system.scheduler.scheduleOnce(scala.concurrent.duration.Duration(3, SECONDS), self, Collect)
          }
        }
      }.failed.foreach(errorHandler)

    case SetState(id, state) =>
      logger.info(s"$self => $state")
      instrumentOp.setState(id, state)
      collectorState = state
  }

  override def postStop(): Unit = {
    if (cancelable != null)
      cancelable.cancel()

    if (comm != null)
      SerialComm.close(comm)
  }
}