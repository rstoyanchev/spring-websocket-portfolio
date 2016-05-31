angular.module('springPortfolio.services', [])
    //.constant('sockJsProtocols', ["xhr-streaming", "xhr-polling"]) // only allow XHR protocols
    .constant('sockJsProtocols', ["websocket"])
    .factory('StompClient', ['sockJsProtocols', '$q', function (sockJsProtocols, $q) {
        var stompClient;
        var wrappedSocket = {
            init: function (url) {
                if (sockJsProtocols.length > 0) {
                    stompClient = Stomp.over(new SockJS(url, null, {transports: sockJsProtocols}));
                }
                else {
                    stompClient = Stomp.over(new SockJS(url));
                }
            },
            connect: function () {
                return $q(function (resolve, reject) {
                    if (!stompClient) {
                        reject("STOMP client not created");
                    } else {
                        stompClient.connect({}, function (frame) {
                            resolve(frame);
                        }, function (error) {
                            reject("STOMP protocol error " + error);
                        });
                    }
                });
            },
            disconnect: function() {
                stompClient.disconnect();
            },
            subscribe: function (destination) {
                var deferred = $q.defer();
                if (!stompClient) {
                    deferred.reject("STOMP client not created");
                } else {
                    stompClient.subscribe(destination, function (message) {
                        deferred.notify(JSON.parse(message.body));
                    });
                }
                return deferred.promise;
            },
            subscribeSingle: function (destination) {
                return $q(function (resolve, reject) {
                    if (!stompClient) {
                        reject("STOMP client not created");
                    } else {
                        stompClient.subscribe(destination, function (message) {
                            resolve(JSON.parse(message.body));
                        });
                    }
                });
            },
            send: function (destination, headers, object) {
                stompClient.send(destination, headers, object);
            }
        };
        return wrappedSocket;
    }])
    .factory('TradeService', ['StompClient', '$q', function (stompClient, $q) {

        return {
            connect: function (url) {
                stompClient.init(url);
                return stompClient.connect().then(function (frame) {
                    return frame.headers['user-name'];
                });
            },
            disconnect: function() {
                stompClient.disconnect();
            },
            loadPositions: function() {
                return stompClient.subscribeSingle("/app/positions");
            },
            fetchQuoteStream: function () {
                return stompClient.subscribe("/topic/price.stock.*");
            },
            subscribeEventStream: function () {
                return stompClient.subscribe("/topic/session.event.abc");
            },
            fetchPositionUpdateStream: function () {
                return stompClient.subscribe("/topic/session.update.abc");
            },
            fetchErrorStream: function () {
                return stompClient.subscribe("/topic/session.errors.abc");
            },
            sendTradeOrder: function(tradeOrder) {
                return stompClient.send("/app/trade", {}, JSON.stringify(tradeOrder));
            }
        };

    }])
    .config(function ($provide) {
        $provide.decorator('$rootScope', ['$delegate', function ($delegate) {
            Object.defineProperty($delegate.constructor.prototype, '$bus', {
                get: function () {
                    var self = this;

                    return {
                        subscribe: function () {
                            var sub = postal.subscribe.apply(postal, arguments);

                            self.$on('$destroy',
                                function () {
                                    sub.unsubscribe();
                                });
                        },
                        channel: postal.channel,
                        publish: postal.publish.bind(postal)
                    };
                },
                enumerable: false
            });

            return $delegate;
        }]);
    });
