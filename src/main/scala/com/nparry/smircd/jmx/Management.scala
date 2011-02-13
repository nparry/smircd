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

  def mbeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer()
  def jmxName(serverId: String) = new ObjectName("com.nparry.smircd.server:name=" + serverId)
  def wrapWithUnregistration(f: () => Unit, name: ObjectName) = { () =>
    logger.info("Unregistering mbean " + name)
    mbeanServer.unregisterMBean(name)
    f()
  }

  override def makeServer(serverId: String, quit: () => Unit) = {
    val name = jmxName(serverId)
    val server = super.makeServer(serverId, wrapWithUnregistration(quit, name))

    logger.info("Registering mbean for " + serverId)
    mbeanServer.registerMBean(
      new IrcServerMXBean {
        def getPort() = ircServerPort
        def getActiveConnectionCount() = server.connectionStats._2
        def getPendingConnectionCount() = server.connectionStats._1
      }, name)

    server
  }

}

