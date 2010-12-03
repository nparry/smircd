package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.ActorBasedDaemon
import com.nparry.smircd.daemon.IrcServer

import org.jboss.netty.logging._

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level

object Mainline {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Usage: java -jar <jar> <port> [<log level>]")
      return
    }

    val level =
      if (args.length > 1) args(1)
      else "INFO"

    org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
      case l: Logger => l.setLevel(Level.toLevel(level))
      case default => System.err.println("Unable to set logging level")
    }

    val port = args(0).toInt
    val m = new Mainline(port)
    m.start()
    Runtime.getRuntime().addShutdownHook(new Thread() { override def run {
      m.stop
    }})

    synchronized {
      wait()
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

