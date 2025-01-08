package org.cloudfoundry.identity.uaa.integration.feature;

import org.cloudfoundry.identity.uaa.ServerRunningExtension;
import org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils;
import org.cloudfoundry.identity.uaa.integration.util.ScreenshotOnFailExtension;
import org.cloudfoundry.identity.uaa.oauth.common.util.RandomValueStringGenerator;
import org.cloudfoundry.identity.uaa.provider.IdentityProvider;
import org.cloudfoundry.identity.uaa.provider.LdapIdentityProviderDefinition;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.test.InMemoryLdapServer;
import org.cloudfoundry.identity.uaa.test.UaaTestAccounts;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneConfiguration;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.uaa.constants.OriginKeys.LDAP;
import static org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils.doesSupportZoneDNS;
import static org.cloudfoundry.identity.uaa.provider.LdapIdentityProviderDefinition.LDAP_TLS_NONE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@SpringJUnitConfig(classes = DefaultIntegrationTestConfig.class)
class LdapLoginIT {

    @Autowired
    @RegisterExtension
    private IntegrationTestExtension integrationTestExtension;

    @RegisterExtension
    private static final ScreenshotOnFailExtension screenshotExtension = new ScreenshotOnFailExtension();

    @Autowired
    RestOperations restOperations;

    @Autowired
    WebDriver webDriver;

    @Value("${integration.test.base_url}")
    String baseUrl;

    @Autowired
    TestClient testClient;

    @RegisterExtension
    private static final ServerRunningExtension serverRunning = ServerRunningExtension.connect();

    private String zoneAdminToken;
    private static InMemoryLdapServer server;
    private Optional<String> alertError = Optional.empty();

    @BeforeAll
    public static void startLocalLdap() {
        server = InMemoryLdapServer.startLdap();
    }

    @AfterAll
    static void stopLocalLdap() {
        server.stop();
    }

    @BeforeEach
    void clearWebDriverOfCookies() {
        //ensure we are able to resolve DNS for hostname testzone2.localhost
        assertThat(doesSupportZoneDNS()).as("Expected testzone1/2/3/4.localhost to resolve to 127.0.0.1").isTrue();

        screenshotExtension.setWebDriver(webDriver);
        for (String domain : Arrays.asList("localhost", "testzone1.localhost", "testzone2.localhost", "testzone3.localhost", "testzone4.localhost")) {
            webDriver.get(baseUrl.replace("localhost", domain) + "/logout.do");
            webDriver.manage().deleteAllCookies();
        }

        String token = IntegrationTestUtils.getClientCredentialsToken(baseUrl, "admin", "adminsecret");

        ScimGroup group = new ScimGroup(null, "zones.testzone2.admin", null);
        IntegrationTestUtils.createGroup(token, "", baseUrl, group);
    }

    @AfterEach
    void cleanup() {
        String token = IntegrationTestUtils.getClientCredentialsToken(baseUrl, "admin", "adminsecret");
        String groupId = IntegrationTestUtils.getGroup(token, "", baseUrl, "zones.testzone2.admin").getId();
        IntegrationTestUtils.deleteGroup(token, "", baseUrl, groupId);

        alertError.ifPresent(msg ->
                System.err.println("Failed to log in with error: \"%s\"".formatted(msg))
        );
    }

    @Test
    void ldapLogin_with_StartTLS() throws Exception {
        Long beforeTest = System.currentTimeMillis();
        performLdapLogin("testzone2", server.getUrl(), "marissa4", "ldap4");
        Long afterTest = System.currentTimeMillis();
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");
        ScimUser user = IntegrationTestUtils.getUserByZone(zoneAdminToken, baseUrl, "testzone2", "marissa4");
        IntegrationTestUtils.validateUserLastLogon(user, beforeTest, afterTest);
        IntegrationTestUtils.validateAccountChooserCookie(baseUrl.replace("localhost", "testzone2.localhost"), webDriver, IdentityZoneHolder.get());
    }

    @Test
    void ldap_login_using_utf8_characters() throws Exception {
        performLdapLogin("testzone2", server.getUrl(), "\u7433\u8D3A", "koala");
        assertThat(webDriver.findElement(By.cssSelector("h1")).getText()).contains("Where to?");
    }

    private void performLdapLogin(String subdomain, String ldapUrl, String username, String password) {
        //ensure that certs have been added to truststore via gradle
        String zoneUrl = baseUrl.replace("localhost", subdomain + ".localhost");

        //identity client token
        RestTemplate identityClient = IntegrationTestUtils.getClientCredentialsTemplate(
                IntegrationTestUtils.getClientCredentialsResource(baseUrl, new String[]{"zones.write", "zones.read", "scim.zones"}, "identity", "identitysecret")
        );
        //admin client token - to create users
        RestTemplate adminClient = IntegrationTestUtils.getClientCredentialsTemplate(
                IntegrationTestUtils.getClientCredentialsResource(baseUrl, new String[0], "admin", "adminsecret")
        );
        //create the zone
        IdentityZoneConfiguration config = new IdentityZoneConfiguration();
        config.getCorsPolicy().getDefaultConfiguration().setAllowedMethods(List.of(GET.toString(), POST.toString()));
        IntegrationTestUtils.createZoneOrUpdateSubdomain(identityClient, baseUrl, subdomain, subdomain, config);

        //create a zone admin user
        String email = new RandomValueStringGenerator().generate() + "@ldaptesting.org";
        ScimUser user = IntegrationTestUtils.createUser(adminClient, baseUrl, email, "firstname", "lastname", email, true);
        String groupName = "zones.%s.admin".formatted(subdomain);
        String groupId = IntegrationTestUtils.findGroupId(adminClient, baseUrl, groupName);
        IntegrationTestUtils.addMemberToGroup(adminClient, baseUrl, user.getId(), groupId);

        //get the zone admin token
        zoneAdminToken =
                IntegrationTestUtils.getAccessTokenByAuthCode(serverRunning,
                        UaaTestAccounts.standard(serverRunning),
                        "identity",
                        "identitysecret",
                        email,
                        "secr3T");

        LdapIdentityProviderDefinition ldapIdentityProviderDefinition = LdapIdentityProviderDefinition.searchAndBindMapGroupToScopes(
                ldapUrl,
                "cn=admin,ou=Users,dc=test,dc=com",
                "adminsecret",
                "dc=test,dc=com",
                "cn={0}",
                "ou=Users,dc=test,dc=com",
                "member={0}",
                "mail",
                null,
                false,
                true,
                true,
                100,
                true);
        ldapIdentityProviderDefinition.setTlsConfiguration(LDAP_TLS_NONE);

        IdentityProvider provider = new IdentityProvider();
        provider.setIdentityZoneId(subdomain);
        provider.setType(LDAP);
        provider.setActive(true);
        provider.setConfig(ldapIdentityProviderDefinition);
        provider.setOriginKey(LDAP);
        provider.setName("simplesamlphp for uaa");
        IntegrationTestUtils.createOrUpdateProvider(zoneAdminToken, baseUrl, provider);

        webDriver.get(zoneUrl + "/login");
        webDriver.findElement(By.name("username")).sendKeys(username);
        webDriver.findElement(By.name("password")).sendKeys(password);
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        saveAlertErrorMessage();
    }

    private void saveAlertErrorMessage() {
        try {
            WebElement element = webDriver.findElement(By.className("alert-error"));
            alertError = Optional.of(element.getText());
        } catch (NoSuchElementException e) {
            // do nothing
        }
    }
}
