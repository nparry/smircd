package com.nparry.smircd.daemon

import java.util.Date

import scala.collection.mutable.{Map => MMap}

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

object IrcServer {
  case class OutboundMessage(cmd: SupportedCommand)
  case class Shutdown()
  case class PingEm()

  val connectionLostMsg = "Connection lost"
}

trait IrcServerComponent {

  this: ConnectionComponent with UserComponent with ChannelComponent =>

  def pingThresholdMinutes: Int
  def dropThresholdMinutes: Int

  def makeServer(serverId: String, quit: () => Unit): IrcServer = new IrcServer(serverId, quit)

  class IrcServer(serverId: String, quit: () => Unit) {
    val logger = Logger(this.getClass())
  
    val pendingConnections = MMap[Connection, User.Pending]()
    val activeConnections = MMap[Connection, User.Registered]()
  
    val lastSeen = MMap[Connection, Date]()

    def connectionStats = (pendingConnections.size, activeConnections.size)
  
    def updateConnection[U <: User](u: U): U = {
      logger.trace("Updating connection for " + u)
      u match {
        case p:  User.Pending => pendingConnections.put(p.connection, p)
        case r: User.Registered => activeConnections.put(r.connection, r)
      }
  
      u
    }
  
    def deleteConnection(u: User) = {
      logger.trace("Deleting connection for " + u)
      u match {
        case p: User.Pending => pendingConnections.remove(p.connection)
        case r: User.Registered => activeConnections.remove(r.connection)
      }
    }
  
    def getUser[R](c: Connection, updateLastSeen: Boolean = true)(useUser: (User) => R): Option[R] = {
      if (updateLastSeen) {
        lastSeen.put(c, new Date())
      }

      activeConnections.get(c).orElse(pendingConnections.get(c)).map { u =>
        logger.trace("Using user " + u)
        useUser(u) 
      }
    }
  
    def deleteUser(u: User) = {
      logger.trace("Deleting user " + u)
      lastSeen.remove(u.connection)
      deleteConnection(u)
      updateNick(u, None)
      u.breakConnection()
    }
  
    def getActiveUser(n: NickName.Normalized): Option[User.Registered] = {
      userInfo.get(n).flatMap(activeConnections.get _)
    }
  
    val userInfo = MMap[NickName.Normalized, Connection]()
  
    def updateNick(user: User, newNick: Option[NickName]) = {
      for (oldNick <- user.maybeNickname) {
        logger.trace("Removing nickname mapping for " + oldNick)
        userInfo.remove(oldNick.normalized)
      }
  
      for (nn <- newNick) {
        logger.trace("Adding nickname mapping for " + newNick)
        userInfo.put(nn.normalized, user.connection)
      }
    }
  
    def isNickCollision(user: User, nick: NickName) = {
      userInfo.get(nick.normalized).map(_ != user.connection).getOrElse(false)
    }

    def currentNicks = userInfo.keySet
  
    val channels = MMap[ChannelName, Channel]()
  
    def getChannel(name: ChannelName): Channel = {
      logger.trace("Looking for channel " + name)
      channels.get(name).getOrElse {
        logger.trace("Creating new channel for " + name)
        val c = makeChannel(
          serverId,
          name,
          { nick => getActiveUser(nick).get },
          { deadName =>
            logger.trace("Deleting channel " + deadName)
            channels.remove(deadName)
          })
        channels.put(name, c)
        c
      }
    }
  
    def getDesiredChannels(names: List[ChannelName]): Iterable[Channel] = {
     val values =
       if (names.isEmpty)
         channels.values
       else
         channels.filterKeys(names.contains(_)).values
     values.toSeq.sorted
    }
    
    def processIncomingMsg(msg: Any) {
      msg match {
        case IrcServer.Shutdown() => {
          logger.info("Shutting down")
          for (u <- pendingConnections.values) {
            deleteUser(u)
          }
          for (u <- activeConnections.values) {
            deleteUser(u)
          }
  
          quit()
        }

        case IrcServer.PingEm() => {
          def previousTime(minutes: Int): Date = {
            import java.util.Calendar
            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, -1 * minutes)
            cal.getTime()
          }

          logger.debug("Pinging connections")
          val pingThreshold = previousTime(pingThresholdMinutes)
          val dropThreshold = previousTime(dropThresholdMinutes)
          val ignore = new Date()
          val grouped = lastSeen.groupBy({ case (connection, lastActivity) =>
            if (lastActivity.before(dropThreshold))
              dropThreshold
            else if (lastActivity.before(pingThreshold))
              pingThreshold
            else
              ignore
          })

          grouped.get(dropThreshold).foreach(_.keySet.foreach { getUser(_, false) { user =>
            // TODO: Duplicate code in the disconnect case below
            logger.debug("Dropping connection to " + user)
            if (user.isRegistered) {
              user.reBroadcast(SupportedCommand(
                user.maybeNickname.map(_.name),
                "QUIT",
                Some(IrcServer.connectionLostMsg)))
            }

            deleteUser(user)
          }})

          grouped.get(pingThreshold).foreach(_.keySet.foreach { getUser(_, false) { user =>
            if (user.isRegistered) {
              logger.debug("Pinging " + user)
              user.send(SupportedCommand(serverId, "PING", List()))
            }
            else {
              logger.debug("Skipping ping for " + user)
            }
          }})
        }
  
        case (c: Connection, joining: Boolean) => {
          if (joining) {
            logger.debug("New user joining")
            updateConnection(User.Pending(c, serverId))
            lastSeen.put(c, new Date())
          }
          else {
            getUser(c) { user =>
              logger.debug("Connection broken to " + user)
              deleteUser(user.reBroadcast(SupportedCommand(
                user.maybeNickname.map(_.name),
                "QUIT",
                Some(IrcServer.connectionLostMsg))))
            }
          }
        }
  
        case (c: Connection, p: PassCommand) => getUser(c) { user =>
          logger.debug("Password command from " + user)
          user.updatePassword(p.password) match {
            case Left(rspCode) => user.returnError(rspCode)
            case Right(updatedUser) => updateConnection(updatedUser)
          }
        }
  
        case (c: Connection, n: NickCommand) => getUser(c) { user =>
          logger.debug("Nickname command from " + user)
          if (isNickCollision(user, n.nickname)) {
            logger.debug("Nickname collision for " + user + " using " + n.nickname)
            user match {
              case p: User.Pending => {
                user.returnError(ResponseCode.ERR_NICKNAMEINUSE)
                deleteUser(p)
              }
              case r: User.Registered => {
                user.returnError(ResponseCode.ERR_NICKCOLLISION)
              }
            }
          }
          else {
            val result = updateConnection(user.updateNickname(n))
            updateNick(user, Some(n.nickname)) 
            result
          }
        }
  
        case (c: Connection, u: UserCommand) => getUser(c) { user =>
          logger.debug("User command from " + user)
          user match {
            case r: User.Registered => r.returnError(ResponseCode.ERR_ALREADYREGISTRED)
            case p: User.Pending => {
              deleteConnection(p)
              val r = p.asRegistered(u)
              updateConnection(r)
              r.reply(ResponseCode.RPL_MOTDSTART, Some("- MOTD -"))
              r.reply(ResponseCode.RPL_MOTD, Some("- Hi -"))
              r.reply(ResponseCode.RPL_ENDOFMOTD, Some(":End of /MOTD command.'"))
            }
          }
        }
  
        // TODO - OPER command
        
        case (c: Connection, q: QuitCommand) => getUser(c) { user =>
          logger.debug("Quit command from " + user)
          deleteUser(user.reBroadcast(q))
        }
  
        case (c: Connection, j: JoinCommand) => getUser(c) { user =>
          logger.debug("Join command from " + user)
          updateConnection(user.joinChannels(j, j.chans.map(getChannel(_))))
        }
  
        case (c: Connection, p: PartCommand) => getUser(c) { user =>
          logger.debug("Part command from " + user)
          updateConnection(user.partChannels(p))
        }
  
        case (c: Connection, t: TopicCommand) => getUser(c) { user =>
          logger.debug("Topic command from " + user)
          user.topic(t)
        }
  
        case (c: Connection, n: NamesCommand) => getUser(c) { user =>
          logger.debug("Names command from " + user)
          for (chan <- getDesiredChannels(n.channels)) {
            chan.sendMememberNamesTo(user, false)
          }
          Channel.sendEndOfNamesToUser(user)
        }
  
        case (c: Connection, l: ListCommand) => getUser(c) { user =>
          logger.debug("List command from " + user)
          user.reply(ResponseCode.RPL_LISTSTART)
          for (chan <- getDesiredChannels(l.channels)) {
            chan.sendInfoTo(user)
          }
          user.reply(ResponseCode.RPL_LISTEND)
        }
  
        case (c: Connection, k: KickCommand) => getUser(c) { user =>
          logger.debug("Kick command from " + user)
          channels.get(k.channel) match {
            case None => user.returnError(ResponseCode.ERR_NOSUCHCHANNEL, k.channel.name)
            case Some(ch) => updateConnection(user.kickUserFromChannel(ch, k))
          }
        }
  
        case (c: Connection, m: PrivMsgCommand) => getUser(c) { user =>
          logger.debug("PrivMsg command from " + user)
          for (n <- m.nicknames) {
            getActiveUser(n.normalized) match {
              case None => user.returnError(ResponseCode.ERR_NOSUCHNICK, n.name)
              case Some(u) => u.messageFrom(user, m)
            }
          }
          for (c <- m.channels) {
            channels.get(c) match {
              case None => user.returnError(ResponseCode.ERR_NOSUCHCHANNEL, c.name)
              case Some(c) => user.messageToChannel(c, m)
            }
          }
        }
  
        case (c: Connection, n: NoticeCommand) => getUser(c) { user =>
          logger.debug("Notice command from " + user)
          for (u <- getActiveUser(n.nickname.normalized)) {
            u.noticeFrom(user, n)
          }
        }
  
        case (c: Connection, k: KillCommand) => getUser(c) { user =>
          logger.debug("Kill command from " + user)
          val victim = getActiveUser(k.nickname.normalized) match {
            case None => user.returnError(ResponseCode.ERR_NOSUCHNICK, k.nickname.name)
            case Some(u) => {
              u.reBroadcast(k.copyWithNewPrefix(user.maybeNickname.map(_.name)), false)
              deleteUser(u)
            }
          }
        }
  
        case (c: Connection, p: PingCommand) => getUser(c) { user =>
          logger.debug("Ping command from " + user)
          user.send(SupportedCommand(serverId, "PONG", List()))
        }
  
        case (c: Connection, p: PongCommand) => getUser(c) { user =>
          logger.debug("Pong command from " + user)
          // This just bumps the last seen time
        }
  
        case (c: Connection, a: AwayCommand) => getUser(c) { user =>
          logger.debug("Away command from " + user)
          updateConnection(user.away(a))
        }
      }
    }
  
  }

}
