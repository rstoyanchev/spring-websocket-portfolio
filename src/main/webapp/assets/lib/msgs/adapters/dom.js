/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for DOM events
	 *
	 * Implemented to the W3C DOM Events API with fallbacks for IE <9
	 * http://www.w3.org/TR/DOM-Level-3-Events
	 * http://msdn.microsoft.com/en-us/library/jj853328(v=vs.85).aspx
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Listen for events from this node forwarding them to this channel
		 *
		 * @param {Node} node the DOM node to listen on
		 * @param {string} opts.event the DOM event to listen for
		 * @param {string} [opts.phase='bubble'] the event phase to listen, either 'capture' or 'bubble'
		 * @param {string|Channel} opts.output the channel to send messages to
		 */
		msgs.prototype.inboundDOMAdapter = function inboundDOMAdapter(node, opts) {
			var adapter = this.inboundAdapter(opts.output);
			if (node.addEventListener) {
				node.addEventListener(opts.event, adapter, opts.phase === 'capture');
			}
			else if (node.attachEvent) {
				node.attachEvent('on' + opts.event, adapter);
			}
			else {
				throw new Error('A DOM node is expected');
			}
		};

		/**
		 * Create a handler that fires events on the target node. The message payload
		 * must be an initialized DOM event ready to be dispatched.
		 *
		 * @param {string} name name to register the adapter as
		 * @param {Node} node the DOM node to fire the event on
		 * @param {string|Channel} [opts.input] channel to send messages for
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundDOMAdapter = msgs.utils.optionalName(function outboundDOMAdapter(name, node, opts) {
			return this.outboundAdapter(name, (node.dispatchEvent || function (event) {
				this.fireEvent('on' + (event.type || 'custom'), event);
			}).bind(node), opts);
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
