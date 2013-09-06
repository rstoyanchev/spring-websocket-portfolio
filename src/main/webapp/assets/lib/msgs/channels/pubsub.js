/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Pub/sub channel. Messages are broadcast to all subscribers.
	 */
	define(function (require) {

		var msgs, broadcastDispatcher;

		msgs = require('..');
		broadcastDispatcher = require('./dispatchers/broadcast');

		/**
		 * Create a publish-subscribe channel. Each subscriber receives a copy
		 * of the messages sent to this channel.
		 *
		 * @param {string} [name] the name to register this channel under
		 * @returns the channel
		 */
		msgs.prototype.pubsubChannel = msgs.utils.optionalName(function pubsubChannel(name) {
			return this._channel(name, broadcastDispatcher(), 'pubsub');
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
