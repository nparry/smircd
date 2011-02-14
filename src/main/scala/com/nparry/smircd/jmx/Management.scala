package com.nparry.smircd.jmx

import javax.management._

import com.nparry.smircd.daemon._
import com.nparry.smircd.protocol._
import com.nparry.smircd.protocol.Command._

import grizzled.slf4j.Logger

trait IrcServerMXBean {
  def getPort(): Int
  def getActiveConnectionCount(): Int
  def getPendingConnectionCount(): Int
}

trait IrcChannelMXBean {
  def getMemberCount(): Int
  def getTopic(): String
  def setTopic(t: String): Unit
}

trait Management extends IrcServerComponent with ChannelComponent {

  this: ConnectionComponent with UserComponent =>

  val logger = Logger(this.getClass())
  val ircServerPort: Int;

  def mbeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer()
  def jmxName(s: String) = new ObjectName("com.nparry.smircd." + s)

  def wrapWithUnregistration[T](f: Function0[T], name: ObjectName): Function0[T] = { () =>
    logger.info("Unregistering mbean " + name)
    mbeanServer.unregisterMBean(name)
    f()
  }

  // Ack! Need to figure out how to wrap an arbitrary function :-(
  def wrapWithUnregistration[A, B](f: Function[A, B], name: ObjectName): Function[A, B] = { (a: A) =>
    logger.info("Unregistering mbean " + name)
    mbeanServer.unregisterMBean(name)
    f(a)
  }

  override def makeServer(serverId: String, quit: () => Unit) = {
    val mbeanName = jmxName("server:name=" + serverId)
    val server = super.makeServer(serverId, wrapWithUnregistration(quit, mbeanName))

    logger.info("Registering mbean for server " + serverId)
    mbeanServer.registerMBean(
      new IrcServerMXBean {
        def getPort() = ircServerPort
        def getActiveConnectionCount() = server.connectionStats._2
        def getPendingConnectionCount() = server.connectionStats._1
      }, mbeanName)

    server
  }

  override def makeChannel(
    serverId: String,
    name: ChannelName,
    memberLookup: (NickName.Normalized) => User.Registered,
    killMe: (ChannelName) => Unit) = {
    val mbeanName = jmxName("channel:server=" + serverId + ",name=" + name.name)
    val channel = super.makeChannel(serverId, name, memberLookup, wrapWithUnregistration(killMe, mbeanName))

    logger.info("Registering mbean for channel " + name)
    mbeanServer.registerMBean(
      new IrcChannelMXBean{
        def getMemberCount() = channel.members.size
        def getTopic() = channel.topic.getOrElse("<No topic>")
        def setTopic(t: String) = {
          logger.info("Changing topic of " + name + " via JMX")
          channel.memberChangedTopic(
            internalSystemAdministration,
            TopicCommand(ParsedCommand(None, "TOPIC", List(name.name, t))))
        }
      }, mbeanName)

    channel
  }

}

