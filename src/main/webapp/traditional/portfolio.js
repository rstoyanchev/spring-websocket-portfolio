
function ApplicationModel(stompClient) {
  var self = this;

  self.username = ko.observable();
  self.portfolio = ko.observable(new PortfolioModel());
  self.trade = ko.observable(new TradeModel(stompClient));
  self.notifications = ko.observableArray();

  self.connect = function() {
      stompClient.connect({}, function(frame) {

      console.log('Connected ' + frame);
      self.username(frame.headers['user-name']);

      stompClient.subscribe("/app/positions", function(message) {
        self.portfolio().loadPositions(JSON.parse(message.body));
      });
      stompClient.subscribe("/topic/price.stock.*", function(message) {
        self.portfolio().processQuote(JSON.parse(message.body));
      });
      stompClient.subscribe("/user/queue/position-updates", function(message) {
        self.pushNotification("Position update " + message.body);
        self.portfolio().updatePosition(JSON.parse(message.body));
      });
      stompClient.subscribe("/user/queue/errors", function(message) {
        self.pushNotification("Error " + message.body);
      });
    }, function(error) {
      console.log("STOMP protocol error " + error);
    });
  }

  self.pushNotification = function(text) {
    self.notifications.push({notification: text});
    if (self.notifications().length > 5) {
      self.notifications.shift();
    }
  }

  self.logout = function() {
    stompClient.disconnect();
    window.location.href = "../logout.html";
  }
}

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
};

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
};

function TradeModel(stompClient) {
  var self = this;

  self.action = ko.observable();
  self.sharesToTrade = ko.observable(0);
  self.currentRow = ko.observable({});
  self.error = ko.observable('');
  self.suppressValidation = ko.observable(false);

  self.showBuy  = function(row) { self.showModal('Buy', row) }
  self.showSell = function(row) { self.showModal('Sell', row) }

  self.showModal = function(action, row) {
    self.action(action);
    self.sharesToTrade(0);
    self.currentRow(row);
    self.error('');
    self.suppressValidation(false);
    $('#trade-dialog').modal();
  }

  $('#trade-dialog').on('shown', function () {
    var input = $('#trade-dialog input');
    input.focus();
    input.select();
  })
  
  var validateShares = function() {
      if (isNaN(self.sharesToTrade()) || (self.sharesToTrade() < 1)) {
        self.error('Invalid number');
        return false;
      }
      if ((self.action() === 'Sell') && (self.sharesToTrade() > self.currentRow().shares())) {
        self.error('Not enough shares');
        return false;
      }
      return true;
  }

  self.executeTrade = function() {
    if (!self.suppressValidation() && !validateShares()) {
      return;
    }
    var trade = {
        "action" : self.action(),
        "ticker" : self.currentRow().ticker,
        "shares" : self.sharesToTrade()
      };
    console.log(trade);
    stompClient.send("/app/trade", {}, JSON.stringify(trade));
    $('#trade-dialog').modal('hide');
  }
}
