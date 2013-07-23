
## Overview

Demonstrates capabilities of the Spring Framework for building WebSocket-style messaging archichtures.

The application uses [STOMP](http://stomp.github.io/) for messaging between browsers and server and [SockJS](http://sockjs.org) for WebSocket fallback options.

Client-side libraries:
* [stomp.js](https://github.com/jmesnil/stomp-websocket/)
* [SockJS client](https://github.com/sockjs/sockjs-client)
* [Twitter Bootstrap](http://twitter.github.io/bootstrap/)
* [Knockout.js](http://knockoutjs.com/)

The server-side can run on Tomcat 8, Jetty 9.0.4, or Glassfish 4.0. Other servlet containers do not yet support WebSocket but the application should also function correctly on any Servlet 3.0 container via HTTP fallback options.

By default the server-side uses a "simple" STOMP message broker but can optionally connect to and use a fully featured STOMP message broker such as RabbitMQ, ActiveMQ, etc. This can be enabled by activating the `stomp-broker-relay` profile in [DispatcherServletInitializer.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/DispatcherServletInitializer.java#L50). More details on using a STOMP message broker a provided below.

### Running on Tomcat 8

Currently Tomcat 8 is [available in snapshot form](https://repository.apache.org/content/repositories/snapshots/org/apache/tomcat/tomcat/8.0-SNAPSHOT/) only. An alpha release should become available in the coming weeks.

Once a Tomcat 8 binary is available.on your system, set TOMCAT8_HOME as an environment variable and use the scripts [deployTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployTomcat8.sh) and [shutdownTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/shutdownTomcat8.sh).

Go to [localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Jetty 9

The easiest way to run on Jetty 9.0.4 is this:

    mvn jetty:run

Go to [localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

If you have a local installation that you want to deploy to, you will probably need to add this to Jetty's start.ini:

    OPTIONS=plus
    etc/jetty-plus.xml
    OPTIONS=annotations
    etc/jetty-annotations.xml

### Glassfish

After unzipping the Glassfish 4 distribution, start the server:

    cd <unzip_dir>/glassfish4
    bin/asadmin start-domain

Set GLASSFISH4_HOME as an environment variable and then run [deployGlassfish.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployGlassfish.sh).

Go to [localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)



