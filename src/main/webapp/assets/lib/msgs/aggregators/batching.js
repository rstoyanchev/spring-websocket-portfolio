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
	 * Aggregate messages into batches as they are received.
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Aggregates messages into batches as they are received. Batches may
		 * be chunked either by an absolute size and/or a timeout since the
		 * first message was received for the chunk.  Either a batch size or
		 * timeout must be specified.
		 *
		 * @param {string} [name] the name to register the aggregator as
		 * @param {number} [opts.batch=0] absolute size of a chunk. If <=0,
		 *   batch size is not a factor
		 * @param {number} [opts.timeout=0] number of milliseconds since the
		 *   first message arrived to queue the chunk. If <=0, timeout is not a
		 *   factor
		 * @param {string|Channel} [opts.output] the channel to post the
		 *   aggregated messages to
		 * @param {string|Channel} [opts.input] the channel to receive message
		 *   from
		 * @param {string|Channel} [opts.error] channel to receive errors
		 * @returns the aggregator
		 * @throws on invalid configuration, batch size or timeout is required
		 */
		msgs.prototype.batchingAggregator = msgs.utils.optionalName(function batchingAggregator(name, opts) {
			var timeout, batch;

			batch = [];
			opts = opts || {};
			opts.batch = opts.batch || 0;
			opts.timeout = opts.timeout || 0;

			if (opts.batch <= 0 && opts.timeout <= 0) {
				throw new Error('Invalid configuration: batch size or timeout must be defined');
			}

			function releaseHelper(release) {
				release(batch);
				batch = [];
				clearTimeout(timeout);
				timeout = undef;
			}

			return this.aggregator(name, function (message, release) {
				batch.push(message.payload);
				if (opts.batch > 0 && batch.length >= opts.batch) {
					releaseHelper(release);
				}
				else if (!timeout && opts.timeout > 0) {
					timeout = setTimeout(function () {
						releaseHelper(release);
					}, opts.timeout);
				}
			}, opts);
		});

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
