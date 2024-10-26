package models
import play.api._
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import ModelHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import Protocol.ProtocolParam
import com.github.nscala_time.time
import com.github.nscala_time.time.Imports
import com.google.inject.assistedinject.Assisted

object GpsCollector extends DriverOps {
  override def getMonitorTypes(param: String): List[String] = {
    val lat = "LAT"
    val lng = "LNG"
    List(lat, lng)
  }

  override def verifyParam(json: String): String = {
    json
  }

  override def getCalibrationTime(param: String): Option[time.Imports.LocalTime] = None

  var count = 0

  trait Factory {
    def apply(id: String, protocolParam: ProtocolParam): Actor
  }

  override def factory(id: String, protocol: ProtocolParam, param: String)(f: AnyRef): Actor ={
    assert(f.isInstanceOf[Factory])
    val f2 = f.asInstanceOf[Factory]
    f2(id, protocol)
  }
}

import net.sf.marineapi.nmea.io.ExceptionListener;
import net.sf.marineapi.nmea.io.SentenceReader;
import net.sf.marineapi.provider.PositionProvider;
import net.sf.marineapi.provider.event.PositionEvent;
import net.sf.marineapi.provider.event.PositionListener;
import net.sf.marineapi.nmea.event.SentenceEvent;
import net.sf.marineapi.nmea.event.SentenceListener;
import net.sf.marineapi.nmea.io.SentenceReader;
import net.sf.marineapi.nmea.sentence.SentenceValidator;

import javax.inject._

class GpsCollector @Inject()(monitorTypeOp: MonitorTypeOp)(@Assisted id: String, @Assisted protocolParam: ProtocolParam) extends Actor
    with ActorLogging with SentenceListener with ExceptionListener with PositionListener {
  val comm: SerialComm = SerialComm.open(protocolParam.comPort.get)
  var reader: SentenceReader = _

  import scala.concurrent.Future
  import scala.concurrent.blocking

  def receive: Receive = handler(MonitorStatus.NormalStat)

  def handler(collectorState: String): Receive = {
    case SetState(id, state) =>
      logger.warn(s"Ignore $self => $state")
  }

  def init(): Unit = {
    logger.info("Init GPS reader...")
    val stream = comm.is
    reader = new SentenceReader(stream)
    reader.setExceptionListener(this)
    val provider = new PositionProvider(reader)
    provider.addListener(this)
    reader.start()
  }

  init()

  override def postStop(): Unit = {
    if (reader != null) {
      reader.stop()
    }

    if (comm != null)
      SerialComm.close(comm)
  }

  import com.github.nscala_time.time.Imports._
  var reportTime: DateTime = DateTime.now
  def providerUpdate(evt: PositionEvent): Unit = {
    if (reportTime < DateTime.now - 3.second) {
      val lat = MonitorTypeData(MonitorType.LAT, evt.getPosition.getLatitude, MonitorStatus.NormalStat)
      val lng = MonitorTypeData(MonitorType.LNG, evt.getPosition.getLongitude, MonitorStatus.NormalStat)
      context.parent ! ReportData(List(lat, lng))
      reportTime = DateTime.now
    }
  }

  def onException(ex: Exception): Unit = {
    logger.warn(ex.getMessage)
  }

  /*
	 * (non-Javadoc)
	 * @see net.sf.marineapi.nmea.event.SentenceListener#readingPaused()
	 */
  def readingPaused(): Unit = {
    logger.debug("-- Paused --");
  }

  /*
	 * (non-Javadoc)
	 * @see net.sf.marineapi.nmea.event.SentenceListener#readingStarted()
	 */
  def readingStarted(): Unit = {
    logger.debug("-- Started --");
  }

  /*
	 * (non-Javadoc)
	 * @see net.sf.marineapi.nmea.event.SentenceListener#readingStopped()
	 */
  def readingStopped(): Unit = {
    logger.debug("-- Stopped --");
  }

  /*
	 * (non-Javadoc)
	 * @see
	 * net.sf.marineapi.nmea.event.SentenceListener#sentenceRead(net.sf.marineapi
	 * .nmea.event.SentenceEvent)
	 */
  def sentenceRead(event: SentenceEvent): Unit = {
    // here we receive each sentence read from the port
    logger.debug(event.getSentence.toString);
  }

}
