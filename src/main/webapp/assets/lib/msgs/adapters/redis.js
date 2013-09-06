/*
 * Copyright 2012-2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Adapters for Redis pub/sub messaging.
	 *
	 * Implemented assuming a node-redis client.
	 * https://github.com/mranney/node_redis
	 */
	define(function (require) {

		var msgs = require('..');

		/**
		 * Post messages from Redis to this channel
		 *
		 * @param {RedisClient} client the Redis client subscribe with
		 * @param {string} topic the remote Redis channel to subscribe to
		 * @param {string|Channel} opts.output channel inbound Redis messages
		 *   are sent to
		 */
		msgs.prototype.inboundRedisAdapter = function inboundRedisAdapter(client, topic, opts) {
			client.on('message', this.inboundAdapter(opts.output, function (channel, message) {
				// make sure it's the channel we care about
				if (channel === topic) {
					return message;
				}
			}));
			client.subscribe(topic);
		};

		/**
		 * Create a handler that publishes messages to Redis
		 *
		 * @param {string} name name to register the adapter as
		 * @param {RedisClient} client the Redis client to publish with
		 * @param {string} topic the remote Redis channel to publish to
		 * @param {string|Channel} [opts.input] channel outbound Redis messages
		 *   are sent from
		 * @param {string|Channel} [opts.error] channel exceptions from the
		 *   Redis client are sent to
		 * @returns {Handler} the handler for this adapter
		 */
		msgs.prototype.outboundRedisAdapter = msgs.utils.optionalName(function outboundRedisAdapter(name, client, topic, opts) {
			var handler;

			handler = this.outboundAdapter(name, function (payload) {
				client.publish(topic, payload);
			}, opts);

			if (opts.error) {
				client.on('error', this.inboundAdapter(opts.error));
			}

			return handler;
		});

		/**
		 * Bridges channels and Redis Pub/Sub. Any exceptions are put on the error
		 * channel.
		 *
		 * A client factory must be provided instead of a concrete client as
		 * the same client cannot be used for publishing and subscribing.
		 *
		 * @param {Function} clientFactory function that returns a new Redis
		 *   client
		 * @param {string} topic the remote Redis channel to subscribe to
		 * @param {string|Channel} [opts.input] channel outbound Redis messages
		 *   are sent from
		 * @param {string|Channel} [opts.output] channel inbound Redis messages
		 *   are sent to
		 * @param {string|Channel} [opts.error] channel for thrown exceptions
		 */
		msgs.prototype.redisGateway = function redisGateway(clientFactory, topic, opts) {
			if (opts.output) {
				this.inboundRedisAdapter(clientFactory(), topic, opts);
			}
			if (opts.input) {
				this.outboundRedisAdapter(clientFactory(), topic, opts);
			}
		};

		return msgs;

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
