
## Overview

A sample application that demonstrates capabilities of the Spring Framework for building WebSocket-style messaging archichtures. The application uses the [STOMP protocol](http://stomp.github.io/) (over WebSocket) for messaging between browsers and server and the [SockJS protocol](https://github.com/sockjs/sockjs-protocol) for WebSocket fallback options.

Client-side libraries:
* [stomp-websocket](https://github.com/jmesnil/stomp-websocket/)
* [sockjs-client](https://github.com/sockjs/sockjs-client)
* [Twitter Bootstrap](http://twitter.github.io/bootstrap/)
* [Knockout.js](http://knockoutjs.com/)

Server-side runs on `Tomcat 8`, `Jetty 9.0.4`, or `Glassfish 4.0`. Other servlet containers should also function correctly via fallback options (assuming Servlet 3.0) but they don't support WebSocket yet.

### Tomcat 8

At present Tomcat 8 is [available as snapshots](https://repository.apache.org/content/repositories/snapshots/org/apache/tomcat/tomcat/8.0-SNAPSHOT/) (an alpha release should be available soon).

After unzipping Tomcat 8, set `TOMCAT8_HOME` as an environment variable and use [deployTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployTomcat8.sh) and [shutdownTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/shutdownTomcat8.sh) in this directory.

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Jetty 9

The easiest way to run on Jetty 9.0.4:

    mvn jetty:run

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

**Note:** To deploy to a Jetty installation, add this to Jetty's start.ini:

    OPTIONS=plus
    etc/jetty-plus.xml
    OPTIONS=annotations
    etc/jetty-annotations.xml

### Glassfish

After unzipping the Glassfish 4 distribution start the server:

    <unzip_dir>/glassfish4/bin/asadmin start-domain

Set `GLASSFISH4_HOME` as an environment variable and use [deployGlassfish.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployGlassfish.sh).

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Using a Message Broker

Out of the box, a _"simple" message broker_ is used to send messages to subscribers (e.g. stock quotes) but you can optionally use a fully featured STOMP message broker such as `RabbitMQ`, `ActiveMQ`, and others, by following these steps:

# Install and start the message broker. If using RabbitMQ, make sure you install the RabbitMQ [STOMP plugin](http://www.rabbitmq.com/stomp.html) as it is not installed by default. If using ActiveMQ, you'll need to enable [ActiveMQ for STOMP](http://activemq.apache.org/stomp.html).

# Comment in the `stomp-broker-relay` profile and comment out the `simple-broker` profile in [DispatcherServletInitializer.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/DispatcherServletInitializer.java#L50).

# You may also need to set the `relayHost`, `relayPort`, `systemLogin`, and `systemPassword` properties of the `StompBrokerRelayMessageHandler` bean in [WebConfig.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/WebConfig.java) depending on where your message broker is running and how it is configured. The default settings of `StompBrokerRelayMessageHandler` should match those of the RabbitMQ STOMP plugin. For ActiveMQ you'll need to set `relayPort` to 61612.
