/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Queue channel. Messages are stored until retrieved.
	 */
	define(function (require) {

		var msgs, pollableDispatcher;

		msgs = require('..');
		pollableDispatcher = require('./dispatchers/pollable');

		/**
		 * Receive the next message on a queue
		 *
		 * @param {string|Channel} target the channel to receive the message
		 *   from
		 * @returns {Object} the message payload or undefined if no message is
		 *   available
		 */
		msgs.prototype.receive = function receive(target) {
			return this.resolveChannel(target).receive();
		};

		/**
		 * Create a message queue. Messages are queued and retrieved
		 * individually.
		 *
		 * @param {string} [name] the name to register this channel under
		 * @param {Queue} [queueStrategy] the queuing strategy for this
		 *   channel. The queue must support 'push' and 'shift' for adding and
		 *   removing messages from the queue respectively. Queues may or may
		 *   not be durable. The default queue is a basic Array.
		 */
		msgs.prototype.queueChannel = msgs.utils.optionalName(function queueChannel(name, queueStrategy) {
			return this._channel(name, pollableDispatcher(queueStrategy), 'queue');
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
