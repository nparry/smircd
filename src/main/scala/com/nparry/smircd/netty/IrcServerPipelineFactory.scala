package com.nparry.smircd.netty

import java.nio.charset.Charset

import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.group._
import org.jboss.netty.handler.codec.oneone._
import org.jboss.netty.handler.codec.frame._
import org.jboss.netty.handler.codec.string._

import com.nparry.smircd.daemon.IrcServer
import com.nparry.smircd.protocol._

class IrcServerPipelineFactory(ircServer: IrcServer, channels: ChannelGroup) extends ChannelPipelineFactory {

  val newLine: Array[Byte] = Array('\r', '\n')
  val utf8 = Charset.forName("UTF-8")

  override def getPipeline(): ChannelPipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
      512, ChannelBuffers.wrappedBuffer(newLine)))

    pipeline.addLast("stringify", new StringDecoder(utf8));
    pipeline.addLast("commandDecoder", new CommandDecoder())

    pipeline.addLast("destringify", new StringEncoder(utf8));
    pipeline.addLast("commandEncoder", new CommandEncoder())

    pipeline.addLast("handler", new IrcServerHandler(ircServer, channels))

    pipeline
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

