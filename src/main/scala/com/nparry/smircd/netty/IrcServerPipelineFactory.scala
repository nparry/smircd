package com.nparry.smircd.netty

import java.nio.charset.Charset

import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.group._
import org.jboss.netty.handler.codec.oneone._
import org.jboss.netty.handler.codec.frame._
import org.jboss.netty.handler.codec.string._
import org.jboss.netty.handler.ssl.SslHandler

import com.nparry.smircd.daemon.ActorBasedDaemon.Daemon
import com.nparry.smircd.protocol._

import grizzled.slf4j.Logger

class IrcServerPipelineFactory(ircServer: Daemon, channels: ChannelGroup) extends ChannelPipelineFactory {
  val logger = Logger(this.getClass())

  // TODO - Pass a non-dummy ssl helper as a constructor arg
  lazy val sslContext = Some(new SslHelper().createSslContext())
  val utf8 = Charset.forName("UTF-8")

  def setupIrcInPipeline(pipeline: ChannelPipeline) = {
    pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
      512, Delimiters.lineDelimiter(): _*))

    pipeline.addLast("stringify", new StringDecoder(utf8));
    pipeline.addLast("commandDecoder", new CommandDecoder())

    pipeline.addLast("destringify", new StringEncoder(utf8));
    pipeline.addLast("commandEncoder", new CommandEncoder())

    pipeline.addLast("handler", new IrcServerHandler(ircServer, channels))
  }

  override def getPipeline(): ChannelPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("multiplexer", new ProtocolMultiplexer())
    pipeline
  }

  // Based on http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/portunification/package-summary.html
  class ProtocolMultiplexer extends FrameDecoder {

    override def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer): Object = {
      if (buffer.readableBytes() < 2) {
        return null
      }

      val magic1 = buffer.getUnsignedByte(buffer.readerIndex())
      val magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1)

      if (isSsl(magic1)) {
        logger.debug("Connection from " + channel.getRemoteAddress() + " using SSL")
        enableSsl(ctx)
      }
      else {
        logger.debug("Connection from " + channel.getRemoteAddress())
      }

      setupIrcInPipeline(ctx.getPipeline())

      ctx.getPipeline().remove(this)

      // Forward the current read buffer as is to the new handlers.
      return buffer.readBytes(buffer.readableBytes())
    }

    def isSsl(magic1: Int): Boolean = {
      if (sslContext.isDefined)
        magic1 match {
          case 20 | 21 | 22 | 23 | 255 => true
          case _ => magic1 >= 128
        }
      else
        false
    }

    def enableSsl(ctx: ChannelHandlerContext) = {
      val p = ctx.getPipeline()

      val engine = sslContext.get.createSSLEngine()
      engine.setUseClientMode(false)

      p.addLast("ssl", new SslHandler(engine))
    }
  }

  class CommandDecoder extends OneToOneDecoder {
    override def decode(ctx: ChannelHandlerContext, channel: Channel, msg: Object) = {
      msg match {
        case s: String => {
          val parsed = CommandParser.parse(s)
          if (parsed.isRight)
            Command.create(parsed.right.get)
          else
            throw new IllegalArgumentException(parsed.left.get)
        }
        case default => msg
      }
    }
  }

  class CommandEncoder extends OneToOneEncoder {
    override def encode(ctx: ChannelHandlerContext, channel: Channel, msg: Object) = {
      msg match {
        case c: Command => {
          c.toString + "\r\n"
        }
        case default => msg
      }
    }
  }
}

