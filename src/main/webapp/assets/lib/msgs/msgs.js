/*
 * Copyright 2012-2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var undef;

	/**
	 * Global msgs bus including all core components and facilities. Domain
	 * specific functionality is located in other modules that augment this
	 * object.
	 *
	 * Creating child buses for specific tasks or modules of an application is
	 * highly recommended. Each child bus is able to resolve channels and
	 * handlers from its  parent. Children may export components to a parent
	 * bus to expose endpoints for a sub-flow.
	 *
	 * Advanced functionality may be added to all buses within the system by
	 * adding properties to this objects 'prototype'.
	 */
	define(function (require) {

		var broadcastDispatcher, directDispatcher, unicastDispatcher, busCounter, channelTopicParserRE;

		broadcastDispatcher = require('./channels/dispatchers/broadcast');
		directDispatcher = require('./channels/dispatchers/direct');
		unicastDispatcher = require('./channels/dispatchers/unicast');

		busCounter = counter();
		channelTopicParserRE = /^([^!]*)(?:!([\w\W]*))?$/;

		/**
		 * Create a new message
		 *
		 * @param {Object} payload content of the message
		 * @param {Object} [headers] meta data for the message
		 */
		function Message(payload, headers) {
			this.payload = payload;
			this.headers = Object.freeze(headers || {});
			Object.freeze(this);
		}

		Message.prototype = {

			/**
			 * Create a new message from this message overriding certain
			 * headers with the provided values. The current message is not
			 * modified.
			 *
			 * @param {Object} [payload] payload for the new message, defaults
			 *   to the current message payload
			 * @param {Object} declaredHeaders headers that overwrite the
			 *   current message's headers
			 * @return {Message} a new message with the same payload and new
			 *   headers
			 */
			mixin: function (payload, declaredHeaders) {
				var headers;

				if (arguments.length < 2) {
					declaredHeaders = payload;
					payload = this.payload;
				}

				headers = mixin(this.headers);
				if (declaredHeaders) {
					headers = mixin(headers, declaredHeaders);
				}

				return new Message(payload, headers);
			}

		};

		/**
		 * Holds a reference to a channel or handler that can be resolved
		 * later. Useful for sharing components outside of their home bus.
		 */
		function Ref(resolver) {
			this.resolve = resolver;
		}

		/**
		 * @returns true if a Ref
		 */
		function isRef(ref) {
			return ref instanceof Ref;
		}

		/**
		 * Create a new message bus
		 *
		 * @param {MessageBus} [parent] a parent message bus to extend from
		 */
		function MessageBus(parent) {
			var components = {},
				children = [],
				busId = busCounter(),
				messageCounter = counter();

			/**
			 * @param {Function} [config] configuration helper invoked in the
			 * context of the bus.
			 *
			 * @returns a new message bus who's parent is the current bus
			 */
			this.bus = function bus(config) {
				var messageBus = new MessageBus(this);
				children.push(messageBus);
				if (config) {
					config.call(messageBus, messageBus);
				}
				return messageBus;
			};

			/**
			 * Create a new message
			 *
			 * @param {Object|Message} payload the message payload
			 * @param {Object} [declaredHeaders] the message headers
			 * @returns the new message
			 */
			this._message = function _message(payload, declaredHeaders) {
				var headers;

				headers = {};
				declaredHeaders = declaredHeaders || {};
				Object.keys(declaredHeaders).forEach(function (header) {
					headers[header] = declaredHeaders[header];
				}, this);

				if (!('id' in declaredHeaders)) {
					headers.id = busId + '-' + messageCounter();
				}

				return this.isMessage(payload) ?
					payload.mixin(headers) :
					new Message(payload, headers);
			};

			/**
			 * Find a handler by name. If the handler is not found in the local
			 * message bus, the parent message bus is queried.
			 *
			 * @param {string|Handler} name the handler name to find
			 * @returns the found handler, undefined when not found
			 */
			this.resolveHandler = function resolveHandler(name) {
				var handler;
				if (this.isHandler(name)) {
					return name;
				}
				if (name in components) {
					handler = components[name];
					if (isRef(handler)) {
						handler = handler.resolve();
					}
					return this.resolveHandler(handler);
				}
				if (parent) {
					return parent.resolveHandler(name);
				}
			};

			/**
			 * Find a channel by name. If the channel is not found in the local
			 * message bus, the parent message bus is queried.
			 *
			 * @param {string|Channel} name the channel name to find
			 * @returns the found channel, undefined when not found
			 */
			this.resolveChannel = function resolveChannel(name) {
				var topic, channel;

				if (this.isChannel(name)) {
					return name;
				}

				if (name.match) {
					(function (results) {
						name = results[1];
						topic = results[2];
					}(name.match(channelTopicParserRE)));
				}

				if (name in components) {
					channel = components[name];
					if (isRef(channel)) {
						channel = channel.resolve();
					}
					channel = this.resolveChannel(channel);
				}
				else if (parent) {
					channel = parent.resolveChannel(name);
				}

				if (topic) {
					channel = topicizeChannel(topic, channel);
				}

				return channel;
			};

			/**
			 * Create an alias for a handler or channel
			 *
			 * @param {string} name the alias
			 * @param {string|Channel|Handler} component the item to register
			 */
			this.alias = function alias(name, component) {
				if (!(this.resolveChannel(component) || this.resolveHandler(component) || isRef(component))) {
					throw new Error('Unable to alias: handler or channel is required');
				}
				if (!name) {
					throw new Error('Unable to alias: name is required');
				}
				if (name in components) {
					throw new Error('Unable to alias: the name \'' + name + '\' is in use');
				}
				components[name] = component;
			};

			/**
			 * Dead letter channel that handles messages that were sent, but
			 * have no handlers.
			 */
			this.deadLetterChannel = this._channel('deadLetterChannel', broadcastDispatcher());

			/**
			 * Invalid message channel that handles messages when an error was
			 * encountered sending the message.
			 */
			this.invalidMessageChannel = this._channel('invalidMessageChannel', broadcastDispatcher());

			if (parent) {
				// share messages with parent's channels
				this.deadLetterChannel.subscribe(this.forward(parent.deadLetterChannel));
				this.invalidMessageChannel.subscribe(this.forward(parent.invalidMessageChannel));

				/**
				 * Make a channel available to the parent bus. Useful for
				 * defining contained sub flows that provide entry and exit
				 * points.
				 *
				 * @param {string} [name] the name to export as
				 * @param {string|Channel} channel the channel to export
				 */
				this.exportChannel = function exportChannel(name, channel) {
					if (arguments.length === 1) {
						channel = name;
					}
					parent.alias(name, new Ref(function () {
						return this.resolveChannel(channel);
					}.bind(this)));
				};

				/**
				 * Deconstructor that cleans up any lingering state that would
				 * not be automatically garbage collected
				 */
				this.destroy = function destroy() {
					children.forEach(function (bus) {
						bus.destroy();
					});
					Object.keys(components).forEach(function (name) {
						var component = components[name];
						if (component.destroy) {
							component.destroy();
						}
						delete components[name];
					}, this);
					this.deadLetterChannel.destroy();
					this.invalidMessageChannel.destroy();
				};
			}
		}

		MessageBus.prototype = {

			/**
			 * @returns true if the object is a message
			 */
			isMessage: function isMessage(message) {
				return message instanceof Message;
			},

			/**
			 * @returns true if the object can handle messages
			 */
			isHandler: function isHandler(handler) {
				return handler && typeof handler.handle === 'function';
			},

			/**
			 * @returns true if the object can send messages
			 */
			isChannel: function isChannel(channel) {
				return channel && typeof channel.send === 'function';
			},

			/**
			 * @returns true is the object is a message bus
			 */
			isBus: function isBus(bus) {
				return bus instanceof MessageBus;
			},

			/**
			 * Create a new channel to pass messages
			 *
			 * @param {string} [name] the name to register this channel under
			 * @param {Dispatcher} dispatcher dispatching strategy for this
			 *   channel
			 * @param {string} [type] type of channel, mostly used for tests
			 * @returns {Channel} a new channel
			 */
			_channel: function _channel(name, dispatcher, type) {
				var taps, channel;

				channel = {
					send: function send(message) {
						if (taps) {
							try {
								taps.dispatch(message, this.resolveHandler.bind(this));
							}
							catch (e) {
								// squelch, wiretaps must never interfere with normal operation
							}
						}
						try {
							if (!dispatcher.dispatch(message, this.resolveHandler.bind(this))) {
								if (channel !== this.deadLetterChannel) {
									this.send(this.deadLetterChannel, message);
								}
							}
						}
						catch (e) {
							if (channel !== this.invalidMessageChannel) {
								this.send(this.invalidMessageChannel, message, { error: e });
							}
						}
					}.bind(this),
					tap: function tap(handler) {
						if (!taps) {
							taps = broadcastDispatcher();
						}
						taps.subscribe(handler);
					},
					untap: function untap(handler) {
						if (!taps) {
							return;
						}
						taps.unsubscribe(handler);
					},
					type: type
				};

				Object.keys(dispatcher.channelMixins || {}).forEach(function (prop) {
					channel[prop] = dispatcher.channelMixins[prop];
				});

				channel.destroy = function destroy() {
					if (taps) {
						taps.destroy();
					}
					if (dispatcher.destroy) {
						dispatcher.destroy();
					}
				};

				if (name) {
					this.alias(name, channel);
				}

				return channel;
			},

			/**
			 * Create a new handler
			 *
			 * @param {string} [name] the name to register this handler under
			 * @param {Function} transform function to transform the message
			 * @param {string|Channel} [outputChannel] where to forward the
			 *   handled message
			 * @param {string|Channel} [inputChannel] channel to receive
			 *   messages from
			 * @param {string|Channel} [errorChannel] where to forward the
			 *   message when an error occurs
			 * @returns a new handler
			 */
			_handler: function _handler(name, transform, outputChannel, inputChannel, errorChannel) {
				var handler = {
					handle: function handle(message, outputChannelOverride) {
						var payload, nextOutput, nextError;
						try {
							nextOutput = outputChannelOverride || outputChannel || message.headers.replyChannel;
							nextError = errorChannel || message.headers.errorChannel;
							payload = transform.call(this, message, nextOutput, nextError);
							if (payload && nextOutput) {
								this.send(nextOutput, payload, message.headers);
							}
						}
						catch (e) {
							if (nextError) {
								this.send(nextError, message, { error: e });
							}
							else {
								throw e;
							}
						}
					}.bind(this)
				};

				if (name) {
					this.alias(name, handler);
				}
				if (inputChannel && this.subscribe) {
					this.subscribe(inputChannel, handler);
				}

				return handler;
			},

			/**
			 * Create a unicast channel. Messages are load balanced between
			 * each subscriber. Only one handler receives a copy of each
			 * message sent to this channel.
			 *
			 * @param {string} [name] the name to register this channel under
			 * @param {Function} [loadBalancer] load balancer
			 * @returns the channel
			 */
			channel: optionalName(function channel(name, loadBalancer) {
				return this._channel(name, unicastDispatcher(loadBalancer), 'default');
			}),

			/**
			 * Subscribe a handler to a channel. The channel must be
			 * subscribable
			 *
			 * @param {string|Channel} from the publishing channel
			 * @param {string|Handler} to the consuming handler
			 */
			subscribe: function subscribe(from, to) {
				this.resolveChannel(from).subscribe(to);
			},

			/**
			 * Unsubscribe a handler from a channel. The channel must be
			 * subscribable
			 *
			 * @param {string|Channel} from the publishing channel
			 * @param {string|Handler} to the consuming handler
			 */
			unsubscribe: function unsubscribe(from, to) {
				this.resolveChannel(from).unsubscribe(to);
			},

			/**
			 * Wire tap a channel. The channel must be tappable
			 *
			 * @param {string|Channel} channel the channel to tap
			 * @param {string|Handler} handler the receiver of tapped messages
			 */
			tap: function tap(channel, handler) {
				this.resolveChannel(channel).tap(handler);
			},

			/**
			 * Remove a wire tap from a channel. The channel must be tappable
			 *
			 * @param {string|Channel} channel the channel to untap
			 * @param {string|Handler} handler the receiver of tapped messages
			 */
			untap: function untap(channel, handler) {
				this.resolveChannel(channel).untap(handler);
			},

			/**
			 * Create and send a message to a channel
			 *
			 * @param {string|Channel} channel the channel to sent the message to
			 * @param {Object|Message} payload the message to send
			 * @param {Object} [headers] headers for the message
			 */
			send: function send(channel, payload, headers) {
				this.resolveChannel(channel).send(this._message(payload, headers));
			},

			/**
			 * Forwards messages from one channel directly to another
			 *
			 * @param {string|Channel} [from] source channel
			 * @param {string|Channel} to recipient channel
			 */
			forward: function forward(from, to) {
				if (arguments.length === 1) {
					to = from;
					from = undef;
				}
				return this._handler(undef, this.utils.noop, to, from);
			},

			/**
			 * Subscribe a listener to a channel
			 *
			 * @param {string|Channel} channel subscription target
			 * @param {Function} listener receiver of messages from the target channel.
			 *   The message payload and headeres are provided as arguments to the
			 *   listener.
			 * @returns {Handler} the subscription handler, needed to unsubscribe
			 */
			on: function on(channel, listener) {
				return this._handler(undef, function (message) {
					listener(message.payload, message.headers);
				}, this.noopChannel, channel);
			},

			/**
			 * Treat an array of handlers as if they are a single handler. Each
			 * handler is executed in order with the message from the previous
			 * handler in the pipeline.
			 *
			 * @param {string} [name] the name to register the pipeline as
			 * @param {Array[Handler]} handlers array of handlers
			 * @param {string|Channel} [opts.output] the channel to forward
			 *   messages to
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the pipeline
			 */
			chain: optionalName(function chain(name, handlers, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					handlers.map(this.resolveHandler, this).forEach(function (handler) {
						if (!message) { return; }
						var m = message;
						// unset 'message' forcing it to be handled in order to continue in the chain
						message = undef;
						handler.handle(m, {
							send: function send(m) {
								message = m;
								return true;
							}
						});
					}, this);
					return message;
				}, opts.output, opts.input, opts.error);
			}),

			/**
			 * Transform messages sent to this channel
			 *
			 * @param {string} [name] the name to register the transform as
			 * @param {Function} transform transform function, invoked with
			 *   message payload and message headers as args, a new payload
			 *   must be returned.
			 * @param {string|Channel} [opts.output] the channel to forward
			 *   transformed messages to
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the transformer
			 */
			transformer: optionalName(function transformer(name, transform, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					return message.mixin(transform.call(undef, message.payload, message.headers), {});
				}, opts.output, opts.input, opts.error);
			}),

			/**
			 * Filter messages based on some criteria. Abandoned messages may
			 * be forward to a discard channel if defined.
			 *
			 * @param {string} [name] the name to register the filter as
			 * @param {Function} rule filter function, invoked with message
			 *   payload and message headers as args. If true is returned, the
			 *   message is forwarded, otherwise it is discarded.
			 * @param {string|Channel} [opts.output] the channel to forward
			 *   messages to
			 * @param {string|Channel} [opts.discard] channel to handle
			 *   discarded messages
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the filter
			 */
			filter: optionalName(function filter(name, rule, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					if (rule.call(this, message.payload, message.headers)) {
						return message;
					}
					else if (opts.discard) {
						this.send(opts.discard, message, { discardedBy: name });
					}
				}, opts.output, opts.input, opts.error);
			}),

			/**
			 * Route messages to handlers defined by the rule. The rule may
			 *   return 0..n recipient channels.
			 * @param {string} [name] the name to register the router as
			 * @param {Function} rule function that accepts the message and
			 *   defined routes returning channels to route the message to
			 * @param {Object|Array} [opts.routes] channel aliases for the
			 *   router
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the router
			 */
			router: optionalName(function router(name, rule, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					var recipients = rule.call(this, message, opts.routes);
					if (!(recipients instanceof Array)) {
						recipients = [recipients];
					}
					opts.routes = opts.routes || {};
					recipients.forEach(function (recipient) {
						this.send(recipient in opts.routes ? opts.routes[recipient] : recipient, message);
					}, this);
				}, this.noopChannel, opts.input, opts.error);
			}),

			/**
			 * Split one message into many
			 *
			 * @param {string} [name] the name to register the splitter as
			 * @param {Function} rule function that accepts a message and
			 *   returns an array of messages
			 * @param {string|Channel} [opts.output] the channel to forward
			 *   split messages to
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the splitter
			 */
			splitter: optionalName(function splitter(name, rule, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					rule.call(this, message).forEach(function (splitMessage, index, splitMessages) {
						this.send(opts.output, splitMessage, {
							sequenceNumber: index,
							sequenceSize: splitMessages.length,
							correlationId: message.headers.id
						});
					}, this);
				}, this.noopChannel, opts.input, opts.error);
			}),

			/**
			 * Aggregate multiple messages into a single message
			 *
			 * @param {string} [name] the name to register the aggregator as
			 * @param {Function} strategy function that accepts a message and
			 *   a callback function. When the strategy determines a new
			 *   message is ready, it must invoke the callback function with
			 *   that message.
			 * @param {string|Channel} [opts.output] the channel to forward
			 *   aggregated messages to
			 * @param {string|Channel} [opts.input] the channel to receive
			 *   message from
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns the aggregator
			 */
			aggregator: optionalName(function aggregator(name, correlator, opts) {
				opts = opts || {};
				var release = function (payload, headers) {
					this.send(opts.output, payload, headers);
				}.bind(this);
				return this._handler(name, function (message) {
					correlator.call(this, message, release);
				}, this.noopChannel, opts.input, opts.error);
			}),

			/**
			 * Log messages at the desired level
			 *
			 * @param {string} [name] the name to register the logger as
			 * @param {Console} [opts.console=console] the console to log with
			 * @param {string} [opts.level='log'] the console level to log at,
			 *   defaults to 'log'
			 * @param {Object|string} [opts.prefix] value included with the
			 *   logged message
			 * @param {string|Channel} [opts.tap] the channel to log messages
			 *   from
			 * @returns the logger
			 */
			logger: optionalName(function logger(name, opts) {
				opts = opts || {};
				opts.console = opts.console || console;
				opts.level = opts.level || 'log';
				var handler, channel;
				handler = this._handler(name, function (message) {
					var output = 'prefix' in opts ?
						[opts.prefix, message] :
						[message];
					opts.console[opts.level].apply(opts.console, output);
				}, this.noopChannel);
				channel = this.resolveChannel(opts.tap);
				if (channel && channel.tap) {
					channel.tap(handler);
				}
				return handler;
			}),

			/**
			 * Post messages to a channel that can be invoked as a JS function.
			 * The first argument of the returned function becomes the message
			 * payload.
			 *
			 * @param {string|Channel} output the channel to post messages to
			 * @param {Function} [adapter] function to adapt the arguments into
			 *   a message payload. The function must return a message payload.
			 * @returns a common function that sends messages
			 */
			inboundAdapter: function inboundAdapter(output, adapter) {
				var counter = this.utils.counter();
				adapter = adapter || this.utils.noop;
				return function () {
					var payload = adapter.apply(arguments[0], arguments);
					if (payload !== undef) {
						this.send(output, payload, { sequenceNumber: counter() });
					}
				}.bind(this);
			},

			/**
			 * Bridge a handler to a common function. The function is invoked
			 * as messages are handled with the message payload provided as an
			 * argument.
			 *
			 * @param {string} [name] the name to register the adapter as
			 * @param {Function} func common JS function to invoke
			 * @param {string|Channel} opts.input the channel to output
			 *   messages for
			 * @param {string|Channel} [opts.error] channel to receive errors
			 * @returns {Handler} the handler for this adapter
			 */
			outboundAdapter: optionalName(function outboundAdapter(name, func, opts) {
				opts = opts || {};
				return this._handler(name, function (message) {
					func.call(undef, message.payload);
				}, this.noopChannel, opts.input, opts.error);
			}),

			/**
			 * Channel that does nothing
			 */
			noopChannel: Object.freeze({
				send: function () {
					return true;
				}
			}),

			/**
			 * Handler that does nothing
			 */
			noopHandler: Object.freeze({
				handle: function () {}
			}),

			/**
			 * Common helpers that are useful to other modules but not worthy
			 * of their own module
			 */
			utils: {
				counter: counter,
				mixin: mixin,
				noop: function noop() { return arguments[0]; },
				optionalName: optionalName,
				topicizeChannel: topicizeChannel
			}

		};

		// make it easy for custom extensions to the MessageBus prototype
		MessageBus.prototype.prototype = MessageBus.prototype;

		return new MessageBus();

	});

	/**
	 * Incrementing counter
	 */
	function counter() {
		/*jshint plusplus:false */
		var count = 0;

		return function increment() {
			return count++;
		};
	}

	/**
	 * Mixin util. Copies properties from the props object to the target object.
	 * Will create a shallow clone if only one args is provided.
	 *
	 * @param {Object} target the object to copy properties to
	 * @param {Object} [props] the source of properties to copy
	 */
	function mixin(target, props) {
		if (arguments.length < 2) {
			props = target;
			target = {};
		}
		for (var prop in props) {
			if (props.hasOwnProperty(prop)) {
				target[prop] = props[prop];
			}
		}
		return target;
	}

	/**
	 * Detect if the first parameter is a name. If the param is omitted,
	 * arguments are normalized and passed to the wrapped function.
	 * Behavior is undesirable if the second argument can be a string.
	 *
	 * @param {Function} func function who's first parameter is a name that
	 *   may be omitted.
	 */
	function optionalName(func) {
		return function (name) {
			var args = Array.prototype.slice.call(arguments);
			if (typeof name !== 'string') {
				// use empty string instead of undef so that this optionalName helpers can be stacked
				args.unshift('');
			}
			return func.apply(this, args);
		};
	}

	/**
	 * Transform a channel to enable topical subscriptions. Wraps the channel's
	 * 'send', 'subscribe' and 'unsubscribe' augmenting the method args with the
	 * topic info.
	 *
	 * The original channel is unaffected.
	 *
	 * @param {string} topic the topic
	 * @param {Channel} the channel to topicize
	 * @returns {Channel} the channel topicized
	 */
	function topicizeChannel(topic, channel) {
		var send, subscribe, unsubscribe;

		send = channel.send;
		subscribe = channel.subscribe;
		unsubscribe = channel.unsubscribe;

		channel = Object.create(channel);

		if (send) {
			channel.send = function (message) {
				return send.call(this, message.mixin({ topic: topic }));
			};
		}
		if (subscribe) {
			channel.subscribe = function (handler) {
				return subscribe.call(this, topic, handler);
			};
		}
		if (unsubscribe) {
			channel.unsubscribe = function (handler) {
				return unsubscribe.call(this, topic, handler);
			};
		}

		return channel;
	}

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
