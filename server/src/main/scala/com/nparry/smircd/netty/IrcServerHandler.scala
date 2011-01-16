package com.nparry.smircd.netty

import scala.actors.Actor
import scala.actors.Actor._

import org.jboss.netty.channel._
import org.jboss.netty.channel.group._

import com.nparry.smircd.daemon.ActorBasedDaemon.Daemon
import com.nparry.smircd.daemon.IrcServer
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

class IrcServerHandler(ircServer: Daemon) extends SimpleChannelUpstreamHandler {
  val logger = Logger(this.getClass())
  
  var actor: Option[Actor] = None

  def withActor(e: ChannelEvent)(fn: (Actor) => Unit) = {
    actor.map(fn)
  }

  def open(channel: Channel): IrcServerHandler = {
    actor = Some(new ConnectionActor(channel, ircServer))
    for (a <- actor) {
      a.start()
      a ! true
    }

    this
  }

  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    logger.debug("Channel open for " + e.getChannel)
    open(e.getChannel)
  }
  
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    logger.debug("Channel closed for " + this)
    withActor(e) { a =>
      a ! false
    }

    actor = None
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    withActor(e) { a =>
      logger.trace("Channel received " + e.getMessage())
      a ! e.getMessage()
    }
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
    withActor(e) { a =>
      logger.warn("Channel caught exception " + e.getCause())
      a ! false
    }
  }

  class ConnectionActor(channel: Channel, ircServer: Daemon) extends Actor {

    var currentFuture: Option[ChannelFuture] = None

    def toServer(a: Any) = {
      ircServer ! (this, a)
    }

    def sendOutbound(c: SupportedCommand) = {
      currentFuture = Some(channel.write(c))
    }

    def act() {
      loop {
        react {
          case IrcServer.OutboundMessage(m) => sendOutbound(m)
          case IrcServer.Shutdown() => {
            currentFuture
              .map(_.addListener(ChannelFutureListener.CLOSE))
              .getOrElse(channel.close)
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

