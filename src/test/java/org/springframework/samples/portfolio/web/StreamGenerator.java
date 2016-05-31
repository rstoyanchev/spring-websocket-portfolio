package org.springframework.samples.portfolio.web;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.portfolio.service.Quote;
import org.springframework.samples.portfolio.service.QuoteService;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Generate faster rate of data stream.
 */
public class StreamGenerator {
    private final ConnectionFactory factory = new ConnectionFactory();
    private final Gson gson = new Gson();
    private final QuoteService.StockQuoteGenerator quoteGenerator = new QuoteService.StockQuoteGenerator();
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Test
    public void generate_price_update() throws IOException, TimeoutException, InterruptedException {
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();
        while (true) {
            for (Quote quote : quoteGenerator.generateQuotes())
                channel.basicPublish("amq.topic", "session.event.abc", null, gson.toJson(quote).getBytes());
            Thread.sleep(100);
            log.info("Quotes sent.");
        }
    }
}
