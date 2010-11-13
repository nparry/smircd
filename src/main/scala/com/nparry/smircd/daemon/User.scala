package com.nparry.smircd.daemon

import scala.actors._

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

object User {

  case class Pending(
    override val connection: Actor,
    serverId: String,
    password: Option[String] = None,
    override val maybeNickname: Option[NickName] = None
  ) extends User(connection, serverId) {

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
    override val connection: Actor,
    serverId: String,
    nickname: NickName,
    info: UserCommand,
    channels: Map[ChannelName, Channel] = Map(),
    awayMessage: Option[String] = None
  ) extends User(connection, serverId) {

    def maybeNickname = Some(nickname)
    def updatePassword(pw: String) = Left(ResponseCode.ERR_ALREADYREGISTRED)

    def updateNickname(cmd: NickCommand) = {
      val newUser = copy(nickname=cmd.nickname)
      for (chan <- channels.values) {
        chan.memberChangedNickName(newUser, this.nickname, cmd)
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

    def messageToChannel(channel: Channel, cmd: PrivMsgCommand) = {
      channels.get(channel.name) match {
        case None => returnError(ResponseCode.ERR_CANNOTSENDTOCHAN, channel.name.name)
        case Some(c) => c.messageFrom(this, cmd)
      }
    }

    def away(cmd: AwayCommand) = {
      def newUser = copy(awayMessage = cmd.message)
      newUser.awayMessage match {
        case None => newUser.reply(ResponseCode.RPL_UNAWAY)
        case Some(_) => newUser.reply(ResponseCode.RPL_NOWAWAY)
      }
    }

    def messageFrom(sender: User, cmd: PrivMsgCommand) = {
      for (msg <- awayMessage) {
        sender.send(SupportedCommand(
          maybeNickname.map(_.name),
          ResponseCode.RPL_AWAY.toString,
          Some(msg)))
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

abstract sealed class User(val connection: Actor, serverId: String) {

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

  def returnError(rspCode: ResponseCode.Value, killConnection: Boolean = false): User = returnError(rspCode, None, killConnection)
  def returnError(rspCode: ResponseCode.Value, message: String): User = returnError(rspCode, Some(message), false)
  def returnError(rspCode: ResponseCode.Value, message: Option[String], killConnection: Boolean): User = {
    reply(rspCode, message)
    if (killConnection) {
      breakConnection()
    }

    this
  }

  def reply(rspCode: ResponseCode.Value, message: Iterable[String] = List()): User = {
    send(SupportedCommand(serverId, rspCode.toString, message))
  }

  def send(cmd: SupportedCommand): User = {
    this
  }

  def breakConnection(): User = {
    this
  }

}

