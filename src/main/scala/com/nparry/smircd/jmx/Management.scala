package com.nparry.smircd.jmx

import javax.management._

import com.nparry.smircd.daemon._

import grizzled.slf4j.Logger

trait IrcServerMXBean {
  def getPort(): Int
  def getActiveConnectionCount(): Int
  def getPendingConnectionCount(): Int
}

trait Management extends IrcServerComponent {

  this: ConnectionComponent with UserComponent with ChannelComponent =>

  val logger = Logger(this.getClass())
  val ircServerPort: Int;

  class JmxIrcServer(val server: IrcServer) extends IrcServerMXBean {
    def getPort() = ircServerPort
    def getActiveConnectionCount() = server.connectionStats._2
    def getPendingConnectionCount() = server.connectionStats._1
  }

  def mbeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer()
  def jmxName(serverId: String) = new ObjectName("com.nparry.smircd.IrcServer:serverId=" + serverId)

  override def makeServer(serverId: String, quit: () => Unit) = {
    val jmxWrapper = new JmxIrcServer(super.makeServer(serverId, { () =>
      logger.info("Unregistering mbean for " + serverId)
      mbeanServer.unregisterMBean(jmxName(serverId))
      quit()
    }))

    logger.info("Registering mbean for " + serverId)
    mbeanServer.registerMBean(jmxWrapper, jmxName(serverId))
    jmxWrapper.server
  }

}

