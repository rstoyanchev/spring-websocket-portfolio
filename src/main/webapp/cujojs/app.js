define(['knockout'], function (ko) {

	return ApplicationModel;

	function ApplicationModel(portfolio, trade, logout) {
		var self = this;

		self.portfolio = portfolio;
		self.trade = trade;
		self.username = ko.observable();
		self.notifications = ko.observableArray();

		self.pushNotification = function (text) {
			self.notifications.push({notification: text});
			if (self.notifications().length > 5) {
				self.notifications.shift();
			}
		};

		self.logout = logout;

		ko.applyBindings(self);
	}

});