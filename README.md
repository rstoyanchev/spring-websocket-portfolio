
## Overview

A sample application that demonstrates capabilities of the Spring Framework for building WebSocket-style messaging archichtures. The application uses [STOMP](http://stomp.github.io/) over WebSocket for messaging between browsers and server and the [SockJS protocol](https://github.com/sockjs/sockjs-protocol) for WebSocket fallback options.

Client-side libraries used:
* [stomp-websocket](https://github.com/jmesnil/stomp-websocket/)
* [sockjs-client](https://github.com/sockjs/sockjs-client)
* [Twitter Bootstrap](http://twitter.github.io/bootstrap/)
* [Knockout.js](http://knockoutjs.com/)

The server-side runs on `Tomcat 8`, `Jetty 9.0.4`, or `Glassfish 4.0`. At this time other servlet containers do not support WebSocket but the application should still function correctly (assuming Servlet 3.0) via HTTP fallback options.

### Tomcat 8

At present Tomcat 8 is [available in snapshot form](https://repository.apache.org/content/repositories/snapshots/org/apache/tomcat/tomcat/8.0-SNAPSHOT/). An alpha release should become available soon.

After unzipping Tomcat 8, set `TOMCAT8_HOME` as an environment variable and use the scripts [deployTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployTomcat8.sh) and [shutdownTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/shutdownTomcat8.sh) in this directory.

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Jetty 9

The easiest way to run on Jetty 9.0.4:

    mvn jetty:run

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

**Note:** To deploy to a Jetty installation, you'll need to add this to Jetty's start.ini:

    OPTIONS=plus
    etc/jetty-plus.xml
    OPTIONS=annotations
    etc/jetty-annotations.xml

### Glassfish

After unzipping the Glassfish 4 distribution, start the server:

    <unzip_dir>/glassfish4/bin/asadmin start-domain

Set GLASSFISH4_HOME as an environment variable and use the script [deployGlassfish.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployGlassfish.sh).

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Using a STOMP Message Broker

Out of the box, a _"simple" STOMP message broker_ is used to broadcast messages to subscribers but you can optionally connect to and use a fully featured STOMP message broker such as `RabbitMQ`, `ActiveMQ`, etc.

The first step is to install and start the message broker. If using RabbitMQ, you'll need to also install the [RabbitMQ STOMP plugin](http://www.rabbitmq.com/stomp.html). If using ActiveMQ, you'll need to [enable ActiveMQ for STOMP](http://activemq.apache.org/stomp.html).

The second step is to activate the `stomp-broker-relay` profile in [DispatcherServletInitializer.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/DispatcherServletInitializer.java#L50). More details on using a STOMP message broker are provided below.

You may also need to configure the `relayPort`, `relayHost`, `systemLogin`, and `systemPassword` properties of the `StompBrokerRelayMessageHandler` bean in [WebConfig.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/WebConfig.java) to match the configuratino of the message broker. The default settings in `StompBrokerRelayMessageHandler` should work with a default installation of `RabbitMQ`. For ActiveMQ you may need to set `relayPort` to 61612.


