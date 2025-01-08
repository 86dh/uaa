package org.cloudfoundry.identity.uaa.integration.feature;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = DefaultIntegrationTestConfig.class)
class ErrorRoutingIT {

    @Autowired
    @RegisterExtension
    private IntegrationTestExtension integrationTestExtension;

    @Autowired
    WebDriver webDriver;

    @Value("${integration.test.base_url}")
    String baseUrl;

    @Test
    void methodNotAllowedRoutedToErrorPage() {
        webDriver.get(baseUrl + "/authenticate");

        assertThat(webDriver.findElement(By.tagName("h2")).getText().contains("Uh oh.")).as("Check if on the error page").isTrue();
        assertThat(webDriver.findElement(By.tagName("h2")).getText().contains("Something went amiss.")).as("Check if on the error page").isTrue();
    }

    @Test
    void statusCodeToErrorPage() throws IOException {
        CallErrorPageAndCheckHttpStatusCode("/error", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/error404", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/error429", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/error500", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/errorAny", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/rejected", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/saml_error", "GET", 200);
        CallErrorPageAndCheckHttpStatusCode("/error", "GET", 200);
    }

    @Test
    void responseToErrorPage() throws IOException {
        String body = CallErrorPageAndCheckHttpStatusCode("/info", "TRACE", 405);
        assertThat(body.indexOf("<html")).as("Expected no response HTML body, but received: " + body).isEqualTo(-1);
    }

    @Test
    void requestRejectedExceptionErrorPage() throws IOException {
        final String rejectedEndpoint = "/login;endpoint=x"; // spring securiy throws RequestRejectedException and by default status 500, but now 400
        webDriver.get(baseUrl + rejectedEndpoint);

        assertThat(webDriver.findElement(By.tagName("h2")).getText().contains("The request was rejected because it contained a potentially malicious character.")).as("Check if on the error page").isTrue();

        CallErrorPageAndCheckHttpStatusCode(rejectedEndpoint, "GET", 400);
    }

    private String CallErrorPageAndCheckHttpStatusCode(String errorPath, String method, int codeExpected) throws IOException {
        HttpURLConnection cn = (HttpURLConnection) new URL(baseUrl + errorPath).openConnection();
        cn.setRequestMethod(method);
        cn.setRequestProperty("Accept", "text/html");
        // connection initiate
        cn.connect();
        assertThat(cn.getResponseCode()).as("Check status code from " + errorPath + " is " + codeExpected).isEqualTo(codeExpected);
        return getResponseBody(cn);
    }

    private String getResponseBody(HttpURLConnection connection) throws IOException {
        BufferedReader reader;
        if (200 <= connection.getResponseCode() && connection.getResponseCode() <= 299) {
            reader = new BufferedReader(new InputStreamReader((connection.getInputStream())));
        } else {
            reader = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
        }

        StringBuilder sb = new StringBuilder();
        int BUFFER = 4096;
        char[] buffer = new char[4096];
        int charsRead = 0;
        try {
            while ((charsRead = reader.read(buffer, 0, BUFFER)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
        } catch (IOException ie) {
            IOUtils.close(connection);
        }
        return sb.toString();
    }
}
