package org.springframework.samples.portfolio.web;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.Test;
import org.springframework.samples.portfolio.service.Quote;
import org.springframework.samples.portfolio.service.QuoteService;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by achmad on 30/05/16.
 */
public class StreamGenerator {
    private final ConnectionFactory factory = new ConnectionFactory();
    private final Gson gson = new Gson();
    private final QuoteService.StockQuoteGenerator quoteGenerator = new QuoteService.StockQuoteGenerator();

    @Test
    public void generate_price_update() throws IOException, TimeoutException, InterruptedException {
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        while (true) {
            for (Quote quote : quoteGenerator.generateQuotes())
                channel.basicPublish("amq.topic", "price.stock." + quote.getTicker(), null, gson.toJson(quote).getBytes());
            Thread.sleep(100);
        }
    }
}
