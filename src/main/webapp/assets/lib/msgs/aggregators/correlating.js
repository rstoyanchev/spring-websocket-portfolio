/*
 * Copyright 2012 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Aggregate messages by correlationId and sequenceNumber
	 */
	define(function (require) {

		var msgs = require('..');

		function sequenceNumberComparator(a, b) {
			return a.headers.sequenceNumber - b.headers.sequenceNumber;
		}

		/**
		 * Aggregates messages that were previously split by a splitter. Once
		 * all of the messages from the splitter are received a new message is
		 * created whose payload is an array of the split messages in order.
		 *
		 * @param {string} [name] the name to register the aggregator as
		 * @param {string|Channel} [opts.output] the channel to post the
		 *   aggregated messages to
		 * @param {string|Channel} [opts.input] the channel to receive message
		 *   from
		 * @param {string|Channel} [opts.error] channel to receive errors
		 * @returns the aggregator
		 */
		msgs.prototype.correlatingAggregator = function correlatingAggregator(name, opts) {
			var buckets;

			// optionalName won't work since output channel may be a string
			if (arguments.length < 2) {
				opts = name;
				name = '';
			}

			buckets = {};

			return this.aggregator(name, function (message, release) {
				var correlationId, bucket;
				correlationId = message.headers.correlationId;
				if (!correlationId) {
					return;
				}
				bucket = buckets[correlationId] = buckets[correlationId] || [];
				bucket.push(message);
				if (bucket.length >= message.headers.sequenceSize) {
					bucket.sort(sequenceNumberComparator);
					release(bucket);
					delete buckets[correlationId];
				}
			}, opts);
		};

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
