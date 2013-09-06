/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Dedicated dispatcher. A single, fixed handler for all messages.
	 */
	define(function (/* require */) {

		/**
		 * Dispatch messages to a specific handler
		 *
		 * @param {string|Handler} handler where to forward messages
		 */
		function directDispatcher(handler) {
			var dispatcher = {};

			/**
			 * Send a message to the configured handler
			 *
			 * @param {Message} message message to send
			 * @param {Function} handlerResolver handler resolver
			 * @throws exceptions from recipient channels
			 * @returns
			 */
			dispatcher.dispatch = function dispatch(message, handlerResolver) {
				handlerResolver(handler).handle(message);
				return true;
			};

			return dispatcher;
		}

		return directDispatcher;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
