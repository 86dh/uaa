package org.cloudfoundry.identity.uaa.integration;

import org.cloudfoundry.identity.uaa.integration.feature.DefaultIntegrationTestConfig;
import org.cloudfoundry.identity.uaa.integration.feature.TestClient;
import org.cloudfoundry.identity.uaa.oauth.client.test.TestAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = DefaultIntegrationTestConfig.class)
class SessionControllerIntegrationTests {
    @Autowired
    TestClient testClient;
    @Autowired
    TestAccounts testAccounts;
    @Autowired
    WebDriver webDriver;
    @Value("${integration.test.base_url}")
    String baseUrl;

    @BeforeEach
    @AfterEach
    void logout_and_clear_cookies() {
        try {
            webDriver.get(baseUrl + "/logout.do");
        } catch (org.openqa.selenium.TimeoutException x) {
            //try again - this should not be happening - 20 second timeouts
            webDriver.get(baseUrl + "/logout.do");
        }
        webDriver.manage().deleteAllCookies();
    }

    @Test
    void sessionPageHasTheFunction() {
        webDriver.get(baseUrl +
                "/session?clientId=admin&messageOrigin=http://localhost:8080");

        Object r = ((JavascriptExecutor) webDriver).executeScript(
                "return typeof(handleMessage);");
        assertThat(r.toString()).isEqualTo("function");
    }

    @Test
    void sessionManagementPageHasVariablesSet() {
        webDriver.get(baseUrl +
                "/session_management?clientId=admin&messageOrigin=http://localhost:8080");

        Object clientId = ((JavascriptExecutor) webDriver).executeScript(
                "return clientId;");
        assertThat(clientId.toString()).isEqualTo("admin");

        Object origin = ((JavascriptExecutor) webDriver).executeScript(
                "return messageOrigin;");
        assertThat(origin.toString()).isEqualTo("http://localhost:8080");
    }
}
