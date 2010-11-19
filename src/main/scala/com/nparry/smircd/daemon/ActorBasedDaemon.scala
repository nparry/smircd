package com.nparry.smircd.daemon

import scala.actors._
import scala.actors.Actor._

object ActorBasedDaemon extends ConnectionComponent
  with IrcServerComponent
  with ChannelComponent
  with UserComponent
{
  type Connection = Actor
  val sendMsg = { (connection: Connection, msg: Any) =>
    connection ! msg
  }

  def apply(serverId: String): Daemon = {
    new Daemon(serverId)
  }

  class Daemon(val serverId: String) extends Actor {
    val server = new IrcServer(serverId, { () => exit() })

    def act() {
      loop {
        react {
          case msg: Any => server.processIncomingMsg(msg)
        }
      }
    }
  }
}


