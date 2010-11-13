package com.nparry.smircd.protocol

object Command {

  object SupportedCommand {
    def apply(prefix: String, command: String, params: Iterable[String]): SupportedCommand =
      apply(Some(prefix), command, params)
    

    def apply(prefix: Option[String], command: String, params: Iterable[String]): SupportedCommand =
      new SupportedCommand(ParsedCommand(prefix, command, params.toList))
  }

  class SupportedCommand(cmd: ParsedCommand) extends Command(cmd.raw) {

    def copyWithNewPrefix(prefix: Option[String]): SupportedCommand = {
      new SupportedCommand(cmd.copy(prefix=prefix))
    }

    def copyWithNewParams(newParams: List[String]): SupportedCommand = {
      new SupportedCommand(cmd.copy(params=newParams))
    }

    protected def p(i: Int) = cmd.params(i)
    protected def maybeP(i: Int) = if (i < cmd.params.size) Some(p(i)) else None

    protected def chanP(i: Int) = ChannelName.single(p(i), handleBadChannel).right.get
    protected def chanListP(i: Int) = ChannelName.list(p(i), handleBadChannel).right.get
    protected def maybeChanListP(i: Int) = maybeP(i).map(ChannelName.list(_, handleBadChannel).right.get)

    protected def nickP(i: Int) = NickName.single(p(i), handleBadNick).right.get

    protected def minimumParams(i: Int, rspCode: ResponseCode.Value = ResponseCode.ERR_NEEDMOREPARAMS) = {
      if (cmd.params.size < i)
        throw new InvalidCommandException(
          "Need " + i + " params",
          rspCode)
    }
  }

  case class UnsupportedCommand(cmd: ParsedCommand) extends Command(cmd.raw) {
    def rspCode = ResponseCode.ERR_UNKNOWNCOMMAND
  }

  case class InvalidCommand(reason: InvalidCommandException, rawStr: String) extends Command(rawStr) {
    def rspCode = reason.rspCode
  }

  def create(c: ParsedCommand): Command = {
    try
      supportedCommandRegistry.getOrElse(c.command.toUpperCase(), { unsupported: ParsedCommand =>
        UnsupportedCommand(unsupported)
      })(c)
    catch {
      case e: InvalidCommandException => InvalidCommand(e, c.raw)
    }
  }

  val supportedCommandRegistry = Map[String, Function1[ParsedCommand, Command]](
    "PASS" -> PassCommand.apply _,
    "NICK" -> NickCommand.apply _,
    "USER" -> UserCommand.apply _,
    "OPER" -> OperCommand.apply _,
    "QUIT" -> QuitCommand.apply _,
    "JOIN" -> JoinCommand.apply _,
    "PART" -> PartCommand.apply _,
    "TOPIC" -> TopicCommand.apply _,
    "NAMES" -> NamesCommand.apply _,
    "LIST" -> ListCommand.apply _,
    "KICK" -> KickCommand.apply _,
    "PRIVMSG" -> PrivMsgCommand.apply _,
    "NOTICE" -> NoticeCommand.apply _,
    "KILL" -> KillCommand.apply _,
    "PING" -> PingCommand.apply _,
    "PONG" -> PongCommand.apply _,
    "AWAY" -> AwayCommand.apply _

    // TODO
    // - MODE
    // - INVITE
    // - VERSION
    // - STATS
    // - TIME
    // - ADMIN
    // - INFO
    // - WHO
    // - WHOIS
    // - WHOWAS
    // - ERROR
  )

  case class PassCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(1)
    def password = p(0)
  }

  case class NickCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(1, ResponseCode.ERR_NONICKNAMEGIVEN)
    val nick = nickP(0)

    def nickname = nick
  }

  case class UserCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(4)
    def username = p(0)
    def realname = p(3)
  }

  case class OperCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(2)
    def user = p(0)
    def password = p(1)
  }

  case class QuitCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    def message = maybeP(0)
  }

  case class JoinCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(1)
    val chans = chanListP(0)
    val keys = maybeP(1).map(ChannelName.keyList _).getOrElse(List())

    def targets: List[(ChannelName,Option[String])]
      = chans.zipAll(keys.map(Some.apply _), null, None).filter(_._1 != null)
  }

  case class PartCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(1)
    val chans = chanListP(0)

    def channels = chans
  }

  case class TopicCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(1)
    val chan = chanP(0)

    def channel = chan
    def topic = maybeP(1)
  }

  case class NamesCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    def channels = maybeP(0)
      .map(s => ChannelName.listOfValid(s, { x => x }))
      .getOrElse(List())
      .flatMap(_.right.toOption)
  }

  case class ListCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    val chans = maybeChanListP(0).getOrElse(List())

    def channels = chans
  }

  case class KickCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(2)
    val chan = chanP(0)
    val usr = nickP(1)

    def channel = chan
    def user = usr
    def message = maybeP(2)
  }

  case class PrivMsgCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(2)
    val receivers = ChannelName.listOfValid(
      p(0),
      { x => NickName.single(x, handleBadNick).right.get })

    def nicknames = receivers.flatMap(_.left.toOption)
    def channels = receivers.flatMap(_.right.toOption)
    def message = p(1)
  }

  case class NoticeCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(2)
    val nick = nickP(0)

    def nickname = nick
    def message = p(1)
  }

  case class KillCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    minimumParams(2)
    val nick = nickP(0)

    def nickname = nick
    def comment = p(1)
  }

  case class PingCommand(cmd: ParsedCommand) extends SupportedCommand(cmd)
  case class PongCommand(cmd: ParsedCommand) extends SupportedCommand(cmd)
    
  case class AwayCommand(cmd: ParsedCommand) extends SupportedCommand(cmd) {
    def message = maybeP(0)
  }

  object ResponseCode extends Enumeration {
    type Response = Value

    val RPL_TRACELINK = Value("200") 
    val RPL_TRACECONNECTING = Value("201") 
    val RPL_TRACEHANDSHAKE = Value("202") 
    val RPL_TRACEUNKNOWN = Value("203") 
    val RPL_TRACEOPERATOR = Value("204") 
    val RPL_TRACEUSER = Value("205") 
    val RPL_TRACESERVER = Value("206") 
    val RPL_TRACENEWTYPE = Value("208") 
    val RPL_STATSQLINE = Value("209") 
    val RPL_STATSLINKINFO = Value("211") 
    val RPL_STATSCOMMANDS = Value("212") 
    val RPL_STATSCLINE = Value("213") 
    val RPL_STATSNLINE = Value("214") 
    val RPL_STATSILINE = Value("215") 
    val RPL_STATSKLINE = Value("216") 
    val RPL_TRACECLASS = Value("217")
    val RPL_STATSYLINE = Value("218") 
    val RPL_ENDOFSTATS = Value("219") 
    val RPL_UMODEIS = Value("221") 
    val RPL_ENDOFSERVICES = Value("231") 
    val RPL_SERVICEINFO = Value("232")
    val RPL_SERVLIST = Value("233") 
    val RPL_SERVICE = Value("234")
    val RPL_SERVLISTEND = Value("235") 
    val RPL_STATSLLINE = Value("241") 
    val RPL_STATSUPTIME = Value("242") 
    val RPL_STATSOLINE = Value("243") 
    val RPL_STATSHLINE = Value("244") 
    val RPL_LUSERCLIENT = Value("251") 
    val RPL_LUSEROP = Value("252") 
    val RPL_LUSERUNKNOWN = Value("253") 
    val RPL_LUSERCHANNELS = Value("254") 
    val RPL_LUSERME = Value("255") 
    val RPL_ADMINME = Value("256") 
    val RPL_ADMINLOC1 = Value("257") 
    val RPL_ADMINLOC2 = Value("258") 
    val RPL_ADMINEMAIL = Value("259") 
    val RPL_TRACELOG = Value("261") 
    val RPL_NONE = Value("300") 
    val RPL_AWAY = Value("301") 
    val RPL_USERHOST = Value("302") 
    val RPL_ISON = Value("303") 
    val RPL_UNAWAY = Value("305") 
    val RPL_NOWAWAY = Value("306") 
    val RPL_WHOISUSER = Value("311") 
    val RPL_WHOISSERVER = Value("312") 
    val RPL_WHOISOPERATOR = Value("313") 
    val RPL_WHOWASUSER = Value("314") 
    val RPL_ENDOFWHO = Value("315") 
    val RPL_KILLDONE = Value("316") 
    val RPL_WHOISIDLE = Value("317") 
    val RPL_ENDOFWHOIS = Value("318") 
    val RPL_WHOISCHANNELS = Value("319") 
    val RPL_LISTSTART = Value("321") 
    val RPL_LIST = Value("322") 
    val RPL_LISTEND = Value("323") 
    val RPL_CHANNELMODEIS = Value("324") 
    val RPL_NOTOPIC = Value("331") 
    val RPL_TOPIC = Value("332") 
    val RPL_INVITING = Value("341") 
    val RPL_SUMMONING = Value("342") 
    val RPL_VERSION = Value("351") 
    val RPL_WHOREPLY = Value("352") 
    val RPL_NAMREPLY = Value("353") 
    val RPL_WHOISCHANOP = Value("361")
    val RPL_CLOSEEND = Value("362") 
    val RPL_CLOSING = Value("363")
    val RPL_LINKS = Value("364") 
    val RPL_ENDOFLINKS = Value("365") 
    val RPL_ENDOFNAMES = Value("366") 
    val RPL_BANLIST = Value("367") 
    val RPL_ENDOFBANLIST = Value("368") 
    val RPL_ENDOFWHOWAS = Value("369") 
    val RPL_INFO = Value("371") 
    val RPL_MOTD = Value("372") 
    val RPL_MYPORTIS = Value("373") 
    val RPL_ENDOFINFO = Value("374") 
    val RPL_MOTDSTART = Value("375") 
    val RPL_ENDOFMOTD = Value("376") 
    val RPL_YOUREOPER = Value("381") 
    val RPL_REHASHING = Value("382") 
    val RPL_INFOSTART = Value("384")
    val RPL_TIME = Value("391") 
    val RPL_USERSSTART = Value("392") 
    val RPL_USERS = Value("393") 
    val RPL_ENDOFUSERS = Value("394") 
    val RPL_NOUSERS = Value("395") 
    val ERR_NOSUCHNICK = Value("401") 
    val ERR_NOSUCHSERVER = Value("402") 
    val ERR_NOSUCHCHANNEL = Value("403") 
    val ERR_CANNOTSENDTOCHAN = Value("404") 
    val ERR_TOOMANYCHANNELS = Value("405") 
    val ERR_WASNOSUCHNICK = Value("406") 
    val ERR_TOOMANYTARGETS = Value("407") 
    val ERR_NOORIGIN = Value("409") 
    val ERR_NORECIPIENT = Value("411") 
    val ERR_NOTEXTTOSEND = Value("412") 
    val ERR_NOTOPLEVEL = Value("413") 
    val ERR_WILDTOPLEVEL = Value("414") 
    val ERR_UNKNOWNCOMMAND = Value("421") 
    val ERR_NOMOTD = Value("422") 
    val ERR_NOADMININFO = Value("423") 
    val ERR_FILEERROR = Value("424") 
    val ERR_NONICKNAMEGIVEN = Value("431") 
    val ERR_ERRONEUSNICKNAME = Value("432") 
    val ERR_NICKNAMEINUSE = Value("433") 
    val ERR_NICKCOLLISION = Value("436") 
    val ERR_USERNOTINCHANNEL = Value("441") 
    val ERR_NOTONCHANNEL = Value("442") 
    val ERR_USERONCHANNEL = Value("443") 
    val ERR_NOLOGIN = Value("444") 
    val ERR_SUMMONDISABLED = Value("445") 
    val ERR_USERSDISABLED = Value("446") 
    val ERR_NOTREGISTERED = Value("451") 
    val ERR_NEEDMOREPARAMS = Value("461") 
    val ERR_ALREADYREGISTRED = Value("462") 
    val ERR_NOPERMFORHOST = Value("463") 
    val ERR_PASSWDMISMATCH = Value("464") 
    val ERR_YOUREBANNEDCREEP = Value("465") 
    val ERR_BADCHANMASK = Value("466") 
    val ERR_YOUWILLBEBANNED = Value("476")
    val ERR_KEYSET = Value("467") 
    val ERR_CHANNELISFULL = Value("471") 
    val ERR_UNKNOWNMODE = Value("472") 
    val ERR_INVITEONLYCHAN = Value("473") 
    val ERR_BANNEDFROMCHAN = Value("474") 
    val ERR_BADCHANNELKEY = Value("475") 
    val ERR_NOPRIVILEGES = Value("481") 
    val ERR_CHANOPRIVSNEEDED = Value("482") 
    val ERR_CANTKILLSERVER = Value("483") 
    val ERR_NOOPERHOST = Value("491") 
    val ERR_NOSERVICEHOST = Value("492") 
    val ERR_UMODEUNKNOWNFLAG = Value("501") 
    val ERR_USERSDONTMATCH = Value("502") 
  }

  class InvalidCommandException(message: String, val rspCode: ResponseCode.Value) extends Exception(message)

  private val handleBadChannel = { e: IllegalArgumentException =>
    throw new InvalidCommandException("Bad channel name " + e.getMessage(), ResponseCode.ERR_NOSUCHCHANNEL)
  }

  private val handleBadNick = { e: IllegalArgumentException =>
    throw new InvalidCommandException("Bad nickname " + e.getMessage(), ResponseCode.ERR_ERRONEUSNICKNAME)
  }
}

class Command(val raw: String)

