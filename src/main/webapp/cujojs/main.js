define(function (require) {

	var bus, SockJS, ApplicationModel, PortfolioModel, TradeModel, socket;

	bus = require('msgs').bus();
	SockJS = require('sockjs');

	ApplicationModel = require('./app');
	PortfolioModel = require('./portfolio');
	TradeModel = require('./trade');

	require('msgs/adapters/webSocket');
	require('msgs/channels/bridges/stomp');

	require('css!common/portfolio.css');

//	bus.logger({ prefix: 'dead', tap: 'deadLetterChannel' });

	socket = new SockJS('/spring-websocket-portfolio/portfolio');
	socket.addEventListener('open', function () {
		var bridge, portfolio, trade, app;

		bridge = bus.stompWebSocketBridge('remote', socket, { ack: 'client' });

		portfolio = new PortfolioModel();
		trade = new TradeModel(bus.inboundAdapter('remote!/app/trade', JSON.stringify.bind(JSON)));
		app = new ApplicationModel(portfolio, trade, logout);

		bridge.controlBus.on('connected', function (connected, headers) {
			console.log('Connected', headers);

			app.username(headers['user-name']);

			bus.on('remote!/app/positions', function(positions) {
				portfolio().loadPositions(JSON.parse(positions));
			});
			bus.on('remote!/topic/price.stock.*', function(quote) {
				portfolio().processQuote(JSON.parse(quote));
			});
			bus.on('remote!/user/queue/position-updates', function(position) {
				app.pushNotification("Position update " + position);
				portfolio().updatePosition(JSON.parse(position));
			});
			bus.on('remote!/user/queue/errors', function(error) {
				app.pushNotification("Error " + error);
			});
		});
		bridge.controlBus.on('error', function (error) {
			console.log('STOMP protocol error ' + error);
		});

		app.pushNotification('Trade results take a 2-3 second simulated delay. Notifications will appear.');

		function logout() {
			bus.destroy();
			window.location.href = '../logout.html';
		}

	});

});
