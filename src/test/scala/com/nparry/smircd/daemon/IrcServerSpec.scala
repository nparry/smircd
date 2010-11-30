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

    "reflectNickChangesOnChannels" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      channelMembers("#chan") must beEqualTo(Set("foo"))

      c.send("NICK bar")
      channelMembers("#chan") must beEqualTo(Set("bar"))
    }

    "tellChanMembersAboutNickChanges" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      c2.clearBuffer()
      c1.clearBuffer().send("NICK baz")
      c1 must haveMessageSequence(":foo NICK baz")
      c2 must haveMessageSequence(":foo NICK baz")
    }

    "updateServerStateOnQuit" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      connectionCounts mustEqual (0, 1)

      c.clearBuffer().send("QUIT")

      connectionCounts mustEqual (0, 0)
      nicknames must beEqualTo(Set())
      c must beDisconnected
    }

    "updateServerStateOnAbruptDisconnect" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      connectionCounts mustEqual (0, 1)

      c.clearBuffer().disconnect()

      connectionCounts mustEqual (0, 0)
      nicknames must beEqualTo(Set())
      c must beDisconnected
    }

    "handleDisconnectEventAfterQuit" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      c.clearBuffer().send("QUIT")
      c must beDisconnected

      c.clearBuffer().disconnect()
      c must beEmpty
    }

    "tellChanMembersAboutQuits" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      c2.clearBuffer()
      c1.clearBuffer().send("QUIT :bye bye")

      c1 must beDisconnected
      c2 must haveMessageSequence(":foo QUIT :bye bye")
      channelMembers("#chan") must beEqualTo(Set("bar"))
    }

    "tellChanMembersAboutAbruptDisconnect" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      c2.clearBuffer()
      c1.clearBuffer().disconnect()

      c1 must beDisconnected
      c2 must haveMessageSequence(":foo QUIT :" + IrcServer.connectionLostMsg)
      channelMembers("#chan") must beEqualTo(Set("bar"))
    }

    "sendExpectedMsgsToFirstChannelMember" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")
      c.clearBuffer().send("JOIN #chan")

      c must haveMessageSequence(":foo JOIN #chan")
      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_NOTOPIC, List("foo", "#chan")),
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan", "foo")))
      c must haveReplySequence(ResponseCode.RPL_ENDOFNAMES)
    }

    "sendExpectedMsgsToSecondChannelMember" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah")
      c2.clearBuffer().send("JOIN #chan")

      c2 must haveMessageSequence(":bar JOIN #chan")
      c2 must haveReplySequence(ResponseCode.RPL_NOTOPIC)
      c2 must haveReplyAndParamSequence(
        (ResponseCode.RPL_NAMREPLY, List("bar", "=", "#chan", "bar foo")))
      c2 must haveReplySequence(ResponseCode.RPL_ENDOFNAMES)
    }

    "tellChanMembersAboutJoins" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      channelMembers("#chan") must beEqualTo(Set("foo"))

      c1.clearBuffer()
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      channelMembers("#chan") must beEqualTo(Set("foo", "bar"))
      c1 must haveMessageSequence(":bar JOIN #chan")
    }

    "updateChannelStateOnParts" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      channelMembers("#chan") must beEqualTo(Set("foo", "bar"))

      c2.send("PART #chan")
      channelMembers("#chan") must beEqualTo(Set("foo"))
    }

    "tellChanMembersAboutParts" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      c1.clearBuffer()
      c2.clearBuffer().send("PART #chan")

      c1 must haveMessageSequence(":bar PART #chan")
      c2 must haveMessageSequence(":bar PART #chan")
    }

    "removeChannelAfterLastMemberParts" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      channelExists("#chan") must beTrue
      c.send("PART #chan")
      channelExists("#chan") must beFalse
    }

    "sendTopicToNewChanMembers" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan",
        "TOPIC #chan :a topic")

      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah")
      c2.clearBuffer().send("JOIN #chan")

      c2 must haveMessageSequence(":bar JOIN #chan")
      c2 must haveReplyAndParamSequence(
        (ResponseCode.RPL_TOPIC, List("bar", "#chan", "a topic")))
    }

    "tellChanMembersAboutNewTopics" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan")

      c1.clearBuffer()
      c2.clearBuffer().send("TOPIC #chan :a topic")

      c1 must haveMessageSequence(":bar TOPIC #chan :a topic")
      c2 must haveMessageSequence(":bar TOPIC #chan :a topic")
    }

    "allowUserToQueryTopic" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan")

      c.clearBuffer().send("TOPIC #chan")
      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_NOTOPIC, List("foo", "#chan")))

      c.send("TOPIC #chan :a topic")

      c.clearBuffer().send("TOPIC #chan")
      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_TOPIC, List("foo", "#chan", "a topic")))
    }

    "allowUserToQueryNamesWithNoResult" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      c.clearBuffer().send("NAMES")

      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_ENDOFNAMES, List("foo", "*")))
    }

    "allowUserToQueryNamesForAllChannels" in {
      val c1 = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan1,#chan2")
      val c2 = connection.connect().send(
        "NICK bar",
        "USER blah blah blah blah",
        "JOIN #chan1,#chan3")

      c1.clearBuffer().send("NAMES")

      c1 must haveReplyAndParamSequence(
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan1", "bar foo")),
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan2", "foo")),
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan3", "bar")),
        (ResponseCode.RPL_ENDOFNAMES, List("foo", "*")))
    }

    "allowUserToQueryNamesForSingleChannel" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan1,#chan2")

      c.clearBuffer().send("NAMES #chan2")

      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan2", "foo")),
        (ResponseCode.RPL_ENDOFNAMES, List("foo", "*")))
    }

    "allowUserToQueryNamesForMultipleChannel" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah",
        "JOIN #chan1,#chan2")

      c.clearBuffer().send("NAMES #chan2,#chan1")

      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan1", "foo")),
        (ResponseCode.RPL_NAMREPLY, List("foo", "=", "#chan2", "foo")),
        (ResponseCode.RPL_ENDOFNAMES, List("foo", "*")))
    }

    "ignoreUnknownChannelsInNameQueries" in {
      val c = connection.connect().send(
        "NICK foo",
        "USER blah blah blah blah")

      c.clearBuffer().send("NAMES #bogus")

      c must haveReplyAndParamSequence(
        (ResponseCode.RPL_ENDOFNAMES, List("foo", "*")))
    }
  }

  def connectionCounts = unitTestServer.connectionStats
  def nicknames = unitTestServer.currentNicks.map(_.normalized)
  def channel(name: String) = unitTestServer.getChannel(ChannelName(name))
  def channelMembers(name: String): Set[String] = channel(name).members.map(_.normalized).toSet
  def channelExists(name: String) = unitTestServer.getDesiredChannels(List(ChannelName(name))).size != 0

  val beDisconnected = new Matcher[C]() {
    def apply(c: => C) = (
      c.buffer.size == 1 && c.buffer.endsWith(List(IrcServer.Shutdown())),
      "connection is disconnected",
      "connection is not disconnected")
  }

  val beConnected = new Matcher[C]() {
    def apply(c: => C) = (
      !c.buffer.contains(IrcServer.Shutdown()),
      "connection is connected",
      "connection is not connected")
  }

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
    def apply(c: => C) = pickMatchResult(
      matchMessages(c, replies.map(r => { cmd: SupportedCommand =>
        checkEquality(r.toString, cmd.command)
      })),
      "All replies match")
  }

  def haveReplyAndParamSequence(replies: Tuple2[ResponseCode.Value, Iterable[String]]*) = new Matcher[C] {
    def apply(c: => C) = pickMatchResult(
      matchMessages(c, replies.map(r => { cmd: SupportedCommand =>
        checkEquality(r._1.toString, cmd.command) orElse(checkEquality(r._2.toList, cmd.params.toList))
      })),
      "All replies match")
  }

  def haveMessageSequence(msgs: String*) = new Matcher[C] {
    def apply(c: => C) = pickMatchResult(
      matchMessages(c, msgs.map(m => { cmd: SupportedCommand =>
        checkEquality(Command.create(CommandParser.parse(m).right.get).toString, cmd.toString)
      })),
      "All messages match")
  }

  def haveCommandSequence(cmds: Any*) = new Matcher[C] {
    def apply(c: => C) = pickMatchResult(
      matchBufferContents(c, cmds.map(cmd => { a: Any =>
        checkEquality(cmd, a)
      })),
      "All commands match")
  }

  def pickMatchResult(r: Iterable[Tuple3[Boolean, String, String]], okMsg: String) =
    r.find(!_._1).getOrElse(tripple(true, okMsg))

  def checkEquality(expected: Any, actual: Any) =
    if (expected.equals(actual)) None
    else Some(actual + " does not equal " + expected)

  def matchMessages(c: C, predicates: Iterable[(SupportedCommand) => Option[String]]): Iterable[Tuple3[Boolean, String, String]] = {
    matchBufferContents(c, predicates.map(p => { a: Any => a match {
      case IrcServer.OutboundMessage(m) => p(m)
      case x: Any => Some("Found " + x + " instead of a message")
    }}))
  }

  def matchBufferContents(c: C, predicates: Iterable[(Any) => Option[String]]): Iterable[Tuple3[Boolean, String, String]] = {
    for (p <- predicates) yield
      if (c.buffer.isEmpty())
        tripple(false, "Empty buffer")
      else
        p(c.buffer.dequeue) match {
          case Some(error) => tripple(false, error)
          case None => tripple(true, "match")
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
