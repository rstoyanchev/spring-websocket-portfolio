/*
 * Copyright 2012-2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for MessagePort's
	 *
	 * Implemented to the W3C MessagePort, Worker APIs
	 * http://www.w3.org/TR/webmessaging/#message-ports
	 * http://www.w3.org/TR/workers/#dedicated-workers-and-the-worker-interface
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Post messages from this port to this channel
		 *
		 * @param {MessagePort} port the message port to receive
		 *   messages from
		 * @param {string|Channel} opts.output the channel to send messages to
		 */
		msgs.prototype.inboundMessagePortAdapter = function inboundMessagePortAdapter(port, opts) {
			port.addEventListener('message', this.inboundAdapter(opts.output, function (event) {
				return event.data;
			}));
		};

		/**
		 * Create a handler that sends to a message port
		 *
		 * @param {string} name name to register the adapter as
		 * @param {MessagePort} port the message port to post to
		 * @param {string|Channel} [opts.input] channel to send messages for
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundMessagePortAdapter = msgs.utils.optionalName(function outboundMessagePortAdapter(name, port, opts) {
			return this.outboundAdapter(name, port.postMessage, opts);
		});

		/**
		 * Bridges channels and message ports. Any exceptions are put on the error
		 * channel.
		 *
		 * @param {MessagePort} port the message port
		 * @param {string|Channel} [opts.input] channel for outbound messages
		 * @param {string|Channel} [opts.output] channel for inbound messages
		 * @param {string|Channel} [opts.error] channel for thrown exceptions
		 *   or port errors
		 */
		msgs.prototype.messagePortGateway = function messagePortGateway(port, opts) {
			if (opts.output) {
				this.inboundMessagePortAdapter(port, opts);
			}
			if (opts.input) {
				this.outboundMessagePortAdapter(port, opts);
			}
			if (opts.error) {
				port.addEventListener('error', this.inboundAdapter(opts.error));
			}
		};

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
