package com.nparry.smircd.netty

import java.net._
import java.util.concurrent._

import org.jboss.netty.bootstrap._
import org.jboss.netty.channel._
import org.jboss.netty.channel.group._
import org.jboss.netty.channel.socket.nio._

import com.nparry.smircd.daemon.ActorBasedDaemon

class NettyServer(ircServer: ActorBasedDaemon#Daemon, port: Int) {

  var channels: Option[ChannelGroup] = None
  var channelFactory: Option[ChannelFactory] = None

  def start() = {
    channels = Some(new DefaultChannelGroup(ircServer.serverId))
    channelFactory = Some(new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool()))

    val bootstrap = new ServerBootstrap(channelFactory.get)
    bootstrap.setPipelineFactory(new IrcServerPipelineFactory(ircServer, channels.get))

    val channel = bootstrap.bind(new InetSocketAddress(port))
    channels.get.add(channel)
  }

  def stop() = {
    channels.map(_.close).map(_.awaitUninterruptibly)
    channelFactory.map(_.releaseExternalResources)
    channels = None
    channelFactory = None
  }

}

