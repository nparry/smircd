package com.nparry.smircd.daemon

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

trait UserComponent {

  this: ConnectionComponent with ChannelComponent =>

  object User {
  
    case class Pending(
      override val connection: Connection,
      serverId: String,
      password: Option[String] = None,
      override val maybeNickname: Option[NickName] = None
    ) extends User(connection, serverId, "PendingUser") {
  
      def updatePassword(pw: String) =
        if (isRegistered) Left(ResponseCode.ERR_ALREADYREGISTRED)
        else Right(copy(password=Some(pw)))
  
      def updateNickname(cmd: NickCommand) = copy(maybeNickname=Some(cmd.nickname))
  
      // TODO - if we don't have a nick yet?
      def asRegistered(info: UserCommand) = Registered(connection, serverId, maybeNickname.get, info)
  
      def joinChannels(cmd: JoinCommand, channels: List[Channel]) = notRegistered
      def partChannels(cmd: PartCommand) = notRegistered
      def topic(cmd: TopicCommand) = notRegistered
      def kickUserFromChannel(channel: Channel, cmd: KickCommand) = notRegistered
      def messageToChannel(channel: Channel, cmd: PrivMsgCommand) = notRegistered
      def away(cmd: AwayCommand) = notRegistered
      def reBroadcast(cmd: SupportedCommand) = notRegistered
  
      def notRegistered(): User = returnError(ResponseCode.ERR_NOTREGISTERED)
  
    }
  
    case class Registered(
      override val connection: Connection,
      serverId: String,
      nickname: NickName,
      info: UserCommand,
      channels: Map[ChannelName, Channel] = Map[ChannelName, Channel](),
      awayMessage: Option[String] = None
    ) extends User(connection, serverId, "RegisteredUser") {
  
      def maybeNickname = Some(nickname)
      def updatePassword(pw: String) = Left(ResponseCode.ERR_ALREADYREGISTRED)
  
      def updateNickname(cmd: NickCommand) = {
        val newUser = copy(nickname=cmd.nickname)
        for (chan <- channels.values) {
          chan.memberChangedNickName(this, newUser.nickname, cmd)
        }
  
        newUser
      }
  
      def joinChannels(cmd: JoinCommand, newChannels: List[Channel]) = {
        val validChans = Map(newChannels.map(chan => chan.name -> chan): _*) -- channels.keySet
        val newUser = copy(channels = channels ++ validChans)
  
        for (chan <- validChans.values) {
          chan.newMember(newUser, cmd)
        }
  
        newUser
      }
  
      def partChannels(cmd: PartCommand) = {
        val (valid, invalid) = cmd.chans.partition { channels.contains(_) }
  
        for (chan <- invalid) {
          returnError(ResponseCode.ERR_NOTONCHANNEL, chan.name)
        }
  
        for (chan <- valid.flatMap(channels.get(_))) {
          chan.partingMember(this, cmd)
        }
  
        copy(channels = channels -- valid)
      }
  
      def topic(cmd: TopicCommand) = {
        channels.get(cmd.chan) match {
          case None => returnError(ResponseCode.ERR_NOTONCHANNEL, cmd.chan.name)
          case Some(c) => cmd.topic match {
            case None => c.sendTopicTo(this)
            case Some(s) => c.memberChangedTopic(this, cmd)
          }
        }
      }
  
      def kickUserFromChannel(channel: Channel, cmd: KickCommand) = {
        channels.get(channel.name) match {
          case None => returnError(ResponseCode.ERR_NOTONCHANNEL, channel.name.name)
          case Some(c) => c.memberKickingUser(this, cmd)
        }
      }
  
      def kickedFrom(channel: Channel) = {
        copy(channels = channels -- Some(channel.name))
      }
  
      def messageToChannel(channel: Channel, cmd: PrivMsgCommand) = {
        channels.get(channel.name) match {
          case None => returnError(ResponseCode.ERR_CANNOTSENDTOCHAN, channel.name.name)
          case Some(c) => c.messageFrom(this, cmd)
        }
      }
  
      def away(cmd: AwayCommand) = {
        val msg = cmd.message match {
          case Some("") => None
          case Some(m) => Some(m) 
          case None => None
        }
  
        val newUser = copy(awayMessage = msg)
        newUser.awayMessage match {
          case None => newUser.reply(ResponseCode.RPL_UNAWAY)
          case Some(_) => newUser.reply(ResponseCode.RPL_NOWAWAY)
        }
      }
  
      def messageFrom(sender: User, cmd: PrivMsgCommand) = {
        for (msg <- awayMessage) {
          sender.reply(
            ResponseCode.RPL_AWAY,
            maybeNickname.map(_.name) ++ Some(msg))
        }
  
        send(cmd.copyWithNewPrefix(sender.maybeNickname.map(_.name)))
      }
  
      def noticeFrom(sender: User, cmd: NoticeCommand) = {
        send(cmd.copyWithNewPrefix(sender.maybeNickname.map(_.name)))
      }
  
      def reBroadcast(cmd: SupportedCommand) = {
        for (chan <- channels.values) {
          chan.reBroadcastFrom(this, cmd)
        }
  
        this
      }
  
      override def breakConnection(): User = {
        for (chan <- channels.values) {
          chan.dropMember(this)
        }
  
        super.breakConnection()
      }
  
    }
  
  }
  
  abstract sealed class User(val connection: Connection, serverId: String, debugName: String) {
    val logger = Logger(this.getClass())
  
    def maybeNickname: Option[NickName];
    def isRegistered: Boolean = maybeNickname.isDefined
  
    def updatePassword(pw: String): Either[ResponseCode.Value, User];
    def updateNickname(cmd: NickCommand): User;
  
    def joinChannels(cmd: JoinCommand, channels: List[Channel]): User;
    def partChannels(cmd: PartCommand): User;
    def topic(cmd: TopicCommand): User;
    def kickUserFromChannel(channel: Channel, cmd: KickCommand): User;
  
    def messageToChannel(channel: Channel, cmd: PrivMsgCommand): User;
    def away(cmd: AwayCommand): User;
  
    def reBroadcast(cmd: SupportedCommand): User;
  
    def returnError(rspCode: ResponseCode.Value): User = returnError(rspCode, None)
    def returnError(rspCode: ResponseCode.Value, message: String): User = returnError(rspCode, Some(message))
    def returnError(rspCode: ResponseCode.Value, message: Option[String]): User = {
      logger.debug("Sending error " + rspCode + " to " + this)
      reply(rspCode, message)
      this
    }
  
    def reply(rspCode: ResponseCode.Value, message: Iterable[String] = List()): User = {
      logger.debug("Sending reply " + rspCode + " to " + this)
      val params = maybeNickname.map(_.name) ++ message
      send(SupportedCommand(serverId, rspCode.toString, params))
    }
  
    def send(cmd: SupportedCommand): User = {
      logger.trace("Sending " + cmd + " to " + this)
      sendMsg(connection, IrcServer.OutboundMessage(cmd))
      this
    }
  
    def breakConnection(): User = {
      logger.trace("Breaking connection to " + this)
      sendMsg(connection, IrcServer.Shutdown())
      this
    }
  
    override def toString() = {
      debugName + "(" + maybeNickname.map(_.name).getOrElse("<no nickname>") + ")"
    }
  }

}
