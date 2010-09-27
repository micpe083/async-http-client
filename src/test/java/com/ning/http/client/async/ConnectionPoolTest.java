/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.http.client.async;

import com.ning.http.client.*;
import com.ning.http.client.logging.LogManager;
import com.ning.http.client.logging.Logger;
import org.jboss.netty.channel.Channel;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

public class ConnectionPoolTest extends AbstractBasicTest {
    protected final Logger log = LogManager.getLogger(AbstractBasicTest.class);

    @Test
    public void testMaxTotalConnections() {
        AsyncHttpClient client = new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setConnectionTimeoutInMs(100)
                        .setRequestTimeoutInMs(100)
                        .setKeepAlive(true)
                        .setMaximumConnectionsTotal(1)
                        .build()
        );

        String url = getTargetUrl();
        int i;
        for (i = 0; i < 3; i++) {
            try {
                log.info(String.format("%d requesting url [%s]...", i, url));
                Response response = client.prepareGet(url).execute().get();
                log.info(String.format("%d response [%s].", i, response));
                if (i > 1) {
                    fail("ConnectionsCache Broken");
                }
            } catch (Exception e) {
                if (i > 0) {
                    assertEquals(e.getClass(), IOException.class);
                    assertEquals(e.getMessage(), "Too many connections");
                    log.error(String.format("%d error: %s", i, e.getMessage()));
                } else {
                    fail("ConnectionsCache Broken");
                }
            }
        }
    }

    @Test
    public void testInvalidConnectionsPool() {

        ConnectionsPool<String, Channel> cp = new ConnectionsPool<String, Channel>() {

            public boolean addConnection(String key, Channel connection) {
                return false;
            }

            public Channel getConnection(String key) {
                return null;
            }

            public Channel removeConnection(String connection) {
                return null;
            }

            public boolean removeAllConnections(Channel connection) {
                return false;
            }

            public boolean canCacheConnection() {
                return false;
            }

            public void destroy() {

            }
        };

        AsyncHttpClient client = new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setConnectionsPool(cp)
                        .build()
        );

        Exception exception = null;
        try {
            client.prepareGet(getTargetUrl()).execute().get(TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(exception.getMessage(), "Too many connections");
    }

    @Test
    public void testValidConnectionsPool() {

        ConnectionsPool<String, Channel> cp = new ConnectionsPool<String, Channel>() {

            public boolean addConnection(String key, Channel connection) {
                return true;
            }

            public Channel getConnection(String key) {
                return null;
            }

            public Channel removeConnection(String connection) {
                return null;
            }

            public boolean removeAllConnections(Channel connection) {
                return false;
            }

            public boolean canCacheConnection() {
                return true;
            }

            public void destroy() {

            }
        };

        AsyncHttpClient client = new AsyncHttpClient(
                new AsyncHttpClientConfig.Builder()
                        .setConnectionsPool(cp)
                        .build()
        );

        Exception exception = null;
        try {
            client.prepareGet(getTargetUrl()).execute().get(TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNull(exception);
    }


    @Test(groups = "standalone")
    public void multipleMaxConnectionOpenTest() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setKeepAlive(true)
                .setConnectionTimeoutInMs(5000).setMaximumConnectionsTotal(1).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        String body = "hello there";

        // once
        Response response = c.preparePost(getTargetUrl())
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), body);

        // twice
        Exception exception = null;
        try {
            c.preparePost(getTargetUrl()).setBody(body).execute().get(TIMEOUT, TimeUnit.SECONDS);
            fail("Should throw exception. Too many connections issued.");
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(exception.getMessage(), "Too many connections");
    }

    @Test(groups = "standalone")
    public void multipleMaxConnectionOpenTestWithQuery() throws Throwable {
        AsyncHttpClientConfig cg = new AsyncHttpClientConfig.Builder().setKeepAlive(true)
                .setConnectionTimeoutInMs(5000).setMaximumConnectionsTotal(1).build();
        AsyncHttpClient c = new AsyncHttpClient(cg);

        String body = "hello there";

        // once
        Response response = c.preparePost(getTargetUrl() + "?foo=bar")
                .setBody(body)
                .execute().get(TIMEOUT, TimeUnit.SECONDS);

        assertEquals(response.getResponseBody(), "foo_" + body);

        // twice
        Exception exception = null;
        try {
            c.preparePost(getTargetUrl()).setBody(body).execute().get(TIMEOUT, TimeUnit.SECONDS);
            fail("Should throw exception. Too many connections issued.");
        } catch (Exception ex) {
            ex.printStackTrace();
            exception = ex;
        }
        assertNotNull(exception);
        assertEquals(exception.getMessage(), "Too many connections");
    }

    @Test(groups = {"online", "async"})
    public void asyncDoGetMaxConnectionsTest() throws Throwable {
        AsyncHttpClient client = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setMaximumConnectionsTotal(2).build());

        // Use a l in case the assert fail
        final CountDownLatch l = new CountDownLatch(2);

        AsyncCompletionHandler<Response> handler = new AsyncCompletionHandlerAdapter() {

            @Override
            public Response onCompleted(Response response) throws Exception {
                l.countDown();
                return response;
            }

            @Override
            public void onThrowable(Throwable t) {
                try {
                    Assert.fail("Unexpected exception", t);
                } finally {
                    l.countDown();
                }
            }
        };

        client.prepareGet("http://www.oracle.com/index.html").execute(handler).get();
        client.prepareGet("http://www.apache.org/").execute(handler).get();

        try {
            client.prepareGet("http://www.ning.com/").execute(handler).get();
            Assert.fail();
        } catch (IOException ex) {
            String s = ex.getMessage();
            assertEquals(s, "Too many connections");
        }

        if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
            Assert.fail("Timed out");
        }
        client.close();
    }
}
