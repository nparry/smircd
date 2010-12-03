package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.ActorBasedDaemon
import com.nparry.smircd.daemon.IrcServer

import org.jboss.netty.logging._

object Mainline {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Usage: java -jar <jar> <port>")
      return
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

