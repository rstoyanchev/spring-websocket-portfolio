/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Unicast dispatcher. A single subscriber receives each message. Includes
	 * common load balancer strategies.
	 */
	define(function (require) {

		var subscribableDispatcher = require('./_subscribable');

		/**
		 * Dispatch messages to a single subscribed handler selected by the
		 * load balancer
		 *
		 * @param {Function} [loadBalancer=random] load balancer strategy,
		 *   defaults to the random load balancer
		 */
		function unicastDispatcher(loadBalancer) {
			var dispatcher, getHandlers;

			dispatcher = subscribableDispatcher();
			getHandlers = dispatcher._handlers;
			delete dispatcher._handlers;

			// default to a random load balancer
			loadBalancer = loadBalancer || unicastDispatcher.loadBalancers.random();

			/**
			 * Send a message to a single handler.
			 *
			 * @param {Message} message the message
			 * @param {Function} handlerResolver handler resolver
			 * @throws exceptions from recipient handler
			 * @returns {boolean} true if the message was received
			 */
			dispatcher.dispatch = function dispatch(message, handlerResolver) {
				var handlers, handler;

				handlers = getHandlers();
				if (handlers.length === 0) {
					return false;
				}

				handler = loadBalancer(handlers);
				handlerResolver(handler).handle(message);

				return true;
			};

			return dispatcher;
		}

		unicastDispatcher.loadBalancers = {
			random: function () {
				return function (handlers) {
					var i = Math.floor(Math.random() * handlers.length);
					return handlers[i];
				};
			},
			roundRobin: function () {
				var last;
				return function (handlers) {
					var i = (handlers.indexOf(last) + 1) % handlers.length;
					last = handlers[i];
					return last;
				};
			},
			naiveRoundRobin: function () {
				var last;
				return function (handlers) {
					var i = (last + 1) % handlers.length;
					last = i;
					return handlers[i];
				};
			}
		};

		return unicastDispatcher;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
