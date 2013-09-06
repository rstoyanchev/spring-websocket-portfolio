/*
 * Copyright 2012-2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for Node.js style streams.
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Post messages from this stream to this channel
		 *
		 * @param {Stream} stream the stream to receive data from
		 * @param {string|Channel} opts.output the channel to send data to
		 */
		msgs.prototype.inboundStreamAdapter = function inboundStreamAdapter(stream, opts) {
			stream.on('data', this.inboundAdapter(opts.output));
		};

		/**
		 * Create a handler that writes message payloads to the stream
		 *
		 * @param {string} name name to register the adapter as
		 * @param {Stream} stream the stream to write messages to
		 * @param {string|Channel} [opts.input] the channel receive messages for
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundStreamAdapter = msgs.utils.optionalName(function outboundStreamAdapter(name, stream, opts) {
			var handler;

			handler = this.outboundAdapter(name, stream.write.bind(stream), opts);

			stream.on('close', function () {
				this.unsubscribe(opts.input, handler);
			}.bind(this));

			return handler;
		});

		/**
		 * Bridges channels and streams. Streams adapted to the input and output
		 * channels. Any exceptions are put on the error channel.
		 *
		 * @param {Stream} stream the stream
		 * @param {string|Channel} [opts.output] channel for inbound messages
		 * @param {string|Channel} [opts.input] channel for outbound messages
		 * @param {string|Channel} [opts.error] channel for thrown exceptions
		 */
		msgs.prototype.streamGateway = function streamGateway(stream, opts) {
			if (opts.output) {
				this.inboundStreamAdapter(stream, opts);
			}
			if (opts.input) {
				this.outboundStreamAdapter(stream, opts);
			}
			if (opts.error) {
				stream.on('error', this.inboundAdapter(opts.error));
			}
		};

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
