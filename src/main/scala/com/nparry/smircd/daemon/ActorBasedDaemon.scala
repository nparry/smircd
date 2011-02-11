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

  val pingThresholdMinutes = 1
  val dropThresholdMinutes = 5

  def apply(serverId: String): Daemon = {
    new Daemon(serverId)
  }

  class Daemon(val serverId: String) extends Actor {
    import java.util.concurrent._

    val pingDriver = Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      val delegate = Executors.defaultThreadFactory()
      override def newThread(r: Runnable): Thread = {
        val t = delegate.newThread(r)
        t.setName("SmircdDaemon[" + serverId + "] ping driver")
        t
      }
    })

    val server = new IrcServer(serverId, { () =>
      pingDriver.shutdownNow()
      pingDriver.awaitTermination(60, TimeUnit.SECONDS)
      exit()
    })

    def act() {
      pingDriver.scheduleWithFixedDelay(
        new Runnable {
          override def run { Daemon.this ! IrcServer.PingEm() }
        },
        60, 60, TimeUnit.SECONDS)

      loop {
        react {
          case msg: Any => server.processIncomingMsg(msg)
        }
      }
    }
  }
}


