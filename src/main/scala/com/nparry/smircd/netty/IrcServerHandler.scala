package com.nparry.smircd.netty

import scala.actors.Actor
import scala.actors.Actor._

import org.jboss.netty.channel._
import org.jboss.netty.channel.group._

import com.nparry.smircd.daemon.IrcServer
import com.nparry.smircd.daemon.IrcServer._
import com.nparry.smircd.protocol.Command._

class IrcServerHandler(ircServer: IrcServer, channels: ChannelGroup) extends SimpleChannelUpstreamHandler {
  
  var actor: Option[Actor] = None

  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    channels.add(e.getChannel)
  }
  
  override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    actor = Some(new ConnectionActor(e.getChannel(), ircServer))
    for (a <- actor) {
      a.start()
      a ! true
    }
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    for (a <- actor) {
      a ! false
      a ! IrcServer.Shutdown()
    }

    actor = None
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    for (a <- actor) {
      a ! e.getMessage()
    }
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
    e.getChannel.close
  }

  class ConnectionActor(channel: Channel, ircServer: IrcServer) extends Actor {

    def toServer(a: Any) = {
      ircServer ! (this, a)
    }

    def sendOutbound(c: SupportedCommand) = {
      channel.write(c)
    }

    def act() {
      loop {
        react {
          case IrcServer.OutboundMessage(m) => sendOutbound(m)
          case IrcServer.Shutdown() => {
            channel.close
            exit() 
          }
          case b: Boolean => toServer(b)
          case c: SupportedCommand => toServer(c)
          case c: UnsupportedCommand => sendOutbound(
            SupportedCommand(ircServer.serverId, c.rspCode.toString, List(c.command)))
          case c: InvalidCommand => sendOutbound(
            SupportedCommand(ircServer.serverId, c.rspCode.toString, List(c.message)))
        }
      }
    }

  }

}

