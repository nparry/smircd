package com.nparry.smircd.daemon

import scala.collection.mutable.Queue

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import org.specs.Specification

class IrcServerSpec extends Specification {

  val unitTestServer = Server()
  def connection = C(unitTestServer)

  "IrcServer" should {

    "sendMotdOnLogin" in {
      val c = connection
      c.connect()
      c.send(
        "NICK foo",
        "USER blah blah blah blah")

      c.verifyReplySequence(
        ResponseCode.RPL_MOTDSTART, 
        ResponseCode.RPL_MOTD,
        ResponseCode.RPL_ENDOFMOTD)

      verifyConnectionCounts(0, 1)
      c.verifyNickOwnership("foo")
    }

  }

  def verifyConnectionCounts(pending: Int, active: Int) = {
    (pending, active) mustEqual unitTestServer.connectionStats
  }

  object C {
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)
    def apply(s: Server.IrcServer) = new C(counter.getAndIncrement(), s)
  }

  class C(val id: Int, server: Server.IrcServer) {
    val buffer: Queue[Any] = Queue()

    override def hashCode() = id.hashCode()
    override def equals(other: Any) = other match {
      case o: C => id == o.id
      case _ => false
    }

    def connect() = server.processIncomingMsg((this, true))
    def disconnect() = server.processIncomingMsg((this, false))
    def send(msg: String*) = for (m <- msg) {
      server.processIncomingMsg((
        this,
        Command.create(CommandParser.parse(m).right.get)))
    }

    def verifyReplySequence(replies: ResponseCode.Value*) = for (r <- replies) {
      buffer.isEmpty() mustBe false
      buffer.dequeue match {
        case IrcServer.OutboundMessage(c) => {
          c.command mustEqual r.toString()
        }
        case x: Any => fail(x + " is not an outbound message (expecting " + r + ")")

      }
    }

    def verifyNickOwnership(nick: String) = {
      val u = server.getUser(this) { u => u }.getOrElse(fail("Unable to get target user"))
      val actualNick  = u.maybeNickname.getOrElse(fail("User does not have a nickname"))
      actualNick.normalized mustEqual NickName(nick).normalized
    }

  }

  object Server extends ConnectionComponent
    with IrcServerComponent
    with ChannelComponent
    with UserComponent
  {
    type Connection = C
    val sendMsg = { (connection: C, msg: Any) =>
      connection.buffer.enqueue(msg)
    }
  
    def apply(): IrcServer = {
      new IrcServer("unittest", { () => })
    }
  }
}
