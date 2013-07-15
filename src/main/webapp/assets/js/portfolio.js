
function ApplicationModel(stompClient) {
  var self = this;

  self.username = ko.observable();
  self.portfolio = ko.observable(new PortfolioModel());
  self.trade = ko.observable(new TradeModel(stompClient));

  self.connect = function() {
    stompClient.connect('', '', function(frame) {

        console.log('Connected ' + frame);
        var userName = frame.headers['user-name'];
        var queueSuffix = frame.headers['queue-suffix'];

        self.username(userName);

        stompClient.subscribe("/positions", function(message) {
          self.portfolio().loadPositions(JSON.parse(message.body));
        });
        stompClient.subscribe("/topic/stocks.PRICE.STOCK.NASDAQ.*", function(message) {
          self.portfolio().processQuote(JSON.parse(message.body));
        });
        stompClient.subscribe("/queue/trade-confirmation/" + queueSuffix, function(message) {
          console.log("Trade result" + message.body);
        });
      }, function(error) {
        console.log('error ' + error);
      });
  }


  self.logout = function() {
    stompClient.disconnect();
    window.location.href = './logout.html';
  }
}

function PortfolioModel() {
  var self = this;

  self.rows = ko.observableArray();
  self.rowLookup = {};

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

  self.loadPositions = function(positions) {
    for ( var i = 0; i < positions.length; i++) {
      var row = new PortfolioRow(positions[i]);
      self.rows.push(row);
      self.rowLookup[row.ticker] = row;
    }
  };

  self.processQuote = function(quote) {
    if (self.rowLookup.hasOwnProperty(quote.ticker)) {
      self.rowLookup[quote.ticker].updatePrice(quote.price);
    }
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
  self.selectedRow = ko.observable({});
  self.shares = ko.observable(0);

  self.showBuy  = function(row) { self.showModal(row, 'Buy') }
  self.showSell = function(row) { self.showModal(row, 'Sell') }

  self.showModal = function(row, action) {
    self.selectedRow(row);
    self.action(action);
    self.shares(0);
    $('#trade-dialog').modal();
  }

  $('#trade-dialog').on('shown', function () {
    var input = $('#trade-dialog input');
    input.focus();
    input.select();
  })

  self.executeTrade = function() {
    var trade = {
        "action" : self.action(),
        "ticker" : self.selectedRow().ticker,
        "shares" : self.shares()
      };
    console.log(trade);
    stompClient.send("/trade", {}, JSON.stringify(trade));
    $('#trade-dialog').modal('hide');
  }
}
