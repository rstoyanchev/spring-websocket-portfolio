msgs.js
=======

Message oriented programming for JavaScript.  Inspired by Spring Integration.


Build Status
------------

<table>
  <tr><td>Master</td><td><a href="http://travis-ci.org/cujojs/msgs" target="_blank"><img src="https://secure.travis-ci.org/cujojs/msgs.png?branch=master" /></a></tr>
  <tr><td>Development</td><td><a href="http://travis-ci.org/cujojs/msgs" target="_blank"><img src="https://secure.travis-ci.org/cujojs/msgs.png?branch=dev" /></a></tr>
</table>


Overview
--------

msgs.js applies the vocabulary and patterns defined in the '[Enterprise Integration Patterns](http://www.eaipatterns.com/)' book to JavaScript extending messaging oriented programming into the browser and/or server side JavaScript. Messaging patterns originally developed to integrate loosely coupled disparate systems, apply just as well to loosely coupled modules within a single application process.

At the most basic level, `messages` are sent to `channels` and then dispatched to `handlers`. There are a wide variety of handler types that can easily be extended to provide rich behavior. The foundation handler types include: transforms, filters, routers, splitters and aggregators. Adapters and gateways provide ways in to and out of the messaging system. Channels dispatch messages to one or many handlers using a load balancer or pub-sub respectively, or queue messages until a poller consumes them.

Adapters are provided to aid integrating with popular external systems, protocols and APIs including: Node streams, Redis pub-sub, web sockets and web workers. Expect even more adapters in the future, or contribute your own.

All channels and handlers exist within the context of a message bus. The bus provides factories to create channels and handlers, in addition to a scope for referencing these components by name.

```javascript
var bus = require('msgs').bus();

bus.channel('lowercase');
bus.transformer(function (message) { return message.toUpperCase(); }, { input: 'lowercase', output: 'uppercase' });
bus.channel('uppercase');
bus.on('uppercase', function (str) {
  console.log(str);
});

bus.send('lowercase', 'hello world'); // 'HELLO WORLD'
```

This example defines two channels, `lowercase` and `uppercase`, and a transform that listens for messages on the `lowercase` channel converts them to upper case and sends the transformed message to the `uppercase` channel.  Finally, an adapter listens for messages on the `uppercase` channel and logs it to the console.  So when we send 'hello world' to the `lowercase` channel, 'HELLO WORLD' is logged to the console.

While converting a string to upper case is a bit contrived, it demonstrates the core concepts. A slightly more complex example starts to show the real power.

```javascript
var bus, webSocketServer;

require('msgs/adapters/stream');
require('msgs/channels/pubsub');

bus = require('msgs').bus();
webSocketServer = ...;

bus.pubsubChannel('broadcast');
webSocketServer.on('connection', function (connection) {
  bus.streamGateway(connection, { output: 'broadcast', input: 'broadcast' });
});

```

Here we're using a publish-subscribe channel to broadcast all messages received from a web socket to every connected web socket.  The `broadcast` channel serves as a medium to receive and dispatch messages. For each new web socket connection that is established, the streamGateway reads messages sent to the server, and then writes messages back to the client.

This works as long as there is only ever a single application instance, but what if we need to scale horizontally?  In that case, we just need to fold in a inter-process messaging solution, Redis in this case.

```javascript
var bus, webSocketServer, redis;

require('msgs/adapters/stream');
require('msgs/adapters/redis');
require('msgs/channels/pubsub');

bus = require('msgs').bus();
redis = require('redis');
webSocketServer = ...;

bus.pubsubChannel('fromClient');
bus.pubsubChannel('toClient');
webSocketServer.on('connection', function (connection) {
  bus.streamGateway(connection, { output: 'fromClient', input: 'toClient' });
});
bus.redisGateway(redis.createClient, 'redisTopic', { output: 'toClient', input: 'fromClient' });
```

We took the previous example, altering the streamGateway to use different channels for sending and receiving messages. The redisGateway bridges these channels while broadcasting messages to every other instance connected to Redis.

Once your application is using messaging, it's rather trivial to extend it into new environments.


Supported Environments
----------------------

Our goal is to work in every major JavaScript environment; Node.js and major browsers are actively tested and supported.

If your preferred environment is not supported, please let us know. Some features may not be available in all environments.

Tested environments:
- Node.js (0.6, 0.8. 0.10)
- Chrome (stable)
- Firefox (stable, ESR, should work in earlier versions)
- IE (6-10)
- Safari (5, 6, iOS 4-6, should work in earlier versions)
- Opera (11, 12, should work in earlier versions)

Specific browser test are provided by [Travis CI](https://travis-ci.org/cujojs/msgs) and [Sauce Labs' Open Sauce Plan](https://saucelabs.com/opensource). You can see [specific browser test results](https://saucelabs.com/u/cujojs-msgs), although odds are they do not reference this specific release/branch/commit.


Getting Started
---------------

msgs.js can be installed via [npm](https://npmjs.org/), [Bower](http://twitter.github.com/bower/), or from source.

To install without source:

    $ npm install msgs

or

    $ bower install msgs

From source:

    $ npm install

msgs.js is designed to run in a browser environment, utilizing [AMD modules](https://github.com/amdjs/amdjs-api/wiki/AMD), or within [Node.js](http://nodejs.org/).  [curl](https://github.com/cujojs/curl) is highly recommended as an AMD loader, although any loader should work.

An ECMAScript 5 compatible environment is assumed.  Older browsers, ::cough:: IE, that do not support ES5 natively can be shimmed.  Any shim should work, although we've tested against cujo's [poly](https://github.com/cujojs/poly)


Running the Tests
-----------------

The test suite can be run in two different modes: in node, or in a browser.  We use [npm](https://npmjs.org/) and [Buster.JS](http://busterjs.org/) as the test driver, buster is installed automatically with other dependencies.

Before running the test suite for the first time:

    $ npm install

To run the suite in node:

    $ npm test

To run the suite in a browser:

    $ npm start
    browse to http://localhost:8282/ in the browser(s) you wish to test.  It can take a few seconds to start.


Get in Touch
------------

You can find us on the [cujojs mailing list](https://groups.google.com/forum/#!forum/cujojs), or the #cujojs IRC channel on freenode.

Please report issues on [GitHub](https://github.com/cujojs/msgs/issues).  Include a brief description of the error, information about the runtime (including shims) and any error messages.

Feature requests are also welcome.


Contributors
------------

- Scott Andrews <sandrews@gopivotal.com>
- Mark Fisher <misher@gopivotal.com>

Please see CONTRIBUTING.md for details on how to contribute to this project.


Copyright
---------

Copyright 2012-2013 the original author or authors

msgs.js is made available under the MIT license.  See LICENSE.txt for details.



Change Log
----------

.next
- topic based channels using the form 'channelName!topic' anywhere a channel is resolved by name
- STOMP - support for subscribing to remote topics
- exchangeChannel providing basic subscription within a channel for topics
- topicExchangeChannel providing AMQP style topic bindings
- adapters for DOM events
- adapters for Backbone.js events
- removed bus.bridge() use bus.forward() instead
- renamed bus.transform() to bus.transformer()
- renamed WebWorker to MessagePort
- renamed NodeStream to Stream
- moved .inboundGateway() and .outboundGateway() from msgs into msgs/gateways, when.js is now an optional dependency
- bus.on('channel', listener) - syntatic sugar over outboundAdapter
- receive'ing from a queue returns the full message, not just the payload
- update when.js to 2.x, dropping 1.x

0.3.3
- extended when.js version to allow when@2.x
- add Node 0.10 as a tested environment
- component.json -> package.json for bower

0.3.2
- renamed project to 'msgs' from 'integration'
- migrate to 'cujojs' organization from 's2js'
- don't unsubcribe from input channel on redis connection end, the client will buffer commands and auto-reconnect

0.3.1
- bug fix, filters now work inside a chain
- easily `forward` messages from one channel to another
- Bower installable, with dependencies
- mutli-browser testing with Sauce Labs

0.3.0
- first release, everything is new
