/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.rest.v1;

import io.pravega.client.ClientConfig;
import io.pravega.client.netty.impl.ConnectionFactoryImpl;
import io.pravega.controller.server.ControllerService;
import io.pravega.controller.server.rest.RESTServer;
import io.pravega.controller.server.rest.RESTServerConfig;
import io.pravega.controller.server.rest.impl.RESTServerConfigImpl;
import io.pravega.test.common.TestUtils;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.jersey.SslConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Test for ping API.
 */
public abstract class PingTest {

    //Ensure each test completes within 30 seconds.
    @Rule
    public final Timeout globalTimeout = new Timeout(10, TimeUnit.SECONDS);

    private RESTServerConfig serverConfig;
    private RESTServer restServer;
    private Client client;

    @Before
    public void setup() {
        ControllerService mockControllerService = mock(ControllerService.class);
        serverConfig = getServerConfig();
        restServer = new RESTServer(null, mockControllerService, null, serverConfig,
                new ConnectionFactoryImpl(ClientConfig.builder().build()));
        restServer.startAsync();
        restServer.awaitRunning();
        client = createJerseyClient();
    }

    protected abstract Client createJerseyClient();

    abstract RESTServerConfig getServerConfig();


    @After
    public void tearDown() {
        client.close();
        restServer.stopAsync();
        restServer.awaitTerminated();
    }

    @Test
    public void test() {
        URI streamResourceURI = UriBuilder.fromPath("//localhost:" + serverConfig.getPort() + "/ping")
                                          .scheme(getURLScheme()).build();
        Response response = client.target(streamResourceURI).request().buildGet().invoke();
        assertEquals(200, response.getStatus());
    }

    protected abstract String getURLScheme();

    public static class SimplePingTest extends PingTest {

        @Override
        protected Client createJerseyClient() {
            return ClientBuilder.newClient();
        }

        @Override
        RESTServerConfig getServerConfig() {
            return RESTServerConfigImpl.builder().host("localhost").port(TestUtils.getAvailableListenPort())
                                .build();
        }

        @Override
        protected String getURLScheme() {
            return "http";
        }
    }

    public static class SecurePingTest extends PingTest {

        @Override
        protected Client createJerseyClient() {
            SslConfigurator sslConfig = SslConfigurator.newInstance()
                                                       .trustStoreFile("../config/bookie.truststore.jks");

            SSLContext sslContext = sslConfig.createSSLContext();
            return ClientBuilder.newBuilder().sslContext(sslContext)
                                .hostnameVerifier((s1, s2) -> true)
                                .build();
        }

        @Override
        RESTServerConfig getServerConfig() {
            return RESTServerConfigImpl.builder().host("localhost").port(TestUtils.getAvailableListenPort())
                                       .tlsEnabled(true)
                                       .keyFilePath("../config/bookie.keystore.jks")
                                       .keyFilePasswordPath("../config/bookie.keystore.jks.passwd")
                                       .build();
        }

        @Override
        protected String getURLScheme() {
            return "https";
        }
    }
}
