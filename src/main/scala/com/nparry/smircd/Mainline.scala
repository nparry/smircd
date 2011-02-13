package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.ActorBasedDaemon
import com.nparry.smircd.daemon.IrcServer

import org.jboss.netty.logging._

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level

import scala.annotation.tailrec

object Mainline {
  val logger = grizzled.slf4j.Logger(this.getClass())

  case class Args(
    port: Int = 6667,
    logLevel: String = Level.INFO.toString(),
    inSbt: Boolean = false)

  @tailrec def processArgs(current: Args, args: List[String]): Args = {
    args match {
      case "--port" :: port :: rest => processArgs(current.copy(port=port.toInt), rest)
      case "--logLevel" :: level :: rest => processArgs(current.copy(logLevel=level), rest)
      case "--inSbt" :: rest => processArgs(current.copy(inSbt=true), rest)
      case unknown :: rest => {
        logger.warn("Unknown argument: " + unknown)
        processArgs(current, rest)

      }
      case Nil => current
    }
  }

  val ChangeLogLevel = """\s*level\s*=?\s*(\S+)\s*""".r
  @tailrec def waitForQuitInSbt(): Unit = {
    Console.readLine() match {
      case "" => return
      case ChangeLogLevel(level) => {
        Console.print("Changing log level to " + level + "\n")
        setLogLevel(level)
        waitForQuitInSbt()
      }
      case _ => waitForQuitInSbt()
    }
  }

  def setLogLevel(level: String) = {
    try {
      org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
        case l: Logger => l.setLevel(Level.toLevel(level))
        case a: Any => throw new IllegalStateException("Unexpected logger type: " + a.getClass())
      }
    } catch {
      case e: Exception => logger.error("Unable to set logging level to " + level + ": " + e.getMessage())
    }
  }

  def apply(args: String = "") = main(("--inSbt " + args).trim().split(" "))

  def main(args: Array[String]) {
    val arguments = processArgs(Args(), args.toList)

    setLogLevel(arguments.logLevel)

    val m = new Mainline(arguments.port)
    m.start()

    if (arguments.inSbt) {
      try {
        Console.print("Press enter to quit...\n")
        waitForQuitInSbt()
      } finally {
        m.stop
      }
    }
    else {
      Runtime.getRuntime().addShutdownHook(new Thread() { override def run {
        m.stop
      }})

      // Block until ^C
      synchronized {
        logger.info("Started on port " + arguments.port)
        wait()
      }
    }
  }
}

class Mainline(port: Int) {
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())

  var netty: Option[NettyServer] = None
  var daemon: Option[ActorBasedDaemon.Daemon] = None

  def start() = {
    daemon = Some(makeDaemon)
    netty = Some(makeNetty)

    daemon.get.start()
    netty.get.start()
  }

  def stop() = {
    for (n <- netty) { n.stop() }
    for (d <- daemon) { d ! IrcServer.Shutdown() }

    daemon = None
    netty = None
  }

  def makeDaemon = {
    ActorBasedDaemon("smircd")
  }

  def makeNetty = {
    new NettyServer(daemon.get, port)
  }

}

