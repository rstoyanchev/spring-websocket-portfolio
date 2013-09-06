/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Broadcast dispatcher. All subscribers receive each message.
	 */
	define(function (require) {

		var subscribableDispatcher = require('./_subscribable');

		/**
		 * Dispatch messages to all subscribed handlers
		 */
		function broadcastDispatcher() {
			var dispatcher, getHandlers;

			dispatcher = subscribableDispatcher();
			getHandlers = dispatcher._handlers;
			delete dispatcher._handlers;

			/**
			 * Send a message to all subscribed handlers.
			 *
			 * @param {Message} message message to send
			 * @param {Function} handlerResolver handler resolver
			 * @throws exceptions from recipient handlers
			 * @returns
			 */
			dispatcher.dispatch = function dispatch(message, handlerResolver) {
				var errors, handlers;

				errors = [];
				handlers = getHandlers();

				if (handlers.length === 0) {
					return false;
				}

				handlers.forEach(function (handler) {
					try {
						handlerResolver(handler).handle(message);
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

			return dispatcher;
		}

		return broadcastDispatcher;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
