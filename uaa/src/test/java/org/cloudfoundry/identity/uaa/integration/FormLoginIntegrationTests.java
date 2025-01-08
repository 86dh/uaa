/*
 * *****************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.integration;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.cloudfoundry.identity.uaa.ServerRunningExtension;
import org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils;
import org.cloudfoundry.identity.uaa.security.web.CookieBasedCsrfTokenRepository;
import org.cloudfoundry.identity.uaa.test.TestAccountExtension;
import org.cloudfoundry.identity.uaa.test.UaaTestAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.OK;

class FormLoginIntegrationTests {

    @RegisterExtension
    private static final ServerRunningExtension serverRunning = ServerRunningExtension.connect();

    private static final UaaTestAccounts testAccounts = UaaTestAccounts.standard(serverRunning);

    @RegisterExtension
    private static final TestAccountExtension testAccountSetup = TestAccountExtension.standard(serverRunning, testAccounts);

    Header header = new BasicHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
    List<Header> headers = Collections.singletonList(header);

    BasicCookieStore cookieStore = new BasicCookieStore();
    CloseableHttpClient httpclient;

    @BeforeEach
    void createHttpClient() {
        httpclient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
                .setDefaultHeaders(headers)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    @AfterEach
    void closeClient() throws Exception {
        httpclient.close();
    }


    @Test
    void unauthenticatedRedirect() throws Exception {
        String location = serverRunning.getBaseUrl() + "/";
        HttpGet httpget = new HttpGet(location);
        httpget.setConfig(
                RequestConfig.custom().setRedirectsEnabled(false).build()
        );
        CloseableHttpResponse response = httpclient.execute(httpget);
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(FOUND.value());
        location = response.getFirstHeader("Location").getValue();
        response.close();
        httpget.completed();
        assertThat(location.contains("/login")).isTrue();
    }

    @Test
    void successfulAuthenticationFlow() throws Exception {
        //request home page /
        String location = serverRunning.getBaseUrl() + "/";
        HttpGet httpget = new HttpGet(location);
        CloseableHttpResponse response = httpclient.execute(httpget);

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(OK.value());

        String body = EntityUtils.toString(response.getEntity());
        EntityUtils.consume(response.getEntity());
        response.close();
        httpget.completed();

        assertThat(body.contains("/login.do")).isTrue();
        assertThat(body.contains("username")).isTrue();
        assertThat(body.contains("password")).isTrue();

        String csrf = IntegrationTestUtils.extractCookieCsrf(body);

        HttpUriRequest loginPost = RequestBuilder.post()
                .setUri(serverRunning.getBaseUrl() + "/login.do")
                .addParameter("username", testAccounts.getUserName())
                .addParameter("password", testAccounts.getPassword())
                .addParameter(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, csrf)
                .build();

        response = httpclient.execute(loginPost);
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(FOUND.value());
        location = response.getFirstHeader("Location").getValue();
        response.close();

        // This implementation of httpclient would not send the `JSESSIONID` cookie that has `Secure` attribute
        // to the locally-run server via HTTP; the other integration tests use webdriver to simulate web flows, which would make
        // an exception for HTTP on localhost (which is the standard [browser behavior](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies))
        // As a workaround, manually attach the `JSESSIONID` cookie (obtained via login) to this request.
        Cookie jsessionidCookie = cookieStore.getCookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .findAny().orElse(null);
        assertThat(jsessionidCookie).isNotNull();
        HttpUriRequest getRequestAfterLogin = RequestBuilder.get()
                .setUri(location)
                .addHeader("Cookie", "JSESSIONID=" + jsessionidCookie.getValue())
                .build();

        response = httpclient.execute(getRequestAfterLogin);
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(OK.value());

        body = EntityUtils.toString(response.getEntity());
        response.close();
        assertThat(body.contains("Sign Out")).isTrue();
    }

}
