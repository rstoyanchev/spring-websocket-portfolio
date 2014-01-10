/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.portfolio.web.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.ApplicationListener;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.springframework.util.SocketUtils;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * A wrapper around an embedded {@link org.apache.catalina.startup.Tomcat} server
 * for use in testing Spring WebSocket applications.
 *
 * Ensures the Tomcat's WsContextListener is deployed and helps with loading
 * Spring configuration and deploying Spring MVC's DispatcherServlet.
 *
 * @author Rossen Stoyanchev
 */
public class TomcatWebSocketTestServer {

	private static final ApplicationListener WS_APPLICATION_LISTENER =
			new ApplicationListener(WsContextListener.class.getName(), false);

	private final Tomcat tomcatServer;

	private final int port;

	private Context context;


	public TomcatWebSocketTestServer(int port) {

		this.port = port;

		Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(this.port);

        File baseDir = createTempDir("tomcat");
        String baseDirPath = baseDir.getAbsolutePath();

		this.tomcatServer = new Tomcat();
		this.tomcatServer.setBaseDir(baseDirPath);
		this.tomcatServer.setPort(this.port);
        this.tomcatServer.getService().addConnector(connector);
        this.tomcatServer.setConnector(connector);
	}

	private File createTempDir(String prefix) {
		try {
			File tempFolder = File.createTempFile(prefix + ".", "." + getPort());
			tempFolder.delete();
			tempFolder.mkdir();
			tempFolder.deleteOnExit();
			return tempFolder;
		}
		catch (IOException ex) {
			throw new RuntimeException("Unable to create temp directory", ex);
		}
	}

	public int getPort() {
		return this.port;
	}

	public void deployConfig(Class<? extends WebApplicationInitializer>... initializers) {

        this.context = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));

		// Add Tomcat's DefaultServlet
		Wrapper defaultServlet = this.context.createWrapper();
		defaultServlet.setName("default");
		defaultServlet.setServletClass("org.apache.catalina.servlets.DefaultServlet");
		this.context.addChild(defaultServlet);

		// Ensure WebSocket support
		this.context.addApplicationListener(WS_APPLICATION_LISTENER);

		this.context.addServletContainerInitializer(
				new SpringServletContainerInitializer(), new HashSet<Class<?>>(Arrays.asList(initializers)));
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
