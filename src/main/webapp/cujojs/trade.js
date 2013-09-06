define(['bootstrap', 'knockout'], function ($, ko) {

	return TradeModel;

	function TradeModel(executeTrade) {
		var self = this;

		self.action = ko.observable();
		self.sharesToTrade = ko.observable(0);
		self.currentRow = ko.observable({});
		self.error = ko.observable('');
		self.suppressValidation = ko.observable(false);

		self.showBuy  = function (row) { self.showModal('Buy', row); };
		self.showSell = function (row) { self.showModal('Sell', row); };

		self.showModal = function (action, row) {
			self.action(action);
			self.sharesToTrade(0);
			self.currentRow(row);
			self.error('');
			self.suppressValidation(false);
			$('#trade-dialog').modal();
		};

		$('#trade-dialog').on('shown', function () {
			var input = $('#trade-dialog input');
			input.focus();
			input.select();
		});

		var validateShares = function () {
			if (isNaN(self.sharesToTrade()) || (self.sharesToTrade() < 1)) {
				self.error('Invalid number');
				return false;
			}
			if ((self.action() === 'Sell') && (self.sharesToTrade() > self.currentRow().shares())) {
				self.error('Not enough shares');
				return false;
			}
			return true;
		};

		self.executeTrade = function () {
			if (!self.suppressValidation() && !validateShares()) {
				return;
			}
			var trade = {
				"action" : self.action(),
				"ticker" : self.currentRow().ticker,
				"shares" : self.sharesToTrade()
			};
			console.log(trade);
			executeTrade(trade);
			$('#trade-dialog').modal('hide');
		};

		return ko.observable(self);
	}

});
