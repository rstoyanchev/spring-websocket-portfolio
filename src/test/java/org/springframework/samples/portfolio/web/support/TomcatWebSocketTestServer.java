/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.portfolio.web.support;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.websocket.server.WsContextListener;

import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * A wrapper around an embedded {@link org.apache.catalina.startup.Tomcat} server
 * for use in testing Spring WebSocket applications.
 *
 * Ensures the Tomcat's WsContextListener is deployed and helps with loading
 * Spring configuration and deploying Spring MVC's DispatcherServlet.
 *
 * @author Rossen Stoyanchev
 */
public class TomcatWebSocketTestServer implements WebSocketTestServer {

	private final Tomcat tomcatServer;

	private final int port;

	private final File baseDir;

	private Context context;


	public TomcatWebSocketTestServer(int port) {

		this.port = port;
		this.baseDir = createBaseDir(port);

		Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(this.port);

		this.tomcatServer = new Tomcat();
		this.tomcatServer.setBaseDir(this.baseDir.getAbsolutePath());
		this.tomcatServer.setPort(this.port);
        this.tomcatServer.getService().addConnector(connector);
        this.tomcatServer.setConnector(connector);
	}

	private static File createBaseDir(int port) {
		try {
			File file = File.createTempFile("tomcat.", "." + port);
			file.delete();
			file.mkdir();
			file.deleteOnExit();
			return file;
		}
		catch (IOException ex) {
			throw new RuntimeException("Unable to create temp directory", ex);
		}
	}

	public int getPort() {
		return this.port;
	}

	@Override
	public void deployDispatcherServlet(WebApplicationContext cxt) {
		this.context = this.tomcatServer.addContext("", this.baseDir.getAbsolutePath());
		Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(cxt));
		this.context.addServletMappingDecoded("/", "dispatcherServlet");
		this.context.addApplicationListener(WsContextListener.class.getName());
	}

	public void deployWithInitializer(Class<? extends WebApplicationInitializer>... initializers) {
        this.context = this.tomcatServer.addContext("", this.baseDir.getAbsolutePath());
        Tomcat.addServlet(this.context, "default", "org.apache.catalina.servlets.DefaultServlet");
		this.context.addApplicationListener(WsContextListener.class.getName());
		this.context.addServletContainerInitializer(new SpringServletContainerInitializer(),
				new HashSet<>(Arrays.asList(initializers)));
	}

	public void undeployConfig() {
		if (this.context != null) {
			this.tomcatServer.getHost().removeChild(this.context);
		}
	}

	public void start() throws Exception {
		this.tomcatServer.start();
	}

	public void stop() throws Exception {
		this.tomcatServer.stop();
	}

}
