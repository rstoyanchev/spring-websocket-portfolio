angular.module('springPortfolio.controllers', ['ui.bootstrap'])
    .constant("buy", "Buy")
    .constant("sell", "Sell")
    .controller('PortfolioController',
    ['$scope', '$uibModal', 'TradeService',
    function ($scope, $uibModal, tradeService) {
        $scope.notifications = [];
        $scope.positions = {};

        var processQuote = function(quote) {
            var existing = $scope.positions[quote.ticker];
            if(existing) {
                existing.change = quote.price - existing.price;
                existing.price = quote.price;
            }
        };
        var udpatePosition = function(position) {
            var existing = $scope.positions[position.ticker];
            if(existing) {
                existing.shares = position.shares;
            }
        };
        var pushNotification = function(message) {
            $scope.notifications.unshift(message);
        };

        var validateTrade = function(trade) {
            if (isNaN(trade.shares) || (trade.shares < 1)) {
                $scope.notifications.push("Trade Error: Invalid number of shares");
                return false;
            }
            if ((trade.action === "Sell") && (trade.shares > $scope.positions[trade.ticker].shares)) {
                $scope.notifications.push("Trade Error: Not enough shares");
                return false;
            }
            return true;
        }

        $scope.openTradeModal = function (action, position) {
            var modalInstance = $uibModal.open({
                templateUrl: 'tradeModal.html',
                controller: 'TradeModalController',
                size: "sm",
                resolve: {
                    action: action,
                    position: position
                }
            });
            modalInstance.result.then(function (result) {
                var trade = {
                    "action" : result.action,
                    "ticker" : result.position.ticker,
                    "shares" : result.numberOfShares
                };
                if(validateTrade(trade)) {
                    tradeService.sendTradeOrder(trade);
                }
            });
        };

        $scope.logout = function() {
            tradeService.disconnect();
        };

        tradeService.connect("/spring-websocket-portfolio/portfolio")
            .then(function (username) {
                    $scope.username = username;
                    pushNotification("Trade results take a 2-3 second simulated delay. Notifications will appear.");
                    return tradeService.loadPositions();
                },
                function (error) {
                    pushNotification(error);
                })
            .then(function (positions) {
                positions.forEach(function(pos) {
                    $scope.positions[pos.ticker] = pos;
                });
                tradeService.fetchQuoteStream().then(null, null,
                    function(quote) {
                        processQuote(quote);
                    }
                );
                tradeService.fetchPositionUpdateStream().then(null, null,
                    function(position) {
                        udpatePosition(position);
                    }
                );
                tradeService.fetchErrorStream().then(null, null,
                    function (error) {
                        pushNotification(error);
                    }
                );
            });

    }])
    .controller('TradeModalController',
            ["$scope", "$uibModalInstance", "TradeService", "action", "position",
            function ($scope, $uibModalInstance, tradeService, action, position) {

        $scope.action = action;
        $scope.position = position;
        $scope.numberOfShares = 0;
        $scope.trade = function () {
            $uibModalInstance.close({action: action, position: position, numberOfShares: $scope.numberOfShares});
        };
        $scope.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    }])
    .filter('percent', ['$filter', function ($filter) {
        return function (input, total) {
            return $filter('number')(input / total * 100, 1) + '%';
        };
    }])
    .filter('totalPortfolioShares', [function () {
        return function (positions) {
            var total = 0;
            for(var ticker in positions) {
                total += positions[ticker].shares;
            }
            return total;
        };
    }])
    .filter('totalPortfolioValue', [function () {
        return function (positions) {
            var total = 0;
            for(var ticker in positions) {
                total += positions[ticker].price * positions[ticker].shares;
            }
            return total;
        };
    }]);