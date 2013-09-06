/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Bridges to STOMP based servers
	 */
	define(function (require) {

		var msgs, stompDispatcher;

		msgs = require('../..');
		stompDispatcher = require('../dispatchers/stomp');

		// intentionally not requiring ../adapters/stream and ../adapters/webSocket
		// user should load them if their behavior is needed

		/**
		 * Basic STOMP bridge that can be used with any transport by providing input
		 * and output channels. The input channel is where messages destined for the
		 * remote server are sent, and the output channel is where remote messages
		 * are received.
		 *
		 * @param {string} [name] name to register the bridge as
		 * @param {string|Channel} opts.input channel to forward to the remote system
		 * @param {string|Channel} opts.output channel that exposes messages from the
		 *   remote system
		 * @param {string|Channel} [opts.error] channel to receive errors
		 * @param {string} opts.host virtual host that the client is connecting to
		 * @param {string} [opts.login] user identifier used to authenticate against
		 *   a secured STOMP server
		 * @param {string} [opts.passcode] password used to authenticate against a
		 *   secured STOMP server
		 * @param {string} [opts.ack='auto'] ack acknowledgment policy for received
		 *   messages for a subscription. May be one of 'auto', 'client', or
		 *   'client-individual'.
		 */
		msgs.prototype.stompBridge = msgs.utils.optionalName(function stompBridge(name, opts) {
			return this._channel(name, stompDispatcher(this, opts), 'stomp');
		});

		/**
		 * STOMP bridge that communicates over a WebSocket
		 *
		 * @param {string} [name] name to register the bridge as
		 * @param {WebSocket} socket the web socket
		 * @param {string} opts.host virtual host that the client is connecting to
		 * @param {string} [opts.login] user identifier used to authenticate against
		 *   a secured STOMP server
		 * @param {string} [opts.passcode] password used to authenticate against a
		 *   secured STOMP server
		 * @param {string} [opts.ack='auto'] ack acknowledgment policy for received
		 *   messages for a subscription. May be one of 'auto', 'client', or
		 *   'client-individual'.
		 */
		msgs.prototype.stompWebSocketBridge = msgs.utils.optionalName(function stompWebSocketBridge(name, socket, opts) {
			var input, output, error;

			input = this.channel();
			output = this.channel();
			error = this.channel();

			this.webSocketGateway(socket, { input: input, output: output, error: error });

			return this.stompBridge(name, this.utils.mixin({ input: output, output: input, error: error }, opts));
		});

		/**
		 * STOMP bridge that communicates over a Stream
		 *
		 * @param {string} [name] name to register the bridge as
		 * @param {Stream} stream the stream
		 * @param {string} opts.host virtual host that the client is connecting to
		 * @param {string} [opts.login] user identifier used to authenticate against
		 *   a secured STOMP server
		 * @param {string} [opts.passcode] password used to authenticate against a
		 *   secured STOMP server
		 * @param {string} [opts.ack='auto'] ack acknowledgment policy for received
		 *   messages for a subscription. May be one of 'auto', 'client', or
		 *   'client-individual'.
		 */
		msgs.prototype.stompStreamBridge = msgs.utils.optionalName(function stompStreamBridge(name, stream, opts) {
			var input, output, error;

			input = this.channel();
			output = this.channel();
			error = this.channel();

			this.streamGateway(stream, { input: input, output: output, error: error });

			return this.stompBridge(name, this.utils.mixin({ input: output, output: input, error: error }, opts));
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
