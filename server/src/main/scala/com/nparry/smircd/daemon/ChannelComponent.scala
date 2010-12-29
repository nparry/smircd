package com.nparry.smircd.daemon

import scala.collection.mutable.{Set => MSet}

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

trait ChannelComponent {

  this: ConnectionComponent with UserComponent =>

  object Channel {
    def sendEndOfNamesToUser(user: User): User = {
      user.reply(ResponseCode.RPL_ENDOFNAMES, Some("*"))
    }
  }
  
  class Channel(
    val name: ChannelName,
    memberLookup: (NickName.Normalized) => User.Registered,
    killMe: (ChannelName) => Unit) {
  
    val logger = Logger(this.getClass())
  
    val members: MSet[NickName.Normalized] = MSet()
    var topic: Option[String] = None
  
    def newMember(user: User.Registered, cmd: JoinCommand) = {
      members.add(user.nickname.normalized)    
      logger.debug(user + " joined " + this) 
  
      reBroadcastFrom(user, cmd.copyWithNewParams(List(name.name)))
      sendTopicTo(user)
      sendMememberNamesTo(user, true)
    }
  
    def partingMember(user: User.Registered, cmd: PartCommand) = {
      val u = reBroadcastFrom(user, cmd.copyWithNewParams(List(name.name)))
      dropMember(user)
      u
    }
  
    def dropMember(user: User.Registered): Unit = dropMember(user.nickname)
    def dropMember(nickname: NickName): Unit = {
      members.remove(nickname.normalized)    
      logger.debug(nickname + " left " + this) 
      if (members.isEmpty) killMe(name)
    }
  
    def memberChangedNickName(user: User.Registered, newNick: NickName, cmd: NickCommand) = {
      reBroadcastFrom(user, cmd)
      members.remove(user.nickname.normalized)
      members.add(newNick.normalized)
    }
  
    def memberChangedTopic(user: User.Registered, cmd: TopicCommand): User.Registered = {
      topic = cmd.topic
      reBroadcastFrom(user, cmd)
    }
  
    def memberKickingUser(user: User.Registered, cmd: KickCommand): User = {
      if (members.contains(cmd.user.normalized)) {
        logger.debug(cmd.user + " kicked from " + this + " by " + user)
        reBroadcastFrom(user, cmd)
        dropMember(cmd.user)
        memberLookup(cmd.user.normalized).kickedFrom(this)
      }
      else {
        user.returnError(ResponseCode.ERR_USERNOTINCHANNEL, cmd.user.name)
      }
    }
  
    def sendTopicTo(user: User): User = {
      topic match {
        case None => user.reply(ResponseCode.RPL_NOTOPIC, List(name.name))
        case Some(t) => user.reply(ResponseCode.RPL_TOPIC, List(name.name, t))
      }
    }
  
    def sendMememberNamesTo(user: User, includeEON: Boolean): User = {
      user.reply(ResponseCode.RPL_NAMREPLY, List(
        "=",
        name.name,
        getChannelMembers.map(_.nickname.name).mkString(" ")))
  
      if (includeEON) {
        Channel.sendEndOfNamesToUser(user)
      }
  
      user
    }
  
    def sendInfoTo(user: User): User = {
      user.reply(ResponseCode.RPL_LIST, List(name.name, members.size.toString, topic.getOrElse("")))
    }
  
    def messageFrom(user: User.Registered, cmd: PrivMsgCommand) = {
      reBroadcastFrom(user, cmd, false)
    }
  
    def reBroadcastFrom(user: User.Registered, cmd: SupportedCommand, includeSender: Boolean = true, updatePrefix: Boolean = true) = {
      logger.debug(this + " broadcasting message " + cmd + " from " + user)
      val c = if (updatePrefix) cmd.copyWithNewPrefix(user.maybeNickname.map(_.name))
              else cmd
      for (u <- getChannelMembers) {
        if (u != user || includeSender) u.send(c)
      }
  
      user
    }
  
    private def getChannelMembers: Iterable[User.Registered] = {
      members.map(memberLookup(_))
    }
  
    override def toString() = {
      "Channel(" + name.name + ", members=" + members.size + ")"
    }
  }

}
