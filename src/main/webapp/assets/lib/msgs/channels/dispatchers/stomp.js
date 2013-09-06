/*
 * Copyright 2013 the original author or authors
 * @license MIT, see LICENSE.txt for details
 *
 * @author Scott Andrews
 */

(function (define) {
	'use strict';

	var undef;

	/**
	 * STOMP dispatcher. Subscriptions are managed via a remote STOMP server.
	 *
	 * Compatible with STOMP 1.1 and 1.2
	 * @see http://stomp.github.io/stomp-specification-1.2.html
	 */
	define(function (require) {

		var msgs, stompTranslator, broadcastDispatcher, exchangeDispatcher;

		msgs = require('../..');
		stompTranslator = require('../translators/stomp');
		broadcastDispatcher = require('./broadcast');
		exchangeDispatcher = require('./exchange');

		/**
		 * Dispatcher to handle STOMP client messaging
		 *
		 * @param {MessageBus} bus message bus the dispatcher is created within
		 * @param {string|Channel} opts.input channel to forward to the remote system
		 * @param {string|Channel} opts.output channel that exposes messages from the
		 *   remote system
		 * @param {string|Channel} [opts.error] channel to receive errors
		 * @param {string} opts.host virtual host that the client is connecting to
		 * @param {string} [opts.login] user identifier used to authenticate against
		 *   a secured STOMP server
		 * @param {string} [opts.passcode] password used to authenticate against a
		 *   secured STOMP server
		 * @param {string} [opts.ack='auto'] ack acknowledgment policy for received
		 *   messages for a subscription. May be one of 'auto', 'client', or
		 *   'client-individual'.
		 */
		return function stompDispatcher(bus, opts) {
			var translator, dispatcher, subscriptions, destinations, receipts, ready, controlBus, counter, handlerResolver;

			translator = stompTranslator(bus);
			dispatcher = {};
			subscriptions = {};
			destinations = {};
			receipts = {};
			counter = bus.utils.counter();
			handlerResolver = bus.resolveHandler.bind(bus);

			// normalize ack policy, default to 'auto'
			opts.ack = opts.ack === 'client' || opts.ack === 'client-individual' ? opts.ack : 'auto';

			// create a control bus with channels for notifications of internal events
			controlBus = bus.bus();
			controlBus._channel('connected', broadcastDispatcher());
			controlBus._channel('disconnected', broadcastDispatcher());
			controlBus._channel('subscribed', exchangeDispatcher(exchangeDispatcher.matchers.literal, broadcastDispatcher));
			controlBus._channel('unsubscribed', exchangeDispatcher(exchangeDispatcher.matchers.literal, broadcastDispatcher));
			controlBus._channel('toServer', broadcastDispatcher());
			controlBus._channel('fromServer', broadcastDispatcher());
			controlBus._channel('error', broadcastDispatcher());

			// expose raw STOMP traffic via the control bus
			bus.tap(opts.output, controlBus.forward('toServer'));
			bus.tap(opts.input, controlBus.forward('fromServer'));

			// normalize opts.error to send errors to the control bus
			if (opts.error) {
				bus.tap(opts.error, controlBus.forward('error'));
			}
			else {
				opts.error = controlBus.resolveChannel('error');
			}

			/**
			 * Subscribe a handler to a STOMP destination.
			 *
			 * - add the handler from the local subscription dispatcher
			 * - send a SUBSCRIBE command to the server if first local subscription
			 * - notify the 'subscribed' channel on the control bus with the
			 *   destination as the topic and the handler as a payload
			 *
			 * Consolidates multiple subscriptions for the same destination as a single
			 * server-side subscription.
			 */
			dispatcher.subscribe = function subscribe(destination, handler) {
				var subscriptionId, receiptId;

				if (destinations.hasOwnProperty(destination)) {
					// already subscribed on the server
					subscriptionId = destinations[destination];
					receiptId = subscriptions[subscriptionId].subscriptionReceiptId;
					subscriptions[subscriptionId].subscribe(handler);
					if (receipts[receiptId]) {
						receipts[receiptId].push(function () {
							controlBus.send('subscribed!' + destination, handler);
						});
					}
					else {
						controlBus.send('subscribed!' + destination, handler);
					}
					return;
				}

				subscriptionId = counter();
				destinations[destination] = subscriptionId;
				subscriptions[subscriptionId] = broadcastDispatcher();
				subscriptions[subscriptionId].subscribe(handler);

				receiptId = subscriptions[subscriptionId].subscriptionReceiptId = counter();
				receipts[receiptId] = [function () {
					controlBus.send('subscribed!' + destination, handler);
				}];

				sendCommand(translator.buildSubscribeFrame(destination, subscriptionId, opts.ack, receiptId));
			};

			/**
			 * Unsubscribe a handler from a STOMP destination.
			 *
			 * - remove the handler from the local subscription dispatcher
			 * - send an UNSUBSCRIBE command to the server if no remaining local
			 *   subscriptions
			 * - notify the 'unsubscribed' channel on the control bus with the
			 *   destination as the topic and the handler as a payload
			 *
			 * Consolidates multiple subscriptions for the same destination as a single
			 * server-side subscription.
			 */
			dispatcher.unsubscribe = function unsubscribe(destination, handler) {
				var subscriptionId, receiptId, dispatcher;
				if (!destinations.hasOwnProperty(destination)) {
					// not subscribed
					return;
				}
				subscriptionId = destinations[destination];
				dispatcher = subscriptions[subscriptionId];
				dispatcher.unsubscribe(handler);
				if (dispatcher.subscribed()) {
					controlBus.send('unsubscribed!' + destination, handler);
					return;
				}
				// remove dispatcher if no handlers remain
				dispatcher.destroy();
				delete subscriptions[subscriptionId];
				delete destinations[destination];

				receiptId = counter();
				receipts[receiptId] = [function () {
					controlBus.send('unsubscribed!' + destination, handler);
				}];

				sendCommand(translator.buildUnsubscribeFrame(subscriptionId, receiptId));
			};

			/**
			 * Dispatch a message to the STOMP server
			 */
			dispatcher.dispatch = function dispatch(message) {
				sendCommand(translator.buildSendFrame(message));
				return true;
			};

			/**
			 * Destroy the dispatcher:
			 * - attempts to send a DISCONNECT command to the server
			 * - attempts to notify the 'disconnected' channel on the control bus
			 * - destroy subscription dispatchers
			 * - destroy the control bus
			 * - cleans up any lingering state
			 */
			dispatcher.destroy = function destroy() {
				var disconnectFrame;
				for (var dispatcher in subscriptions) {
					if (subscriptions.hasOwnProperty(dispatcher)) {
						subscriptions[dispatcher].destroy();
					}
				}

				subscriptions = undef;
				destinations = undef;
				receipts = undef;

				try {
					if (isReady()) {
						disconnectFrame = translator.buildDisconnectFrame();
						sendCommand(disconnectFrame);
						ready = false;
						controlBus.send('disconnected', disconnectFrame);
					}
				}
				catch (e) {
					// squelch, disconnect is a good faith effort
					// bridge target may have already been destroyed
				}
				finally {
					controlBus.destroy();
				}
			};

			/**
			 * Send a command to the STOMP server.
			 *
			 * If the connection is not ready, the command will be sent to the error
			 * channel and an error thrown.
			 *
			 * @param {string|Message} message the command to send
			 */
			function sendCommand(message) {
				if (isReady()) {
					bus.send(opts.output, message);
				}
				else {
					bus.send(opts.error, message, { error: 'STOMP connection is not ready' });
					throw new Error('STOMP connection is not ready');
				}
			}

			// route server commands
			bus._handler(undef, function (message) {
				var frame, command;
				frame = translator.parse(message.payload);
				command = frame.headers.command;
				if (command === 'MESSAGE') {
					handleMessageFrame(frame);
				}
				else if (command === 'RECEIPT') {
					handleReceiptFrame(frame);
				}
				else if (command === 'ERROR') {
					handleErrorFrame(frame);
				}
				else if (command === 'CONNECTED') {
					handleConnectedFrame(frame);
				}
				else if (command) {
					// unknown command
					throw new Error('Unknown STOMP command: ' +  command);
				}
			}, bus.noopChannel, opts.input, opts.error);


			/**
			 * Process a MESSAGE command:
			 * - dispatch the message to it's subscribers
			 * - ACK/NACK the message, if needed
			 *
			 * @param {Message} message the receipt message
			 */
			function handleMessageFrame(message) {
				var handled, id;
				id = message.headers['message-id'];
				try {
					handled = subscriptions[message.headers.subscription].dispatch(message, handlerResolver);
				}
				catch (e) {
					handled = false;
					throw e;
				}
				finally {
					if (id && opts.ack !== 'auto') {
						sendCommand(translator[handled ? 'buildAckFrame' : 'buildNackFrame'](id));
					}
				}
			}

			/**
			 * Process a RECEIPT command:
			 * - fire the receipt's callback
			 * - remove callback from receipt store
			 *
			 * @param {Message} message the receipt message
			 */
			function handleReceiptFrame(message) {
				var receiptId = message.headers['receipt-id'];
				receipts[receiptId].forEach(function (receiptCallback) {
					receiptCallback(message);
				});
				delete receipts[receiptId];
			}

			/**
			 * Process a CONNECTED command:
			 * - forward the message to the 'connected' control bus channel
			 * - mark the connection as ready
			 *
			 * @param {Message} message the connected message
			 */
			function handleConnectedFrame(message) {
				ready = true;
				controlBus.send('connected', message);
			}

			/**
			 * Process an ERROR command:
			 * - forward the message to the 'disconnected' control bus channel
			 * - mark the connection as not ready
			 *
			 * @param {Message} message the connected message
			 */
			function handleErrorFrame(message) {
				ready = false;
				controlBus.send('disconnected', message);
			}

			/**
			 * @returns {boolean} true if the connection is able to execute commands
			 */
			function isReady() {
				return ready;
			}

			dispatcher.channelMixins = {
				subscribe: dispatcher.subscribe,
				unsubscribe: dispatcher.unsubscribe,
				destroy: dispatcher.destroy,
				controlBus: controlBus,
				isReady: isReady
			};

			// open the connection
			bus.send(opts.output, translator.buildConnectFrame(opts));

			return dispatcher;
		};

	});

}(
	typeof define === 'function' && define.amd ? define : function (factory) { module.exports = factory(require); }
	// Boilerplate for AMD and Node
));
