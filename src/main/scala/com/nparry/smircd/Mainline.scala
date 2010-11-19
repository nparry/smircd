package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.ActorBasedDaemon
import com.nparry.smircd.daemon.IrcServer

import org.jboss.netty.logging._

class Mainline {
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
    new NettyServer(daemon.get, 8080)
  }

}

