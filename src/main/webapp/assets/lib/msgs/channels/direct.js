/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Ad hoc channel. A channel backed by a dedicated handler.
	 */
	define(function (require) {

		var msgs, directDispatcher;

		msgs = require('..');
		directDispatcher = require('./dispatchers/direct');

		/**
		 * Channel that has one, and only one, handler
		 *
		 * @param {string} [name] the name to register this channel under
		 * @param {string|Handler} handler the handler for this channel
		 * @returns {Channle} the channel
		 */
		msgs.prototype.directChannel = msgs.utils.optionalName(function directChannel(name, handler) {
			return this._channel(name, directDispatcher(handler), 'direct');
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
