define(['knockout'], function (ko) {

	return PortfolioModel;

	function PortfolioModel() {
		var self = this;

		self.rows = ko.observableArray();

		self.totalShares = ko.computed(function() {
			var result = 0;
			for ( var i = 0; i < self.rows().length; i++) {
				result += self.rows()[i].shares();
			}
			return result;
		});

		self.totalValue = ko.computed(function() {
			var result = 0;
			for ( var i = 0; i < self.rows().length; i++) {
				result += self.rows()[i].value();
			}
			return "$" + result.toFixed(2);
		});

		var rowLookup = {};

		self.loadPositions = function(positions) {
			for ( var i = 0; i < positions.length; i++) {
				var row = new PortfolioRow(positions[i]);
				self.rows.push(row);
				rowLookup[row.ticker] = row;
			}
		};

		self.processQuote = function(quote) {
			if (rowLookup.hasOwnProperty(quote.ticker)) {
				rowLookup[quote.ticker].updatePrice(quote.price);
			}
		};

		self.updatePosition = function(position) {
			rowLookup[position.ticker].shares(position.shares);
		};

		return ko.observable(self);
	}

	function PortfolioRow(data) {
		var self = this;

		self.company = data.company;
		self.ticker = data.ticker;
		self.price = ko.observable(data.price);
		self.formattedPrice = ko.computed(function() { return "$" + self.price().toFixed(2); });
		self.change = ko.observable(0);
		self.arrow = ko.observable();
		self.shares = ko.observable(data.shares);
		self.value = ko.computed(function() { return (self.price() * self.shares()); });
		self.formattedValue = ko.computed(function() { return "$" + self.value().toFixed(2); });

		self.updatePrice = function(newPrice) {
			var delta = (newPrice - self.price()).toFixed(2);
			self.arrow((delta < 0) ? '<i class="icon-arrow-down"></i>' : '<i class="icon-arrow-up"></i>');
			self.change((delta / self.price() * 100).toFixed(2));
			self.price(newPrice);
		};
	}

});
