/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	/**
	 * Plugin for wire.js
	 *
	 * @author Scott Andrews
	 */
	define(function (require) {

		var msgs, when, wireArray, wirePipeline;

		msgs = require('./gateways');
		when = require('when');
		wireArray = require('wire/lib/array');
		wirePipeline = require('wire/lib/pipeline');

		/**
		 * Resolve the context's implicit bus making it available for msgs use
		 * cases not directly supported by this plugin.
		 */
		function busResolverFactory(bus) {
			return function (resolver /*, name, refObj, wire */) {
				resolver.resolve(bus);
			};
		}

		/**
		 * Send message to a channel inside a wire expression using any common
		 * source, on/connect/aop/etc
		 */
		function inboundGatewayResolverFactory(bus) {
			return function (resolver, name /*, refObj, wire */) {
				var inboundGateway = bus.inboundGateway(name);
				resolver.resolve(inboundGateway);
			};
		}

		function normalizeChannelsConfig(config) {
			if (typeof config === 'string') {
				config = [config];
			}
			if (Array.isArray(config)) {
				config = { channel: config };
			}
			Object.keys(config).forEach(function (type) {
				if (!Array.isArray(config[type])) {
					config[type] = [config[type]];
				}
			});
			return config;
		}

		/**
		 * Create channels for the context's bus
		 */
		function channelsFactoryFactory(bus) {
			return function (resolver, spec /*, wire */) {
				try {
					var config = normalizeChannelsConfig(spec.options);
					Object.keys(config).forEach(function (type) {
						config[type].forEach(function (name) {
							bus[type](name);
						});
					});
					resolver.resolve(bus);
				}
				catch (e) {
					resolver.reject('Unable to define channels: ' + e.message);
				}
			};
		}

		/**
		 * Create outboundAdapters for wire components subscribing them to bus
		 * channels.
		 */
		function outboundAdapterFacetFactory(bus) {
			return function (resolver, facet, wire) {
				var config = facet.options;
				when.map(Object.keys(config), function (channel) {
					return when.map(wireArray.delegate(config[channel]), function (target) {
						return wirePipeline(facet, target, wire).then(function (func) {
							return bus.outboundAdapter(func, { input: channel });
						});
					});
				}).then(resolver.resolve, resolver.reject);
			};
		}

		return {

			/**
			 * msgs plugin for wire.
			 *
			 * Each instance of this plugin creates an implicit message bus that is used
			 * by the features of the plugin. The hierarchy of this bus, follows the
			 * hierarchy of the wire context. If there is a parent-child relationship
			 * between two wire contexts that both use this plugin, the message buses
			 * provided by the plugin will follow that same parent-child relationship.
			 */
			wire$plugin: function (/* options */) {

				// bus proxy that can be shared within the plugin
				// instance state is mixed in during plugin initialize phase
				var bus = Object.create(msgs.prototype);

				return {
					context: {
						initialize: function (resolver, wire) {
							wire.resolveRef('__msgs_bus').otherwise(function () {
								// no bus found in parent context, default to root bus
								return msgs;
							}).then(function (parent) {
								// add instance behavior to bus proxy
								msgs.utils.mixin(bus, parent.bus());
								// register bus for child contexts to find
								wire.addInstance(bus, '__msgs_bus');
								resolver.resolve();
							});
						},
						shutdown: function (resolver) {
							bus.destroy();
							resolver.resolve();
						}
					},
					resolvers: {
						bus: busResolverFactory(bus),
						channel: inboundGatewayResolverFactory(bus)
					},
					factories: {
						channels: channelsFactoryFactory(bus)
					},
					facets: {
						subscribe: {
							connect: outboundAdapterFacetFactory(bus)
						}
					}
				};

			}

		};

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
