/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for web socket clients.
	 *
	 * Implemented to the W3C WebSocket API
	 * http://www.w3.org/TR/websockets/#the-websocket-interface
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Post messages from this socket to this channel
		 *
		 * @param {WebSocket} socket the socket to receive data from
		 * @param {string|Channel} opts.output the channel to send data to
		 */
		msgs.prototype.inboundWebSocketAdapter = function inboundWebSocketAdapter(socket, opts) {
			socket.addEventListener('message', this.inboundAdapter(opts.output, function (message) {
				return message.data;
			}));
		};

		/**
		 * Create a handler that writes message payloads to the socket
		 *
		 * @param {string} name name to register the adapter as
		 * @param {WebSocket} socket the web socket to write to
		 * @param {string|Channel} [opts.input] channel to send messages for
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundWebSocketAdapter = msgs.utils.optionalName(function outboundWebSocketAdapter(name, socket, opts) {
			var handler;

			handler = this.outboundAdapter(name, socket.send.bind(socket), opts);

			socket.addEventListener('close', function () {
				this.unsubscribe(opts.input, handler);
			}.bind(this));

			return handler;
		});

		/**
		 * Bridges channels and web sockets. New connections must have their bridge
		 * reestablished as the WebSocket object is not reused. Any exceptions are
		 * put on the error channel.
		 *
		 * @param {WebSocket} socket the web socket
		 * @param {string|Channel} [opts.input] channel for outbound messages
		 * @param {string|Channel} [opts.output] channel for inbound messages
		 * @param {string|Channel} [opts.error] channel for thrown exceptions
		 *   or socket errors
		 */
		msgs.prototype.webSocketGateway = function webSocketGateway(socket, opts) {
			if (opts.output) {
				this.inboundWebSocketAdapter(socket, opts);
			}
			if (opts.input) {
				this.outboundWebSocketAdapter(socket, opts);
			}
			if (opts.error) {
				socket.addEventListener('error',  this.inboundAdapter(opts.error));
			}
		};

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
