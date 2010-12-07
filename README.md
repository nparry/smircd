SmIRCd - Scalarific minimal IRC daemon
======================================

A basic low-feature IRC daemon written in [Scala](http://www.scala-lang.org/) and built via [SBT](http://code.google.com/p/simple-build-tool/).

What it does
--------------------------------------

The following IRC commands are supported:

* NICK
* QUIT
* AWAY
* MSG
* NOTICE
* JOIN
* LEAVE
* KICK
* TOPIC

The following commands are supported but incomplete:

* LIST
* NAMES

What it doesn't do (yet)
--------------------------------------

In addition to the commands absent from the above list, notable missing features include:

* Passwords - SmIRCd ignores any password you send when logging in.
* Operators - No permissions checks are applied; anyone can do anything.
* Modes - There are neither user nor channel modes.
* Multi-Server - SmIRCd is stand-alone, it does not connect with other IRC servers.

Build it!
--------------------------------------

    $ sbt proguard

This creates an executable minified uberjar.

    $ ls target/scala_2.8.0/*.min.jar
    target/scala_2.8.0/smircd_2.8.0-1.0.min.jar

SBT handles all the dependencies...

* [Netty](http://www.jboss.org/netty)
* [SLF4J and Logback](http://logback.qos.ch/)
* [Grizzled (SLF4J Scala wrapper)](http://bmc.github.com/grizzled-slf4j/)

Run it!
--------------------------------------

    $ java -jar target/scala_2.8.0/smircd_2.8.0-1.0.min.jar

This starts SmIRCd listening on port 6667 and blocks until you press Ctrl+C.

Optional command line arguments are:

    $ java -jar <jar> [--port p] [--logLevel ALL|TRACE|DEBUG|INFO|WARN|ERROR]

    --port     Listening port, defaults to 6667
    --logLevel Logging verbosity, defaults to INFO

License
--------------------------------------

Apache 2.0, see the LICENSE file.

