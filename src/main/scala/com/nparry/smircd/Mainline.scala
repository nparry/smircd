package com.nparry.smircd

import com.nparry.smircd.netty.NettyServer
import com.nparry.smircd.daemon.IrcServer

class Mainline {

  var netty: Option[NettyServer] = None
  var daemon: Option[IrcServer] = None

  def start() = {
    daemon = Some(new IrcServer("smircd"))
    netty = Some(new NettyServer(daemon.get, 8080))

    daemon.get.start()
    netty.get.start()
  }

  def stop() = {
    for (n <- netty) { n.stop() }
    for (d <- daemon) { d ! IrcServer.Shutdown() }

    daemon = None
    netty = None
  }

}

