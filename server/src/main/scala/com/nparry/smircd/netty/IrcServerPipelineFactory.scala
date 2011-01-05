package com.nparry.smircd.netty

import java.nio.charset.Charset

import java.security.MessageDigest

import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.group._
import org.jboss.netty.handler.codec.oneone._
import org.jboss.netty.handler.codec.frame._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.websocket._
import org.jboss.netty.handler.codec.string._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.util.CharsetUtil

import com.nparry.smircd.daemon.ActorBasedDaemon.Daemon
import com.nparry.smircd.protocol._

import grizzled.slf4j.Logger

class IrcServerPipelineFactory(ircServer: Daemon, channels: ChannelGroup) extends ChannelPipelineFactory {
  val logger = Logger(this.getClass())

  // TODO - Pass a non-dummy ssl helper as a constructor arg
  lazy val sslContext = Some(new SslHelper().createSslContext())

  override def getPipeline(): ChannelPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("multiplexer", new ProtocolMultiplexer())
    pipeline
  }

  // Based on http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/portunification/package-summary.html
  // and http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/http/websocket/package-summary.html 
  class ProtocolMultiplexer(lookForSsl: Boolean = true) extends FrameDecoder {

    // TODO - if a client opens a connection but never sends any data, we don't
    // ever clean up and kill the connection.
    override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
      logger.debug("Channel open for " + e.getChannel)
      channels.add(e.getChannel())
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
      logger.warn("Channel caught exception " + e.getCause())
      e.getChannel().close()
    }

    override def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer): Object = {
      if (buffer.readableBytes() < 2) {
        return null
      }

      val magic1 = buffer.getUnsignedByte(buffer.readerIndex())
      val magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1)

      if (lookForSsl && isSsl(magic1)) {
        logger.debug("SSL connection from " + channel.getRemoteAddress())
        switchToSsl(ctx)
      }
      else if (isHttp(magic1, magic2)) {
        logger.debug("HTTP connection from " + channel.getRemoteAddress())
        switchToHttp(ctx)
      }
      else {
        logger.debug("IRC connection from " + channel.getRemoteAddress())
        switchToIrc(ctx)
      }

      // Whatever we choose, this handler is done
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

    def isHttp(magic1: Int, magic2: Int): Boolean = {
      magic1 == 'G' && magic2 == 'E' // GET
    }

    def switchToSsl(ctx: ChannelHandlerContext) = {
      val p = ctx.getPipeline()

      val engine = sslContext.get.createSSLEngine()
      engine.setUseClientMode(false)

      p.addLast("ssl", new SslHandler(engine))
      p.addLast("newMultiplexer", new ProtocolMultiplexer(false))
    }

    def switchToHttp(ctx: ChannelHandlerContext) = {
      val p = ctx.getPipeline()
      p.addLast("httpDecoder", new HttpRequestDecoder())
      p.addLast("httpAggregator", new HttpChunkAggregator(65536))
      p.addLast("httpEncoder", new HttpResponseEncoder())
      p.addLast("httpHandler", new SmircdHttpHandler(ircServer))
    }

    def switchToIrc(ctx: ChannelHandlerContext) = {
      val p = ctx.getPipeline()
      p.addLast("framer", new DelimiterBasedFrameDecoder(
        512, Delimiters.lineDelimiter(): _*))

      p.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8))
      p.addLast("commandDecoder", new CommandDecoder())

      p.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
      p.addLast("commandEncoder", new CommandEncoder(false))

      p.addLast("ircHandler", new IrcServerHandler(ircServer).open(ctx.getChannel))
    }
  }
}

class CommandDecoder extends OneToOneDecoder {
  override def decode(ctx: ChannelHandlerContext, channel: Channel, msg: Object) = {
    msg match {
      case s: String => decodeString(s)
      case ws: WebSocketFrame => decodeString(ws.getTextData())
      case default => msg
    }
  }

  def decodeString(s: String) = {
    val parsed = CommandParser.parse(s)
    if (parsed.isRight)
      Command.create(parsed.right.get)
    else
      throw new IllegalArgumentException(parsed.left.get)
  }
}

class CommandEncoder(emitWebsocketFrames: Boolean) extends OneToOneEncoder {
  override def encode(ctx: ChannelHandlerContext, channel: Channel, msg: Object) = {
    msg match {
      case c: Command => {
        if (emitWebsocketFrames)
          new DefaultWebSocketFrame(c.toString)
        else
          c.toString + "\r\n"
      }
      case default => msg
    }
  }
}

// Based on http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/http/websocket/WebSocketServerHandler.html
class SmircdHttpHandler(ircServer: Daemon) extends SimpleChannelUpstreamHandler {
  val logger = Logger(this.getClass())

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case req: HttpRequest => handleHttpRequest(ctx, req)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
      logger.warn("Channel caught exception " + e.getCause())
      e.getChannel().close()
  }

  def handleHttpRequest(ctx: ChannelHandlerContext, req: HttpRequest) = {
    if (Values.UPGRADE.equalsIgnoreCase(req.getHeader(Names.CONNECTION)) &&
      Values.WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {
      // Create the WebSocket handshake response.
      val rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
        new HttpResponseStatus(101, "Web Socket Protocol Handshake"))

      rsp.addHeader(Names.UPGRADE, Values.WEBSOCKET)
      rsp.addHeader(Names.CONNECTION, Values.UPGRADE)

      // Fill in the headers and contents depending on handshake method.
      if (req.containsHeader(Names.SEC_WEBSOCKET_KEY1) && req.containsHeader(Names.SEC_WEBSOCKET_KEY2)) {
        // New handshake method with a challenge:
        rsp.addHeader(Names.SEC_WEBSOCKET_ORIGIN, req.getHeader(Names.ORIGIN))
        rsp.addHeader(Names.SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req))
        Option(req.getHeader(Names.SEC_WEBSOCKET_PROTOCOL)).foreach { protocol =>
          rsp.addHeader(Names.SEC_WEBSOCKET_PROTOCOL, protocol)
        }

        val buffer = ChannelBuffers.buffer(16)

        // Calculate the answer of the challenge.
        List(Names.SEC_WEBSOCKET_KEY1, Names.SEC_WEBSOCKET_KEY2)
          .map(req.getHeader(_))
          .map(key => key.replaceAll("[^0-9]", "").toLong / key.replaceAll("[^ ]", "").length())
          .foreach(x => buffer.writeInt(x.toInt))

        buffer.writeLong(req.getContent().readLong())
        rsp.setContent(ChannelBuffers.wrappedBuffer(
          MessageDigest.getInstance("MD5").digest(buffer.array())))
      }
      else {
        // Old handshake method with no challenge:
        rsp.addHeader(Names.WEBSOCKET_ORIGIN, req.getHeader(Names.ORIGIN))
        rsp.addHeader(Names.WEBSOCKET_LOCATION, getWebSocketLocation(req))
        Option(req.getHeader(Names.WEBSOCKET_PROTOCOL)).foreach { protocol =>
          rsp.addHeader(Names.WEBSOCKET_PROTOCOL, protocol)
        }
      }

      // Upgrade the connection and send the handshake response.
      val p = ctx.getChannel().getPipeline()
      p.remove("httpDecoder")
      p.remove("httpAggregator")
      p.remove("httpHandler")

      p.addLast("websocketDecoder", new WebSocketFrameDecoder())
      p.addLast("commandDecoder", new CommandDecoder())

      ctx.getChannel().write(rsp)
      p.remove("httpEncoder")

      p.addLast("websocketEncoder", new WebSocketFrameEncoder())
      p.addLast("commandEncoder", new CommandEncoder(true))
      p.addLast("ircHandler", new IrcServerHandler(ircServer).open(ctx.getChannel))
    }
    else {
      val rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      rsp.setHeader(Names.CONTENT_TYPE, "text/html; charset=UTF-8")

      rsp.setContent(ChannelBuffers.copiedBuffer("Todo", CharsetUtil.UTF_8))
      sendHttpResponse(ctx, req, rsp)
    }
  }

  def getWebSocketLocation(req: HttpRequest) = "ws://" + req.getHeader(Names.HOST) + req.getUri()

  def sendHttpResponse(ctx: ChannelHandlerContext, req: HttpRequest, rsp: HttpResponse) = {
    // Generate an error page if response status code is not OK (200).
    if (rsp.getStatus().getCode() != 200) {
      rsp.setContent(ChannelBuffers.copiedBuffer(rsp.getStatus().toString(), CharsetUtil.UTF_8))
    }

    // Send the response and close the connection if necessary.
    setContentLength(rsp, rsp.getContent().readableBytes())
    val f = ctx.getChannel().write(rsp)
    if (!isKeepAlive(req) || rsp.getStatus().getCode() != 200) {
      f.addListener(ChannelFutureListener.CLOSE)
    }
  }
}

