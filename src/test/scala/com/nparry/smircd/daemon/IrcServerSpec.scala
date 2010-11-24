package com.nparry.smircd.daemon

import scala.collection.mutable.Queue

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import org.specs.Specification
import org.specs.matcher.Matcher

class IrcServerSpec extends Specification {

  val unitTestServer = Server()
  def connection = C(unitTestServer)

  "IrcServer" should {

    "sendMotdOnLogin" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      connectionCounts mustEqual (0, 1)
      c must ownNickName("foo")
      c must haveReplySequence(
        ResponseCode.RPL_MOTDSTART, 
        ResponseCode.RPL_MOTD,
        ResponseCode.RPL_ENDOFMOTD)

    }

    "supportNickNameChanges" in {
      val c = connection.connect().send("NICK foo")
      c must ownNickName("foo")
      nicknames must beEqualTo(Set("foo"))

      c.send("NICK bar")
      c must ownNickName("bar")
      nicknames must beEqualTo(Set("bar"))

      c.send("USER blah blah blah blah")
      c must ownNickName("bar")
      nicknames must beEqualTo(Set("bar"))

      c.send("NICK baz")
      c must ownNickName("baz")
      nicknames must beEqualTo(Set("baz"))
    }

    "preventPendingNickClashVsPendingConnection" in {
      val c1 = connection.connect()
      val c2 = connection.connect()

      connectionCounts mustEqual (2, 0)

      c1.send("NICK foo")
      c2.send("NICK Foo")

      connectionCounts mustEqual (1, 0)
      c1 must beEmpty
      c1 must ownNickName("foo")
      c2 must haveReplySequence(ResponseCode.ERR_NICKNAMEINUSE)
      c2 must beDisconnected
    }

    "preventPendingNickClashVsActiveConnection" in {
      val c1 = connection.connect()
      val c2 = connection.connect()

      connectionCounts mustEqual (2, 0)

      c1.send(
        "NICK foo",
        "USER blah blah blah blah")
      connectionCounts mustEqual (1, 1)
      c1 must ownNickName("foo")

      c2.send("NICK Foo")

      connectionCounts mustEqual (0, 1)
      c1 must beConnected // Not really obeying IRC spec
      c1 must ownNickName("foo")
      c2 must haveReplySequence(ResponseCode.ERR_NICKNAMEINUSE)
      c2 must beDisconnected
    }

    "preventActiveNickClashVsPendingConnection" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")
      val c2 = connection.connect().send("NICK bar")

      connectionCounts mustEqual (1, 1)
      c1 must ownNickName("foo")
      c2 must ownNickName("bar")

      c1.clearBuffer().send("NICK bar")

      connectionCounts mustEqual (1, 1)
      c1 must beConnected
      c1 must haveReplySequence(ResponseCode.ERR_NICKCOLLISION)
      c1 must ownNickName("foo")
      c2 must beConnected
      c2 must beEmpty
      c2 must ownNickName("bar")
    }

    "preventActiveNickClashVsActiveConnection" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah")

      connectionCounts mustEqual (0, 2)
      c1 must ownNickName("foo")
      c2 must ownNickName("bar")

      c2.clearBuffer()
      c1.clearBuffer().send("NICK bar")

      connectionCounts mustEqual (0, 2)
      c1 must beConnected
      c1 must haveReplySequence(ResponseCode.ERR_NICKCOLLISION)
      c1 must ownNickName("foo")
      c2 must beConnected
      c2 must beEmpty
      c2 must ownNickName("bar")
    }
  }

  def connectionCounts = unitTestServer.connectionStats
  def nicknames = unitTestServer.currentNicks.map(_.normalized)

  val beDisconnected = new Matcher[C]() {
    def apply(c: => C) = (
      c.buffer.endsWith(List(IrcServer.Shutdown())),
      "connection is disconnected",
      "connection is not disconnected")
  }

  val beConnected = beDisconnected.not

  def ownNickName(nick: String) = new Matcher[C] {
    def apply(c: => C) = c.server.getUser(c) { u => u } match {
      case None => tripple(false, "No user")
      case Some(u) => u.maybeNickname match {
        case None => tripple(false, "No nickname")
        case Some(n) => (
          n.normalized.equals(NickName(nick).normalized),
          nick + " equals " + n.normalized.normalized,
          nick + " does not equal " + n.normalized.normalized)
      }
    }
  }

  def haveReplySequence(replies: ResponseCode.Value*) = new Matcher[C] {
    def apply(c: => C) = matchReplies(c, replies: _*).find(!_._1).getOrElse(tripple(true, "All replies match"))
  }

  def matchReplies(c: C, replies: ResponseCode.Value*): Iterable[Tuple3[Boolean, String, String]] = {
    for (r <- replies) yield
      if (c.buffer.isEmpty())
        tripple(false, "Empty buffer looking for " + r)
      else
        c.buffer.dequeue match {
          case IrcServer.OutboundMessage(c) =>
            if (c.command.equals(r.toString())) tripple(true, "match")
            else tripple(false, c.command + " does not match " + r)
          case x: Any => tripple(false,  "Found " + x + " looking for " + r)
        }
  }

  def tripple(b: Boolean, s: String) = (b, s, s)

  object C {
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)
    def apply(s: Server.IrcServer) = new C(counter.getAndIncrement(), s)
  }

  class C(val id: Int, val server: Server.IrcServer) {
    val buffer: Queue[Any] = Queue()

    override def hashCode() = id.hashCode()
    override def equals(other: Any) = other match {
      case o: C => id == o.id
      case _ => false
    }

    def isEmpty = buffer.isEmpty
    def clearBuffer() = { buffer.clear(); this }

    def connect() = { server.processIncomingMsg((this, true)); this }
    def disconnect() = { server.processIncomingMsg((this, false)); this }
    def send(msg: String*) = {
      for (m <- msg) {
        server.processIncomingMsg((
          this,
          Command.create(CommandParser.parse(m).right.get)))
      }
      this
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
