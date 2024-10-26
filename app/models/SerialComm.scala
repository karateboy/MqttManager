package models

import com.serotonin.modbus4j.serial.SerialPortWrapper

import java.io.InputStream
import java.io.OutputStream
import jssc.SerialPort
import play.api._

case class SerialComm(port: SerialPort, is: SerialInputStream, os: SerialOutputStream) {
  val logger = Logger(this.getClass)
  var readBuffer = Array.empty[Byte]
  def getLine = {
    def splitLine(buf: Array[Byte]): List[String] = {
      val idx = buf.indexOf('\n'.toByte)
      if (idx == -1) {
        val cr_idx = buf.indexOf('\r'.toByte)
        if (cr_idx == -1) {
          readBuffer = buf
          Nil
        } else {
          val (a, rest) = buf.splitAt(cr_idx + 1)
          new String(a).trim() :: splitLine(rest)
        }
      } else {
        val (a, rest) = buf.splitAt(idx + 1)
        new String(a).trim() :: splitLine(rest)
      }
    }

    val ret = port.readBytes()
    if (ret != null)
      readBuffer = readBuffer ++ ret

    splitLine(readBuffer)
  }

  def getLine2 = {
    def splitLine(buf: Array[Byte]): List[String] = {
      val idx = buf.indexOf('\n'.toByte)
      if (idx == -1) {
        val cr_idx = buf.indexOf('\r'.toByte)
        if (cr_idx == -1) {
          readBuffer = buf
          Nil
        } else {
          val (a, rest) = buf.splitAt(cr_idx + 1)
          new String(a).trim() :: splitLine(rest)
        }
      } else {
        val (a, rest) = buf.splitAt(idx + 1)
        new String(a) :: splitLine(rest)
      }
    }

    val ret = port.readBytes()
    if (ret != null)
      readBuffer = readBuffer ++ ret

    splitLine(readBuffer)
  }

  def getLine2(timeout: Int):List[String] = {
    import com.github.nscala_time.time.Imports._
    var strList = getLine2
    val startTime = DateTime.now()
    while (strList.isEmpty) {
      val elapsedTime = new Duration(startTime, DateTime.now())
      if (elapsedTime.getStandardSeconds > timeout) {
        throw new Exception("Read timeout!")
      }
      strList = getLine2
    }
    strList
  }

  def getLine3 = {
    def splitLine(buf: Array[Byte]): List[String] = {
      val idx = buf.indexOf('\n'.toByte)
      if (idx == -1) {
        val cr_idx = buf.indexOf('\r'.toByte)
        if (cr_idx == -1) {
          readBuffer = buf
          Nil
        } else {
          val (a, rest) = buf.splitAt(cr_idx + 1)
          new String(a).trim() :: splitLine(rest)
        }
      } else {
        val (a, rest) = buf.splitAt(idx + 1)
        new String(a) :: splitLine(rest)
      }
    }

    val ret = port.readBytes()
    if (ret != null)
      readBuffer = readBuffer ++ ret

    logger.info(s"readBuffer len=${readBuffer.length}")
    splitLine(readBuffer)
  }

  def close(): Unit = {
    logger.info(s"port is closed")
    is.close()
    os.close()
    port.closePort()
    readBuffer = Array.empty[Byte]
  }
}

object SerialComm {
  def open(n: Int): SerialComm = {
    open(n, SerialPort.BAUDRATE_9600)
  }

  def open(n: Int, baudRate: Int): SerialComm = {
    val port = new SerialPort(s"COM${n}")
    if (!port.openPort())
      throw new Exception(s"Failed to open COM$n")

    port.setParams(baudRate,
      SerialPort.DATABITS_8,
      SerialPort.STOPBITS_1,
      SerialPort.PARITY_NONE); //Set params. Also you can set params by this string: serialPort.setParams(9600, 8, 1, 0);

    val is = new SerialInputStream(port)
    val os = new SerialOutputStream(port)
    SerialComm(port, is, os)
  }

  def close(sc: SerialComm): Unit = {
    sc.close()
  }
}

class SerialOutputStream(port: SerialPort) extends OutputStream {
  override def write(b: Int): Unit = {
    port.writeByte(b.toByte)
  }
}

class SerialInputStream(serialPort: jssc.SerialPort) extends InputStream {
  override def read(): Int = {
    val retArray = serialPort.readBytes(1)
    if(retArray.isEmpty)
      -1
    else
      retArray(0)
  }
}

class SerialRTU(n: Int, baudRate: Int) extends SerialPortWrapper {
  val logger = Logger(this.getClass)
  var serialCommOpt : Option[SerialComm]= None

  override def close(): Unit = {
    logger.info(s"SerialRTU COM${n} close")

    for(serialComm <- serialCommOpt)
      serialComm.close()
  }

  override def open(): Unit = {
    logger.info(s"SerialRTU COM${n} open")
    serialCommOpt = Some(SerialComm.open(n, baudRate))
  }

  override def getInputStream: InputStream = serialCommOpt.get.is

  override def getOutputStream: OutputStream = serialCommOpt.get.os

  override def getBaudRate: Int = baudRate

  override def getFlowControlIn: Int = 0

  override def getFlowControlOut: Int = 0

  override def getDataBits: Int = 8

  override def getStopBits: Int = 1

  override def getParity: Int = 0
}
