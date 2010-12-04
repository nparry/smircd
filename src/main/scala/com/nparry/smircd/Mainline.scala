package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.ActorBasedDaemon
import com.nparry.smircd.daemon.IrcServer

import org.jboss.netty.logging._

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level

object Mainline {
  case class Args(
    port: Int = 6667,
    logLevel: String = "INFO",
    inSbt: Boolean = false)

  def processArgs(current: Args, args: List[String]): Args = {
    args match {
      case "--port" :: port :: rest => processArgs(current.copy(port=port.toInt), rest)
      case "--logLevel" :: level :: rest => processArgs(current.copy(logLevel=level), rest)
      case "--inSbt" :: rest => processArgs(current.copy(inSbt=true), rest)
      case unknown :: rest => {
        System.err.println("Unknown argument: " + unknown)
        processArgs(current, rest)

      }
      case Nil => current
    }
  }

  def apply(args: String = "") = main(("--inSbt " + args).trim().split(" "))

  def main(args: Array[String]) {
    val arguments = processArgs(Args(), args.toList)

    org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
      case l: Logger => l.setLevel(Level.toLevel(arguments.logLevel))
      case default => System.err.println("Unable to set logging level")
    }

    val m = new Mainline(arguments.port)
    m.start()

    if (arguments.inSbt) {
      Console.readLine("Press enter to quit...\n")
      m.stop
    }
    else {
      Runtime.getRuntime().addShutdownHook(new Thread() { override def run {
        m.stop
      }})

      // Block until ^C
      synchronized {
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

