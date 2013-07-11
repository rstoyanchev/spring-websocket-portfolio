
function PortfolioModel(client) {
	var self = this;

	self.portfolioRows = ko.observableArray();
	self.lookup = {};

	self.totalShares = ko.computed(function() {
		var result = 0;
		for ( var i = 0; i < self.portfolioRows().length; i++) {
			result += self.portfolioRows()[i].shares();
		}
		return result;
	});

	self.totalValue = ko.computed(function() {
		var result = 0;
		for ( var i = 0; i < self.portfolioRows().length; i++) {
			result += self.portfolioRows()[i].value();
		}
		return "$" + result.toFixed(2);
	});

	self.loadPositions = function(positions) {
		for ( var i = 0; i < positions.length; i++) {
			var row = new PortfolioRow(positions[i]);
			self.portfolioRows.push(row);
			self.lookup[row.ticker] = row;
		}
	};

	self.processQuote = function(quote) {
		if (self.lookup.hasOwnProperty(quote.ticker)) {
			self.lookup[quote.ticker].updatePrice(quote.price);
		}
	};

	self.trade = ko.observable(new TradeAction(client));
};

function PortfolioRow(position) {
	var self = this;

	self.company = position.company;
	self.ticker = position.ticker;
	self.price = ko.observable(position.price);
	self.formattedPrice = ko.computed(function() {
		return "$" + self.price().toFixed(2);
	});
	self.change = ko.observable(0);
	self.arrow = ko.observable();
	self.shares = ko.observable(position.shares);
	self.value = ko.computed(function() {
		return (self.price() * self.shares());
	});
	self.formattedValue = ko.computed(function() {
		return "$" + self.value().toFixed(2);
	});

	self.updatePrice = function(newPrice) {
		var delta = (newPrice - self.price()).toFixed(2);
		self.arrow((delta < 0) ? '<i class="icon-arrow-down"></i>' : '<i class="icon-arrow-up"></i>');
		self.change((delta / self.price() * 100).toFixed(2));
		self.price(newPrice);
	};
};

function TradeAction(client) {
	var self = this;

	self.action = ko.observable();
	self.selectedRow = ko.observable({});
	self.shares = ko.observable(0);

	self.showBuy = function(row) { 
		self.selectedRow(row);
		self.action('Buy');
		$('#tradeDialog').modal();
	};

	self.showSell = function(row) { 
		self.selectedRow(row);
		self.action('Sell');
		$('#tradeDialog').modal();
	};

	self.executeTrade = function() {
		var request = {
				"ticker" : self.selectedRow().ticker,
				"shares" : self.shares(),
				"action" : self.action()
			};
		console.log(request);
		client.send("/tradeRequest", {}, JSON.stringify(request));
		$('#tradeDialog').modal('hide');
	}
}
