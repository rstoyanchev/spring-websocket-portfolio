## Overview

A sample demonstrating capabilities in the Spring Framework to build WebSocket-style messaging applications. The application uses [STOMP](http://stomp.github.io/) (over WebSocket) for messaging between browsers and server and [SockJS](https://github.com/sockjs/sockjs-protocol) for WebSocket fallback options.
Well, that's the original example. I modified a bit by using postal.js to do message broker thing on client side and 
using only one topic on the backend side. The idea is using only one queue on the backend to hold any message that will be sent to browser
because after trying the real message broker, rabbitmq in this case, there were queue left over that keep piling up
when the user is disconnected or server died.
To simulate security, I used token based authentication on URL endpoint that is used when initializing stomp connection.
See WebSocketConfig for auth logic. It is as simple as checking token existence. You can go further by validating the token and
return the actual principal based on your token in distributed fashion (it can be useful in polyglot architecture 
just don't forget to set allowed origin if you do so). The other idea came to mind is, instead of relying on queue on message dispatch,
it is better using topic for publishing message so that you can load balance your websocket server and still keep recognize
who is connecting by token as mentioned earlier.

Client-side libraries used:
* [stomp-websocket](https://github.com/jmesnil/stomp-websocket/)
* [sockjs-client](https://github.com/sockjs/sockjs-client)
* [Twitter Bootstrap](http://twitter.github.io/bootstrap/)
* [Knockout.js](http://knockoutjs.com/)
* [Postal.js](https://github.com/postaljs/postal.js)

Server-side runs on `Tomcat 7.0.47+`, `Jetty 9.0.7+`, or `Glassfish 4.0`. Other servlet containers should also function correctly via fallback options (assuming Servlet 3.0) but they don't support WebSocket yet.

Also see the [blog post](http://blog.springsource.org/2013/07/24/spring-framework-4-0-m2-websocket-messaging-architectures/) introducing these features.

### Preparation

So far I've not succeeded on calling lodash from webjar so install it manually:

`bower install lodash#3.10.1`

### Tomcat 7/8

The app has been tested with this `Tomcat 8 RC10` as well as `Tomcat 7.0.47` which includes a backport of the Tomcat 8 WebSocket support.

For Tomcat 8, set `TOMCAT8_HOME` as an environment variable and use [deployTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployTomcat8.sh) and [shutdownTomcat8.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/shutdownTomcat8.sh) in this directory.

For Tomcat 7, you can use `mvn tomcat7:run`.

Open a browser and go to <http://localhost:8080/spring-websocket-portfolio/index.html>

### Jetty 9

The easiest way to run on Jetty 9 is `mvn jetty:run`.

Open a browser and go to <http://localhost:8080/spring-websocket-portfolio/index.html>

**Note:** To deploy to a Jetty installation, add this to Jetty's `start.ini`:

    OPTIONS=plus
    etc/jetty-plus.xml
    OPTIONS=annotations
    etc/jetty-annotations.xml

### Glassfish

After unzipping Glassfish 4 start the server:

    <unzip_dir>/glassfish4/bin/asadmin start-domain

Set `GLASSFISH4_HOME` as an environment variable and use [deployGlassfish.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployGlassfish.sh) in this directory.

Open a browser and go to <http://localhost:8080/spring-websocket-portfolio/index.html>

### WildFly/Undertow

Support available startin with Spring Framework 4.0.1. Requires WildFly 8.0.0.Final.

Unzip the WildFly server.

Set `WILDFLY_HOME` as an environment variable and use [deployWildFly.sh](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/deployWildFly.sh) in this directory.

Open a browser and go to <http://localhost:8080/spring-websocket-portfolio/index.html>


### Using a Message Broker

Out of the box, a _"simple" message broker_ is used to send messages to subscribers (e.g. stock quotes) but you can optionally use a fully featured STOMP message broker such as `RabbitMQ`, `ActiveMQ`, and others, by following these steps:

1.   Install and start the message broker. For RabbitMQ make sure you've also installed the [RabbitMQ STOMP plugin](http://www.rabbitmq.com/stomp.html). For ActiveMQ you need to configure a [STOMP transport connnector](http://activemq.apache.org/stomp.html).
2.   Use the `MessageBrokerConfigurer` in [WebSocketConfig.java](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/config/WebSocketConfig.java) to enable the STOMP broker relay instead of the simple broker.
3.   You may also need to configure additional STOMP broker relay properties such as `relayHost`, `relayPort`, `systemLogin`, `systemPassword`, depending on your message broker. The default settings should work for RabbitMQ and ActiveMQ.


### Logging

To see all logging, enable TRACE for `org.springframework.messaging` and `org.springframework.samples` in [log4j.xml](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/resources/log4j.xml).

Keep in mind that will generate a lot of information as messages flow through the application. The [QuoteService](https://github.com/rstoyanchev/spring-websocket-portfolio/blob/master/src/main/java/org/springframework/samples/portfolio/service/QuoteService.java) for example generates a lot of messages frequently. You can modify it to send quotes less frequently or simply comment out the `@Scheduled` annotation.





