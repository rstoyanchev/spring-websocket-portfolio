/*
 * Copyright 2012-2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var undef;

	/**
	 * Adapters for Node.js style streams.
	 */
	define(function (require) {

		var msgs = require('./msgs'),
		    directDispatcher = require('./channels/dispatchers/direct'),
		    when = require('when');

		/**
		 * Gateway between application code that expects a reply and the
		 * message bus. Similar to an inbound adapter, however, the
		 * returned function itself returns a promise representing the
		 * outcome of the message.
		 *
		 * @param {string|Channel} output the channel to post messages to
		 * @returns {Function} a function that when invoked places a
		 *   message on the bus that returns a promise representing the
		 *   outcome of the message
		 */
		msgs.prototype.inboundGateway = function inboundGateway(output) {
			var counter = this.utils.counter();
			return function (payload) {
				var defer;

				defer = when.defer();
				this.send(output, payload, {
					replyChannel: this._channel(undef, directDispatcher(this.outboundAdapter(defer.resolve))),
					errorChannel: this._channel(undef, directDispatcher(this.outboundAdapter(defer.reject))),
					sequenceNumber: counter()
				});

				return defer.promise;
			}.bind(this);
		};

		/**
		 * Gateway out of the messaging system to a traditional service
		 * within the application. The service may return an object, which
		 * becomes the reply message payload, or a promise to defer a reply.
		 *
		 * @param {string} [name] the name to register the activator as
		 * @param {Function} service the service to activate. Invoked with
		 *   the message payload and headers as arguments.
		 * @param {string|Channel} [opts.output] the channel to receive
		 *   replies from the service
		 * @param {string|Channel} [opts.input] the channel to receive
		 *   message from
		 * @param {string|Channel} [opts.error] channel to receive errors
		 * @returns the service activator handler
		 */
		msgs.prototype.outboundGateway = msgs.utils.optionalName(function outboundGateway(name, service, opts) {
			opts = opts || {};
			return this._handler(name, function (message, reply, error) {
				when(service.call(this, message.payload, message.headers),
					function (result) {
						this.send(reply, result, message.headers);
					}.bind(this),
					function (result) {
						this.send(error, result, message.headers);
					}.bind(this)
				);
			}, opts.output, opts.input, opts.error);
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
