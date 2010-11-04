package com.nparry.smircd.protocol

import org.specs.Specification
import Command._

class CommandSpec extends Specification {

  "Command" should {
    "handleParseableButUnknownCommand" in {
      cmd[UnsupportedCommand]("FOO bar baz").raw mustEqual "FOO bar baz"
    }

    "handlePassCmd" in {
      cmd[PassCommand]("PASS foo").password mustEqual "foo"
      cmd[PassCommand]("PASS foo ignored").password mustEqual "foo"
    }

    "rejectInvalidPassCmd" in {
      cmd[InvalidCommand]("PASS").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleNickCmd" in {
      cmd[NickCommand]("NICK Foo").nickname mustEqual "Foo"
      cmd[NickCommand]("NICK Foo ignored").nickname mustEqual "Foo"
    }

    "rejectInvalidNickCmd" in {
      cmd[InvalidCommand]("NICK").rspCode mustEqual ResponseCode.ERR_NONICKNAMEGIVEN
    }

    "handleUserCmd" in {
      cmd[UserCommand]("USER user ignored ignored :real name").username mustEqual "user"
      cmd[UserCommand]("USER user ignored ignored :real name").realname mustEqual "real name"
      cmd[UserCommand]("USER user ignored ignored real ignored").realname mustEqual "real"
    }

    "rejectInvalidUserCmd" in {
      cmd[InvalidCommand]("USER").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("USER 1").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("USER 1 2").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("USER 1 2 3").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleOperCmd" in {
      cmd[OperCommand]("OPER someone pw").user mustEqual "someone"
      cmd[OperCommand]("OPER someone pw").password mustEqual "pw"
      cmd[OperCommand]("OPER someone pw ignore").password mustEqual "pw"
    }

    "rejectInvalidOperCmd" in {
      cmd[InvalidCommand]("OPER").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("OPER blah").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleQuitCmd" in {
      cmd[QuitCommand]("QUIT").message mustEqual None
      cmd[QuitCommand]("QUIT foo").message mustEqual Some("foo")
      cmd[QuitCommand]("QUIT foo ignore").message mustEqual Some("foo")
    }

    "handleJoinCmd" in {
      cmd[JoinCommand]("JOIN #foo").targets(0)._1.name mustEqual "#foo"
      cmd[JoinCommand]("JOIN #foo").targets(0)._2 mustEqual None

      cmd[JoinCommand]("JOIN #foo").targets(0)._1.name mustEqual "#foo"
      cmd[JoinCommand]("JOIN #foo bar").targets(0)._2 mustEqual Some("bar")

      cmd[JoinCommand]("JOIN #foo,&bar").targets(0)._1.name mustEqual "#foo"
      cmd[JoinCommand]("JOIN #foo,&bar").targets(1)._1.name mustEqual "&bar"
      cmd[JoinCommand]("JOIN #foo,&bar").targets(0)._2 mustEqual None
      cmd[JoinCommand]("JOIN #foo,&bar").targets(1)._2 mustEqual None

      cmd[JoinCommand]("JOIN #foo,&bar a,b").targets(0)._1.name mustEqual "#foo"
      cmd[JoinCommand]("JOIN #foo,&bar a,b").targets(1)._1.name mustEqual "&bar"
      cmd[JoinCommand]("JOIN #foo,&bar a,b").targets(0)._2 mustEqual Some("a")
      cmd[JoinCommand]("JOIN #foo,&bar a,b").targets(1)._2 mustEqual Some("b")

      cmd[JoinCommand]("JOIN #foo a,b,c,d,e").targets.size mustEqual 1
      cmd[JoinCommand]("JOIN #foo a,b,c,d,e").targets(0)._2 mustEqual Some("a")
    }

    "rejectInvalidJoinCmd" in {
      cmd[InvalidCommand]("JOIN").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("JOIN blah").rspCode mustEqual ResponseCode.ERR_NOSUCHCHANNEL
    }

    "handlePartCmd" in {
      cmd[PartCommand]("PART #foo").channels.size mustEqual 1
      cmd[PartCommand]("PART #foo").channels(0).name mustEqual "#foo"

      cmd[PartCommand]("PART #foo,&bar").channels.size mustEqual 2
      cmd[PartCommand]("PART #foo,&bar").channels(0).name mustEqual "#foo"
      cmd[PartCommand]("PART #foo,&bar").channels(1).name mustEqual "&bar"
    }

    "rejectInvalidPartCmd" in {
      cmd[InvalidCommand]("PART").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("PART blah").rspCode mustEqual ResponseCode.ERR_NOSUCHCHANNEL
    }

    "handleTopicCmd" in {
      cmd[TopicCommand]("TOPIC #foo").channel.name mustEqual "#foo"
      cmd[TopicCommand]("TOPIC #foo").topic mustEqual None
      cmd[TopicCommand]("TOPIC #foo :").topic mustEqual Some("")
      cmd[TopicCommand]("TOPIC #foo :blah blah blah").topic mustEqual Some("blah blah blah")
      cmd[TopicCommand]("TOPIC #foo blah ignore").topic mustEqual Some("blah")
    }

    "rejectInvalidTopicCmd" in {
      cmd[InvalidCommand]("TOPIC").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("TOPIC blah").rspCode mustEqual ResponseCode.ERR_NOSUCHCHANNEL
    }

    "handleNamesCmd" in {
      cmd[NamesCommand]("NAMES").channels mustEqual List()
      cmd[NamesCommand]("NAMES #foo").channels.map(_.name) mustEqual List("#foo")
      cmd[NamesCommand]("NAMES #foo,&bar").channels.map(_.name) mustEqual List("#foo", "&bar")
    }

    "ignoreInvalidChannelsInNamesCmd" in {
      cmd[NamesCommand]("NAMES foo").channels mustEqual(List())
      cmd[NamesCommand]("NAMES foo,#bar").channels.map(_.name) mustEqual(List("#bar"))
    }

    "handleListCmd" in {
      cmd[ListCommand]("LIST").channels mustEqual List()
      cmd[ListCommand]("LIST #foo").channels.map(_.name) mustEqual List("#foo")
      cmd[ListCommand]("LIST #foo,&bar").channels.map(_.name) mustEqual List("#foo", "&bar")
    }

    "handleKickCmd" in {
      cmd[KickCommand]("KICK #blah someone").channel.name mustEqual "#blah"
      cmd[KickCommand]("KICK #blah someone").user mustEqual "someone"
      cmd[KickCommand]("KICK #blah someone").message mustEqual None
      cmd[KickCommand]("KICK #blah someone :bye bye").message mustEqual Some("bye bye")
    }

    "rejectInvalidKickCmd" in {
      cmd[InvalidCommand]("KICK").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("KICK #blah").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("KICK blah someone").rspCode mustEqual ResponseCode.ERR_NOSUCHCHANNEL
    }

    "handlePrivMsgCmd" in {
      cmd[PrivMsgCommand]("PRIVMSG foo :hi there").message mustEqual "hi there"
      cmd[PrivMsgCommand]("PRIVMSG foo hi there").message mustEqual "hi"

      cmd[PrivMsgCommand]("PRIVMSG foo :hi there").nicknames mustEqual List("foo")
      cmd[PrivMsgCommand]("PRIVMSG foo :hi there").channels mustEqual List()

      cmd[PrivMsgCommand]("PRIVMSG #foo :hi there").nicknames mustEqual List()
      cmd[PrivMsgCommand]("PRIVMSG #foo :hi there").channels mustEqual List(ChannelName("#foo"))

      cmd[PrivMsgCommand]("PRIVMSG #foo,bar :hi there").nicknames mustEqual List("bar")
      cmd[PrivMsgCommand]("PRIVMSG #foo,bar :hi there").channels mustEqual List(ChannelName("#foo"))
    }

    "rejectInvalidPrvMsgCmd" in {
      cmd[InvalidCommand]("PRIVMSG").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("PRIVMSG 1").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleNoticeCmd" in {
      cmd[NoticeCommand]("NOTICE me blah").nickname mustEqual "me"
      cmd[NoticeCommand]("NOTICE me blah").message mustEqual "blah"
    }

    "rejectInvalidNoticeCmd" in {
      cmd[InvalidCommand]("NOTICE").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("NOTICE 1").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleKillCmd" in {
      cmd[KillCommand]("KILL me blah").nickname mustEqual "me"
      cmd[KillCommand]("KILL me blah").comment mustEqual "blah"
    }

    "rejectInvalidKillCmd" in {
      cmd[InvalidCommand]("KILL").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
      cmd[InvalidCommand]("KILL 1").rspCode mustEqual ResponseCode.ERR_NEEDMOREPARAMS
    }

    "handleAwayCmd" in {
      cmd[AwayCommand]("AWAY").message mustEqual None
      cmd[AwayCommand]("AWAY bye").message mustEqual Some("bye")
    }
  }

  def cmd[C](s: String): C = {
    Command.create(CommandParser.parse(s).right.get) match {
      case c:C => c
      case _ => throw new IllegalStateException("Unexpected result type")
    }
  }
}

