/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Channel with topical subscriptions
	 */
	define(function (require) {

		var msgs, exchangeDispatcher, broadcastDispatcher;

		msgs = require('..');
		exchangeDispatcher = require('./dispatchers/exchange');
		broadcastDispatcher = require('./dispatchers/broadcast');

		/**
		 * Create an exchange channel.
		 *
		 * The topic matcher and dispatcher are configurable, they default to the
		 * literal topic matcher and the broadcast dispatcher. By default all
		 * handlers subscribed to the exact message topic will receive the message.
		 *
		 * @param {string} [name] the name to register this channel as
		 * @param {Function} [opts.matcher=literal] matcher to decide if a message's
		 *   topic matches a subscription
		 * @param {Function} [opts.dispatchStrategy=broadcast] dispatcher to use
		 *   for subscribers within a topic, must be a subscribable dispatcher
		 */
		msgs.prototype.exchangeChannel = msgs.utils.optionalName(function exchangeChannel(name, opts) {
			opts = opts || {};
			opts.matcher = opts.matcher || exchangeDispatcher.matchers.literal;
			opts.dispatcher = opts.dispatcher || broadcastDispatcher;
			return this._channel(name, exchangeDispatcher(opts.matcher, opts.dispatcher), 'exchange');
		});

		/**
		 * Create a topic exchange channel. Topic subscriptions are handled as AMQP
		 * style bindings. The topic is a series of dot-delimited words with two
		 * wild cards:
		 * - '*' matches exactly one word
		 * - '#' matches zero or more words
		 *
		 * @param {string} [name] the name to register this channel as
		 */
		msgs.prototype.topicExchangeChannel = msgs.utils.optionalName(function topicExchangeChannel(name) {
			return this._channel(name, exchangeDispatcher(exchangeDispatcher.matchers.topical, broadcastDispatcher), 'topic-exchange');
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
