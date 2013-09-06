/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for Backbone.js events
	 *
	 * @see http://backbonejs.org/#Events
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Convert Backbone events to messages on the output channel.
		 *
		 * The payload of the resulting message is the first argument to the
		 * callback. As Backbone events can contain multiple arguments, the raw
		 * arguments are exposed as an array under the 'args' message header.
		 *
		 * @param {Backbone} bbobj the Backbone evented object
		 * @param {string|Channel} opts.output the channel to send data to
		 * @param {string} [opts.events='all'] Backbone events to listen for
		 */
		msgs.prototype.inboundBackboneAdapter = function inboundBackboneAdapter(bbobj, opts) {
			var events = opts.events || 'all';
			bbobj.on(events, this.inboundAdapter(opts.output, function (payload) {
				return this._message(payload, { args: Array.prototype.slice.call(arguments) });
			}.bind(this)));
		};

		/**
		 * Create a handler that triggers events on the Backbone object when a
		 * message is received.
		 *
		 * @param {string} name name to register the adapter as
		 * @param {Backbone} bbobj the Backbone evented object
		 * @param {string|Channel} [opts.input] the channel receive messages for
		 * @param {string} opts.events Backbone events to trigger
		 * @param {boolean} [opts.apply=false] applies the payload as arguments
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundBackboneAdapter = msgs.utils.optionalName(function outboundBackboneAdapter(name, bbobj, opts) {
			opts = opts || {};
			if (!opts.events) { throw new Error('\'events\' option is requried for outboundBackboneAdapter'); }
			var events = opts.events.split(/\s+/);
			return this.outboundAdapter(name, function (payload) {
				events.forEach(function (event) {
					var args = opts.apply ? [event].concat(payload) : [event, payload];
					bbobj.trigger.apply(bbobj, args);
				});
			}, opts);
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
