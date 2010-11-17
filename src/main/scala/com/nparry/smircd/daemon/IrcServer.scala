package com.nparry.smircd.daemon

import java.util.Date

import scala.actors._
import scala.actors.Actor._

import scala.collection.mutable.{Map => MMap}

import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

object IrcServer {
  case class OutboundMessage(cmd: SupportedCommand)
  case class Shutdown()
}

class IrcServer(val serverId: String) extends Actor {
  val logger = Logger(this.getClass())

  val pendingConnections = MMap[Actor, User.Pending]()
  val activeConnections = MMap[Actor, User.Registered]()

  val lastSeen = MMap[Actor, Date]()

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

  def getUser[T](a: Actor)(useUser: (User) => T): Option[T] = {
    lastSeen.put(a, new Date())
    activeConnections.get(a).orElse(pendingConnections.get(a)).map { u =>
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

  val userInfo = MMap[NickName.Normalized, Actor]()

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

  val channels = MMap[ChannelName, Channel]()

  def getChannel(name: ChannelName): Channel = {
    logger.trace("Looking for channel " + name)
    channels.get(name).getOrElse {
      logger.trace("Creating new channel for " + name)
      val c = new Channel(
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
   if (names.isEmpty)
     channels.values
   else
     channels.filterKeys(names.contains(_)).values
  }
  
  def act() {
    loop {
      react {
        case IrcServer.Shutdown() => {
          logger.info("Shutting down")
          for (u <- pendingConnections.values) {
            deleteUser(u)
          }
          for (u <- activeConnections.values) {
            deleteUser(u)
          }

          exit()
        }

        case (a: Actor, joining: Boolean) => {
          if (joining) {
            logger.debug("New user joining")
            updateConnection(User.Pending(a, serverId))
          }
          else {
            getUser(a) { user =>
              logger.debug("Connection broken to " + user)
              deleteUser(user.reBroadcast(SupportedCommand(
                user.maybeNickname.map(_.name),
                "QUIT",
                Some("Connection lost"))))
            }
          }
        }

        case (a: Actor, p: PassCommand) => getUser(a) { user =>
          logger.debug("Password command from " + user)
          user.updatePassword(p.password) match {
            case Left(rspCode) => user.returnError(rspCode)
            case Right(updatedUser) => updateConnection(updatedUser)
          }
        }

        case (a: Actor, n: NickCommand) => getUser(a) { user =>
          logger.debug("Nickname command from " + user)
          if (isNickCollision(user, n.nickname)) {
            logger.debug("Nickname collision for " + user + " using " + n.nickname)
            user match {
              case p: User.Pending => {
                user.returnError(ResponseCode.ERR_NICKNAMEINUSE, true)
                deleteUser(p)
              }
              case r: User.Registered => {
                user.returnError(ResponseCode.ERR_NICKCOLLISION)
              }
            }
          }
          else {
            updateNick(user, Some(n.nickname)) 
            updateConnection(user.updateNickname(n))
          }
        }

        case (a: Actor, u: UserCommand) => getUser(a) { user =>
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
        
        case (a: Actor, q: QuitCommand) => getUser(a) { user =>
          logger.debug("Quit command from " + user)
          deleteUser(user.reBroadcast(q))
        }

        case (a: Actor, j: JoinCommand) => getUser(a) { user =>
          logger.debug("Join command from " + user)
          updateConnection(user.joinChannels(j, j.chans.map(getChannel(_))))
        }

        case (a: Actor, p: PartCommand) => getUser(a) { user =>
          logger.debug("Part command from " + user)
          updateConnection(user.partChannels(p))
        }

        case (a: Actor, t: TopicCommand) => getUser(a) { user =>
          logger.debug("Topic command from " + user)
          user.topic(t)
        }

        case (a: Actor, n: NamesCommand) => getUser(a) { user =>
          logger.debug("Names command from " + user)
          for (chan <- getDesiredChannels(n.channels)) {
            chan.sendMememberNamesTo(user, false)
          }
          Channel.sendEndOfNamesToUser(user)
        }

        case (a: Actor, l: ListCommand) => getUser(a) { user =>
          logger.debug("List command from " + user)
          user.reply(ResponseCode.RPL_LISTSTART)
          for (chan <- getDesiredChannels(l.channels)) {
            chan.sendInfoTo(user)
          }
          user.reply(ResponseCode.RPL_LISTEND)
        }

        case (a: Actor, k: KickCommand) => getUser(a) { user =>
          logger.debug("Kick command from " + user)
          channels.get(k.channel) match {
            case None => user.returnError(ResponseCode.ERR_NOSUCHCHANNEL, k.channel.name)
            case Some(c) => updateConnection(user.kickUserFromChannel(c, k))
          }
        }

        case (a: Actor, m: PrivMsgCommand) => getUser(a) { user =>
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

        case (a: Actor, n: NoticeCommand) => getUser(a) { user =>
          logger.debug("Notice command from " + user)
          for (u <- getActiveUser(n.nickname.normalized)) {
            u.noticeFrom(user, n)
          }
        }

        case (a: Actor, k: KillCommand) => getUser(a) { user =>
          logger.debug("Kill command from " + user)
          val victim = getActiveUser(k.nickname.normalized) match {
            case None => user.returnError(ResponseCode.ERR_NOSUCHNICK, k.nickname.name)
            case Some(u) => deleteUser(u)
          }
        }

        case (a: Actor, p: PingCommand) => getUser(a) { user =>
          logger.debug("Ping command from " + user)
          user.send(SupportedCommand(serverId, "PONG", List()))
        }

        case (a: Actor, p: PongCommand) => getUser(a) { user =>
          logger.debug("Pong command from " + user)
          // This just bumps the last seen time
        }

        case (a: Actor, c: AwayCommand) => getUser(a) { user =>
          logger.debug("Away command from " + user)
          updateConnection(user.away(c))
        }
      }
    }
  }

}

