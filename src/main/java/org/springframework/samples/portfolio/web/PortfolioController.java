package org.springframework.samples.portfolio.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.portfolio.PortfolioPosition;
import org.springframework.stereotype.Controller;
import org.springframework.web.messaging.annotation.SubscribeEvent;
import org.springframework.web.messaging.support.WebMessagingTemplate;


@Controller
public class PortfolioController {

	private final WebMessagingTemplate messagingTemplate;


	@Autowired
	public PortfolioController(WebMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}


	@SubscribeEvent("/positions")
	public List<PortfolioPosition> getPortfolios() throws IOException {
		List<PortfolioPosition> positions = new ArrayList<PortfolioPosition>();
		positions.add(new PortfolioPosition("Citrix Systems, Inc.", "CTXS", 24.30, 75));
		positions.add(new PortfolioPosition("Dell Inc.", "DELL", 13.44, 50));
		positions.add(new PortfolioPosition("EMC Corporation", "EMC", 24.30, 75));
		positions.add(new PortfolioPosition("Google Inc", "GOOG", 905.09, 5));
		positions.add(new PortfolioPosition("Microsoft", "MSFT", 34.15, 33));
		positions.add(new PortfolioPosition("VMware, Inc.", "VMW", 65.58, 23));
		return positions;
	}

}
