
## Overview

A sample application demonstrating capabilities in the Spring Framework for building WebSocket-style messaging archichtures. The application uses [STOMP](http://stomp.github.io/) (over WebSocket) for messaging between browsers and server and [SockJS](https://github.com/sockjs/sockjs-protocol) for WebSocket fallback options.

Client-side libraries used:
* [stomp-websocket](https://github.com/jmesnil/stomp-websocket/)
* [sockjs-client](https://github.com/sockjs/sockjs-client)
* [Twitter Bootstrap](http://twitter.github.io/bootstrap/)
* [Knockout.js](http://knockoutjs.com/)

Server-side runs on `Tomcat 8`, `Jetty 9.0.4`, or `Glassfish 4.0`. Other servlet containers should also function correctly via fallback options (assuming Servlet 3.0) but they don't support WebSocket yet.

Also see the [blog post](http://blog.springsource.org) accompanying the sample.

### Tomcat 8

At present Tomcat 8 is [available as snapshots](https://repository.apache.org/content/repositories/snapshots/org/apache/tomcat/tomcat/8.0-SNAPSHOT/) (alpha release is forthcoming).

After unzipping Tomcat 8, set `TOMCAT8_HOME` as an environment variable and use [deployTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployTomcat8.sh) and [shutdownTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/shutdownTomcat8.sh) in this directory.

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Jetty 9

The easiest way to run on Jetty 9.0.4:

    mvn jetty:run

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

**Note:** To deploy to a Jetty installation, add this to Jetty's `start.ini`:

    OPTIONS=plus
    etc/jetty-plus.xml
    OPTIONS=annotations
    etc/jetty-annotations.xml

### Glassfish

After unzipping Glassfish 4 start the server:

    <unzip_dir>/glassfish4/bin/asadmin start-domain

Set `GLASSFISH4_HOME` as an environment variable and use [deployGlassfish.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployGlassfish.sh) in this directory.

Open a browser and go to [http://localhost:8080/spring-websocket-portfolio/index.html](localhost:8080/spring-websocket-portfolio/index.html)

### Using a Message Broker

Out of the box, a _"simple" message broker_ is used to send messages to subscribers (e.g. stock quotes) but you can optionally use a fully featured STOMP message broker such as `RabbitMQ`, `ActiveMQ`, and others, by following these steps:

1.   Install and start the message broker. For RabbitMQ make sure you install the [RabbitMQ STOMP plugin](http://www.rabbitmq.com/stomp.html) as it is not installed by default. For ActiveMQ you need to configure a [STOMP transport connnector](http://activemq.apache.org/stomp.html).
2.   Comment in the `stomp-broker-relay` profile and comment out the `simple-broker` profile in [DispatcherServletInitializer.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/DispatcherServletInitializer.java#L50).
3.   You may also need to set one or more of `relayHost`, `relayPort`, `systemLogin`, and `systemPassword` properties of `StompBrokerRelayMessageHandler` in [WebConfig.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/WebConfig.java) depending on your message broker configuration. The default settings of `StompBrokerRelayMessageHandler` should work for RabbitMQ and ActiveMQ.

### Logging

To see all logging, enable TRACE for `org.springframework.messaging` and `org.springframework.samples` in [log4j.xml](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/resources/log4j.xml). That will produce a lot of information as messages flow through the application. One constant source of messages is the [QuoteService](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/service/QuoteService.java). You can modify it to send quotes less frequently or simply disable it by commenting out the `@Scheduled` annotation.





