SmIRCd - Scalarific minimal IRC daemon [![Build Status](https://travis-ci.org/nparry/smircd.svg)](https://travis-ci.org/nparry/smircd)
======================================

A basic low-feature IRC daemon written in [Scala](http://www.scala-lang.org/).

You can use any IRC client to connect to SmIRCd. SmIRCd also bundles a basic browser hosted IRC client.

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

    $ sbt assembly

This creates an executable uberjar.

    $ ls target/scala-*/*-assembly-*.jar
    target/scala-2.11/smircd-assembly-1.0-SNAPSHOT.jar

Run it!
--------------------------------------

    $ java -jar target/scala-2.11/smircd-assembly-1.0-SNAPSHOT.jar

This starts SmIRCd listening on port 6667 and blocks until you press Ctrl+C.

Optional command line arguments are:

    $ java -jar <jar> [--port p] [--logLevel ALL|TRACE|DEBUG|INFO|WARN|ERROR]

    --port     Listening port, defaults to 6667
    --logLevel Logging verbosity, defaults to INFO

Use it!
--------------------------------------

Once SmIRCd is running, point your browser to http://localhost:6667 to use the embedded
browser based IRC client. Or, use your favorite dedicated IRC client - SmIRCd uses the
same port for IRC traffic and HTTP. Note that the browser client requires
[Websocket support](http://en.wikipedia.org/wiki/WebSockets#Browsers_supporting_WebSocket).

License
--------------------------------------

Apache 2.0, see the LICENSE file.

