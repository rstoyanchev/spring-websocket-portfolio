/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var LF = '\x0A',
	    NULL = '\x00',
	    HEADER_SEPARAROR = ':',
	    LINE_SPLITTER = /\r?\n/,
	    HEADER_BODY_SPLITTER = /(\r?\n){2}/,
	    undef;

	/**
	 * Translate an internal message to/from a STOMP message
	 */
	define(function (require) {

		var msgs = require('../..');

		/**
		 * Create a STOMP frame translator
		 *
		 * @param {MessageBus} bus message bus used to create new messages
		 * @returns STOMP translator
		 */
		return function (bus) {
			if (!msgs.isBus(bus)) {
				throw new Error('A msgs bus must be provided');
			}

			return {

				/**
				 * Build a STOMP frame from a message
				 *
				 * @param {Message} message message to translate
				 * @returns {string} built STOMP frame
				 */
				build: function build(message) {
					var headers, body, frame;

					headers = message.headers;
					body = message.payload;

					frame = headers.command.toUpperCase();

					Object.keys(message.headers).forEach(function (name) {
						var type = typeof headers[name];
						if (name !== 'command' && (type === 'string' || type === 'number')) {
							// assumes header name and value do not contain LF
							frame += LF + name + HEADER_SEPARAROR + headers[name];
						}
					});

					if (body && body.length && !('content-length' in message.headers)) {
						frame += LF + 'content-length' + HEADER_SEPARAROR + body.length;
					}

					frame += LF + LF;
					if (body) {
						// assumes body does not contain NULL
						frame += body;
					}
					frame += NULL;

					return frame;
				},

				/**
				 * Parse a STOMP frame into a message
				 *
				 * @param {string} frame STOMP frame to translate
				 * @returns {Message} translated message
				 */
				parse: function parse(frame) {
					var headers = {}, headerArr, command, body, split;

					try {
						split = frame.match(HEADER_BODY_SPLITTER);
						body = frame.substr(split.index + split[0].length).split(NULL)[0];
						headerArr = frame.substr(0, split.index).split(LINE_SPLITTER);
						command = headerArr.shift();

						headerArr.forEach(function (header) {
							var components = header.split(HEADER_SEPARAROR);
							headers[components.shift()] = components.join(HEADER_SEPARAROR);
						});
						headers.command = command;

						if (!body) {
							body = undef;
						}

						return bus._message(body, headers);
					}
					catch (e) {
						return bus._message(frame);
					}
				},

				/**
				 * Build a heart beat
				 *
				 * @returns {string} STOMP heart beat frame
				 */
				buildHeartBeatFrame: function () {
					return bus._message(LF);
				},

				/**
				 * Build a CONNECT frame
				 *
				 * @param {string} opts.host virtual host of the STOMP broker
				 * @param {string} [opts.login] user identifier used to authenticate
				 * @param {string} [opts.passcode] password used to authenticate
				 * @returns {string} STOMP CONNECT frame
				 */
				buildConnectFrame: function buildConnectFrame(opts) {
					var headers = {
						command: 'CONNECT',
						'accept-version': '1.1,1.2',
						host: opts.host,
						'heart-beat': '0,0'
					};
					if (opts.login) {
						headers.login = opts.login;
						headers.passcode = opts.passcode;
					}
					return this.build(bus._message(undef, headers));
				},

				/**
				 * Build a SEND frame
				 *
				 * @param {Message} message the message to send
				 * @param {string} [receipt] require a RECEIPT frame with this key
				 * @returns {string} STOMP SEND frame
				 */
				buildSendFrame: function buildSendFrame(message, receipt) {
					var headers = {
						command: 'SEND',
						destination: message.headers.topic,
						'content-type': message.headers.contentType || undef,
						topic: undef,
						contentType: undef
					};
					if (receipt) {
						headers.receipt = receipt;
					}
					return this.build(message.mixin(headers));
				},

				/**
				 * Build a SUBSCRIBE frame
				 *
				 * @param {string} destination the destination to subscribe to
				 * @param {string} id identifier for the subscription
				 * @param {string} [ack] acknowledgment policy for the subscription
				 * @param {string} [receipt] require a RECEIPT frame with this key
				 * @returns {string} STOMP SUBSCRIBE frame
				 */
				buildSubscribeFrame: function buildSubscribeFrame(destination, id, ack, receipt) {
					var headers = {
						command: 'SUBSCRIBE',
						destination: destination,
						id: id,
						ack: ack || 'auto'
					};
					if (receipt) {
						headers.receipt = receipt;
					}
					return this.build(bus._message(undef, headers));
				},

				/**
				 * Build a UNSUBSCRIBE frame
				 *
				 * @param {string} id identifier for the subscription
				 * @param {string} [receipt] require a RECEIPT frame with this key
				 * @returns {string} STOMP UNSUBSCRIBE frame
				 */
				buildUnsubscribeFrame: function buildUnsubscribeFrame(id, receipt) {
					var headers = {
						command: 'UNSUBSCRIBE',
						id: id
					};
					if (receipt) {
						headers.receipt = receipt;
					}
					return this.build(bus._message(undef, headers));
				},

				/**
				 * Build an ACK frame
				 *
				 * @param {string} id identifier for the message being acknowledged
				 * @returns {string} STOMP ACK frame
				 */
				buildAckFrame: function buildAckFrame(id) {
					var headers = {
						command: 'ACK',
						id: id
					};
					return this.build(bus._message(undef, headers));
				},

				/**
				 * Build a NACK frame
				 *
				 * @param {string} id identifier for the message being not-acknowledged
				 * @returns {string} STOMP NACK frame
				 */
				buildNackFrame: function buildNackFrame(id) {
					var headers = {
						command: 'NACK',
						id: id
					};
					return this.build(bus._message(undef, headers));
				},

				/**
				 * Build a DISCONNECT frame
				 *
				 * @param {string} [receipt] require a RECEIPT frame with this key
				 * @returns {string} STOMP DISCONNECT frame
				 */
				buildDisconnectFrame: function buildDisconnectFrame(receipt) {
					var headers = {
						command: 'DISCONNECT'
					};
					if (receipt) {
						headers.receipt = receipt;
					}
					return this.build(bus._message(undef, headers));
				}

			};

		};

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
