/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var undef;

	/**
	 * Exchange dispatcher. Subscribers matching the message topic receive the
	 * message according to the topic dispatching strategy.
	 */
	define(function (/* require */) {

		/**
		 * @param {Function} topicMatcher function to compare subscribed topics with
		 *   the message topic. Complex matchers should be memorized for performance.
		 * @param {Function} topicDispatcher function to create a dispatcher for a
		 *   subscribed topic
		 */
		function exchangeDispatcher(topicMatcher, topicDispatcher) {
			var dispatcher, topics;

			dispatcher = {};
			topics = {};

			/**
			 * Add a new handler to receive messages sent to this channel
			 *
			 * @param {string} topic the topic to subscribe to
			 * @param {Handler|string} handler the handler to receive messages
			 */
			dispatcher.subscribe = function subscribe(topic, handler) {
				if (!topics.hasOwnProperty(topic)) {
					topics[topic] = topicDispatcher();
				}
				topics[topic].subscribe(handler);
			};

			/**
			 * Removes a handler from receiving messages sent to this channel
			 *
			 * @param {string} topic the topic to unsubscribe from
			 * @param {Handler|string} handler the handler to stop receiving
			 *   messages
			 */
			dispatcher.unsubscribe = function unsubscribe(topic, handler) {
				if (!topics.hasOwnProperty(topic)) {
					return;
				}
				var dispatcher = topics[topic];
				dispatcher.unsubscribe(handler);
				if (!dispatcher.subscribed()) {
					// remove dispatcher if no handlers remain
					dispatcher.destroy();
					delete topics[topic];
				}
			};

			/**
			 * Unsubscribe all handlers
			 */
			dispatcher.destroy = function destroy() {
				for (var topic in topics) {
					if (topics.hasOwnProperty(topic)) {
						topics[topic].destroy();
					}
				}
				topics = undef;
			};

			/**
			 * Send a message to all subscribed handlers.
			 *
			 * @param {Message} message message to send
			 * @param {Function} handlerResolver handler resolver
			 * @throws exceptions from recipient handlers
			 * @returns
			 */
			dispatcher.dispatch = function dispatch(message, handlerResolver) {
				var errors, topic, dispatchers;

				errors = [];
				topic = message.headers.topic;
				dispatchers = Object.keys(topics).filter(function (t) {
					return topicMatcher(t, topic);
				});

				if (dispatchers.length === 0) {
					return false;
				}

				dispatchers.forEach(function (dispatcher) {
					try {
						topics[dispatcher].dispatch(message, handlerResolver);
					}
					catch (e) {
						errors.push(e);
					}
				}, this);

				if (errors.length !== 0) {
					throw errors;
				}

				return true;
			};

			dispatcher.channelMixins = {
				subscribe: dispatcher.subscribe,
				unsubscribe: dispatcher.unsubscribe,
				destroy: dispatcher.destroy
			};

			return dispatcher;
		}

		exchangeDispatcher.matchers = {
			literal: function literalMatcher(subscription, topic) {
				return subscription === topic;
			},
			topical: (function () {
				var cache = {},
				    wordSeparatorRE = /\./g,
				    singleWordRE = /\*/g,
				    multiMultiWordsRE = /#\\\.#/g,
				    multiWordLeadingRE = /#\\\./g,
				    multiWordTrailingRE = /\\\.#/g,
				    multiWordRE = /#/g;
				return function (subscription, topic) {
					var pattern, re;
					if (cache[subscription] instanceof RegExp) {
						re = cache[subscription];
					}
					else {
						pattern = subscription.replace(wordSeparatorRE, '\\.')
						                      .replace(singleWordRE, '[^.]+')
						                      .replace(multiMultiWordsRE, '#')
						                      .replace(multiWordLeadingRE, '(?:.+\\.)?')
						                      .replace(multiWordTrailingRE, '(?:\\..+)?')
						                      .replace(multiWordRE, '(?:.+)?');
						re = cache[subscription] = new RegExp('^' + pattern + '$');
					}
					return re.test(topic);
				};
			}())
		};

		return exchangeDispatcher;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
