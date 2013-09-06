/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var undef;

	/**
	 * Common base for subscribable dispatchers
	 */
	define(function (/* require */) {

		/**
		 * Subscribable dispatcher
		 */
		function SubscribableDispatcher() {

			var handlers = [];

			/**
			 * Add a new handler to receive messages sent to this channel
			 *
			 * @param {Handler|string} handler the handler to receive messages
			 */
			this.subscribe = function subscribe(handler) {
				if (handlers.indexOf(handler) >= 0) {
					// already subscribed
					return;
				}
				handlers.push(handler);
			};

			/**
			 * Removes a handler from receiving messages sent to this channel
			 *
			 * @param {Handler|string} handler the handler to stop receiving messages
			 */
			this.unsubscribe = function unsubscribe(handler) {
				var index = handlers.indexOf(handler);
				if (index >= 0) {
					handlers = handlers.slice(0, index).concat(handlers.slice(index + 1));
				}
			};

			/**
			 * @returns {boolean} true if a handler is subscribed to this dispatcher
			 */
			this.subscribed = function subscribed() {
				return handlers.length !== 0;
			};

			/**
			 * Unsubscribe all handlers
			 */
			this.destroy = function destroy() {
				handlers = undef;
			};

			this.channelMixins = {
				subscribe: this.subscribe,
				unsubscribe: this.unsubscribe,
				destroy: this.destroy
			};

			/**
			 * Obtain a copy of the list of handlers
			 *
			 * @return {Array} the handlers
			 */
			this._handlers = function () {
				return handlers.slice();
			};

		}

		SubscribableDispatcher.prototype = {

			/**
			 * Send a messages to the desired recipients
			 *
			 * @param {Message} message the message to send
			 * @param {Function} handlerResolver handler resolver
			 */
			dispatch: function dispatch(/* message, handlerResolver */) {
				// to be overridden
				return false;
			}

		};

		function subscribableDispatcher() {
			return new SubscribableDispatcher();
		}

		return subscribableDispatcher;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
