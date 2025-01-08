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

import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.assertj.core.api.Assertions;
import org.cloudfoundry.identity.uaa.ServerRunningExtension;
import org.cloudfoundry.identity.uaa.client.UaaClientDetails;
import org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils;
import org.cloudfoundry.identity.uaa.oauth.client.http.OAuth2ErrorHandler;
import org.cloudfoundry.identity.uaa.oauth.client.test.OAuth2ContextConfiguration;
import org.cloudfoundry.identity.uaa.oauth.client.test.OAuth2ContextExtension;
import org.cloudfoundry.identity.uaa.oauth.common.DefaultOAuth2AccessToken;
import org.cloudfoundry.identity.uaa.oauth.common.OAuth2AccessToken;
import org.cloudfoundry.identity.uaa.oauth.common.util.RandomValueStringGenerator;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimGroupMember;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.security.web.CookieBasedCsrfTokenRepository;
import org.cloudfoundry.identity.uaa.test.TestAccountExtension;
import org.cloudfoundry.identity.uaa.test.UaaTestAccounts;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneConfiguration;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils.doesSupportZoneDNS;
import static org.cloudfoundry.identity.uaa.integration.util.IntegrationTestUtils.getHeaders;
import static org.cloudfoundry.identity.uaa.oauth.common.util.OAuth2Utils.USER_OAUTH_APPROVAL;
import static org.cloudfoundry.identity.uaa.oauth.token.TokenConstants.GRANT_TYPE_AUTHORIZATION_CODE;
import static org.cloudfoundry.identity.uaa.security.web.CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@OAuth2ContextConfiguration(OAuth2ContextConfiguration.ClientCredentials.class)
public class ScimGroupEndpointsIntegrationTests {

    private ScimGroupMember dale;
    private ScimGroupMember joel;
    private ScimGroupMember vidya;

    private final String deleteMe = "deleteme_" + new RandomValueStringGenerator().generate().toLowerCase();

    private final String cfDev = "cf_dev_" + new RandomValueStringGenerator().generate().toLowerCase();

    private final String cfMgr = "cf_mgr_" + new RandomValueStringGenerator().generate().toLowerCase();

    private final String cfid = "cfid_" + new RandomValueStringGenerator().generate().toLowerCase();

    private final List<String> allowedGroups = List.of(deleteMe, cfDev, cfMgr, cfid);

    private final String groupEndpoint = "/Groups";

    private final String userEndpoint = "/Users";

    private List<String> groupIds = new ArrayList<>();

    private static final List<String> defaultGroups = Arrays.asList("openid", "scim.me", "cloud_controller.read",
            "cloud_controller.write", "password.write", "scim.userids", "uaa.user", "approvals.me",
            "oauth.approvals", "cloud_controller_service_permissions.read", "profile", "roles", "user_attributes", "uaa.offline_token");

    @RegisterExtension
    private static final ServerRunningExtension serverRunning = ServerRunningExtension.connect();

    private static final UaaTestAccounts testAccounts = UaaTestAccounts.standard(serverRunning);

    @RegisterExtension
    private static final TestAccountExtension testAccountSetup = TestAccountExtension.standard(serverRunning, testAccounts);

    @RegisterExtension
    private static final OAuth2ContextExtension context = OAuth2ContextExtension.withTestAccounts(serverRunning, testAccountSetup);

    private RestTemplate client;
    private List<ScimGroup> scimGroups;

    @BeforeEach
    public void createRestTemplate() {
        client = (RestTemplate) serverRunning.getRestTemplate();
        client.setErrorHandler(new OAuth2ErrorHandler(context.getResource()) {
            // Pass errors through in response entity for status code analysis
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
            }
        });

        joel = new ScimGroupMember(createUser("joel_" + new RandomValueStringGenerator().generate().toLowerCase(), "Passwo3d").getId());
        dale = new ScimGroupMember(createUser("dale_" + new RandomValueStringGenerator().generate().toLowerCase(), "Passwo3d").getId());
        vidya = new ScimGroupMember(createUser("vidya_" + new RandomValueStringGenerator().generate().toLowerCase(), "Passwo3d").getId());
    }

    @AfterEach
    public void tearDown() {
        deleteResource(userEndpoint, dale.getMemberId());
        deleteResource(userEndpoint, joel.getMemberId());
        deleteResource(userEndpoint, vidya.getMemberId());
        for (String id : groupIds) {
            deleteResource(groupEndpoint, id);
        }
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> deleteResource(String url, String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("If-Match", "*");
        return client.exchange(serverRunning.getUrl(url + "/{id}"), HttpMethod.DELETE, new HttpEntity<Void>(headers),
                Map.class, id);
    }

    private ScimUser createUser(String username, String password) {
        ScimUser user = new ScimUser();
        user.setUserName(username);
        user.setName(new ScimUser.Name(username, username));
        user.addEmail(username);
        user.setVerified(true);
        user.setPassword(password);
        ResponseEntity<ScimUser> result = client.postForEntity(serverRunning.getUrl(userEndpoint), user, ScimUser.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        return result.getBody();
    }

    private ScimGroup createGroup(String name, ScimGroupMember... members) {
        ScimGroup g = new ScimGroup(null, name, IdentityZoneHolder.get().getId());
        List<ScimGroupMember> m = members != null ? Arrays.asList(members) : Collections.emptyList();
        g.setMembers(m);
        ScimGroup g1 = client.postForEntity(serverRunning.getUrl(groupEndpoint), g, ScimGroup.class).getBody();
        assertEquals(name, g1.getDisplayName());
        assertEquals(m.size(), g1.getMembers().size());
        groupIds.add(g1.getId());
        return g1;
    }

    private ScimGroup updateGroup(String id, String name, ScimGroupMember... members) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("If-Match", "*");
        ScimGroup g = new ScimGroup(null, name, IdentityZoneHolder.get().getId());
        List<ScimGroupMember> m = members != null ? Arrays.asList(members) : Collections.emptyList();
        g.setMembers(m);
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> r = client.exchange(serverRunning.getUrl(groupEndpoint + "/{id}"), HttpMethod.PUT,
                new HttpEntity<>(g, headers), Map.class, id);
        ScimGroup g1 = client.exchange(serverRunning.getUrl(groupEndpoint + "/{id}"), HttpMethod.PUT,
                new HttpEntity<>(g, headers), ScimGroup.class, id).getBody();
        assertEquals(name, g1.getDisplayName());
        assertEquals(m.size(), g1.getMembers().size());
        return g1;
    }

    private void validateUserGroups(String id, String... groups) {
        List<String> groupNames = groups != null ? Arrays.asList(groups) : Collections.emptyList();
        assertEquals(groupNames.size() + defaultGroups.size(), getUser(id).getGroups().size());
        for (ScimUser.Group g : getUser(id).getGroups()) {
            assertTrue(defaultGroups.contains(g.getDisplay()) || groupNames.contains(g.getDisplay()));
        }
    }

    private ScimUser getUser(String id) {
        return client.getForEntity(serverRunning.getUrl(userEndpoint + "/{id}"), ScimUser.class, id).getBody();
    }

    @Test
    public void getGroupsWithoutAttributesReturnsAllData() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = client.getForEntity(serverRunning.getUrl(groupEndpoint), Map.class);

        @SuppressWarnings("rawtypes")
        Map results = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Integer) results.get("totalResults") > 0, "There should be more than zero users");
        assertTrue(!((Collection<?>) results.get("resources")).isEmpty(), "There should be some resources");
        @SuppressWarnings("rawtypes")
        Map firstGroup = (Map) ((List) results.get("resources")).get(0);
        assertTrue(firstGroup.containsKey("id"));
        assertTrue(firstGroup.containsKey("displayName"));
        assertTrue(firstGroup.containsKey("schemas"));
        assertTrue(firstGroup.containsKey("meta"));
    }

    @Test
    public void createGroupSucceeds() {
        ScimGroup g1 = createGroup(cfid);
        // Check we can GET the group
        ScimGroup g2 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g1.getId());
        assertEquals(g1, g2);
    }

    @Test
    public void createAllowedGroupSucceeds() throws URISyntaxException {
        String testZoneId = "testzone1";
        assertTrue(doesSupportZoneDNS(), "Expected testzone1.localhost and testzone2.localhost to resolve to 127.0.0.1");
        String adminToken = IntegrationTestUtils.getClientCredentialsToken(serverRunning.getBaseUrl(), "admin", "adminsecret");
        IdentityZoneConfiguration config = new IdentityZoneConfiguration();
        config.getUserConfig().setAllowedGroups(allowedGroups);
        String zoneUrl = serverRunning.getBaseUrl().replace("localhost", testZoneId + ".localhost");
        String inZoneAdminToken = IntegrationTestUtils.createClientAdminTokenInZone(serverRunning.getBaseUrl(), adminToken, testZoneId, config);
        ScimGroup g1 = new ScimGroup(null, cfid, testZoneId);
        // Check we can GET the group
        ScimGroup g2 = IntegrationTestUtils.createOrUpdateGroup(inZoneAdminToken, null, zoneUrl, g1);
        assertEquals(g1.getDisplayName(), g2.getDisplayName());
        assertEquals(g1.getDisplayName(), IntegrationTestUtils.getGroup(inZoneAdminToken, null, zoneUrl, g1.getDisplayName()).getDisplayName());
        IntegrationTestUtils.deleteZone(serverRunning.getBaseUrl(), testZoneId, adminToken);
    }

    @Test
    public void createNotAllowedGroupFailsCorrectly() throws URISyntaxException {
        String testZoneId = "testzone1";
        assertTrue(doesSupportZoneDNS(), "Expected testzone1.localhost and testzone2.localhost to resolve to 127.0.0.1");
        final String notAllowed = "not_allowed_" + new RandomValueStringGenerator().generate().toLowerCase();
        String adminToken = IntegrationTestUtils.getClientCredentialsToken(serverRunning.getBaseUrl(), "admin", "adminsecret");
        ScimGroup g1 = new ScimGroup(null, notAllowed, testZoneId);
        IdentityZoneConfiguration config = new IdentityZoneConfiguration();
        config.getUserConfig().setAllowedGroups(allowedGroups);
        String zoneUrl = serverRunning.getBaseUrl().replace("localhost", testZoneId + ".localhost");
        String inZoneAdminToken = IntegrationTestUtils.createClientAdminTokenInZone(serverRunning.getBaseUrl(), adminToken, testZoneId, config);
        RestTemplate template = new RestTemplate();
        HttpEntity entity = new HttpEntity<>(JsonUtils.writeValueAsBytes(g1), IntegrationTestUtils.getAuthenticatedHeaders(inZoneAdminToken));
        try {
            template.exchange(zoneUrl + "/Groups", HttpMethod.POST, entity, HashMap.class);
            fail("must fail");
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().is4xxClientError());
            assertEquals(400, e.getRawStatusCode());
            assertThat(e.getMessage(),
                    containsString("The group with displayName: " + g1.getDisplayName() + " is not allowed in Identity Zone " + testZoneId));
        } finally {
            IntegrationTestUtils.deleteZone(serverRunning.getBaseUrl(), testZoneId, adminToken);
        }
    }

    @Test
    public void relyOnDefaultGroupsShouldAllowedGroupSucceed() throws URISyntaxException {
        String testZoneId = "testzone1";
        assertTrue(doesSupportZoneDNS(), "Expected testzone1.localhost and testzone2.localhost to resolve to 127.0.0.1");
        String adminToken = IntegrationTestUtils.getClientCredentialsToken(serverRunning.getBaseUrl(), "admin", "adminsecret");

        final String ccReadGroupName = "cloud_controller_service_permissions.read";

        /* allowed groups are empty, but 'cloud_controller_service_permissions.read' is part of the default groups
         * -> this group should therefore nevertheless be created during zone creation */
        final IdentityZoneConfiguration config = new IdentityZoneConfiguration();
        config.getUserConfig().setAllowedGroups(List.of());
        config.getUserConfig().setDefaultGroups(defaultGroups);

        final String zoneUrl = serverRunning.getBaseUrl().replace("localhost", testZoneId + ".localhost");
        // this creates/updates the zone with the new config -> also creates/updates the default groups
        final String inZoneAdminToken = IntegrationTestUtils.createClientAdminTokenInZone(serverRunning.getBaseUrl(), adminToken, testZoneId, config);

        // Check we can GET the group
        final ScimGroup ccGroupFromGetCall = IntegrationTestUtils.getGroup(inZoneAdminToken, null, zoneUrl, ccReadGroupName);
        assertNotNull(ccGroupFromGetCall);
        assertEquals(ccReadGroupName, ccGroupFromGetCall.getDisplayName());

        IntegrationTestUtils.deleteZone(serverRunning.getBaseUrl(), testZoneId, adminToken);
    }

    @Test
    public void changeDefaultGroupsAllowedGroupsUsageShouldSucceed() throws URISyntaxException {
        String testZoneId = "testzone1";
        assertTrue(doesSupportZoneDNS(), "Expected testzone1.localhost and testzone2.localhost to resolve to 127.0.0.1");
        String adminToken = IntegrationTestUtils.getClientCredentialsToken(serverRunning.getBaseUrl(), "admin", "adminsecret");
        IdentityZoneConfiguration config = new IdentityZoneConfiguration();

        // ensure zone does not exist
        if (IntegrationTestUtils.zoneExists(serverRunning.getBaseUrl(), testZoneId, adminToken)) {
            IntegrationTestUtils.deleteZone(serverRunning.getBaseUrl(), testZoneId, adminToken);
        }

        // add a new group to the allowed groups
        final String allowed = "allowed_" + new RandomValueStringGenerator().generate().toLowerCase();
        List<String> newDefaultGroups = new ArrayList<>(defaultGroups);
        newDefaultGroups.add(allowed);
        config.getUserConfig().setAllowedGroups(List.of());
        config.getUserConfig().setDefaultGroups(newDefaultGroups);
        String zoneUrl = serverRunning.getBaseUrl().replace("localhost", testZoneId + ".localhost");
        // this creates the zone as well as all default groups
        String inZoneAdminToken = IntegrationTestUtils.createClientAdminTokenInZone(serverRunning.getBaseUrl(), adminToken, testZoneId, config);

        // creating the newly allowed group should fail, as it already exists
        RestTemplate template = new RestTemplate();
        ScimGroup g1 = new ScimGroup(null, allowed, testZoneId);
        HttpEntity entity = new HttpEntity<>(JsonUtils.writeValueAsBytes(g1), IntegrationTestUtils.getAuthenticatedHeaders(inZoneAdminToken));
        try {
            final HttpClientErrorException.Conflict exception = assertThrows(
                    HttpClientErrorException.Conflict.class,
                    () -> template.exchange(zoneUrl + "/Groups", HttpMethod.POST, entity, HashMap.class)
            );
            Assertions.assertThat(exception.getMessage())
                    .contains("A group with displayName: %s already exists.".formatted(allowed));
        } finally {
            IntegrationTestUtils.deleteZone(serverRunning.getBaseUrl(), testZoneId, adminToken);
        }
    }

    @Test
    public void createGroupWithMembersSucceeds() {
        ScimGroup g1 = createGroup(cfid, joel, dale, vidya);
        // Check we can GET the group
        ScimGroup g2 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g1.getId());
        assertEquals(g1, g2);
        assertEquals(3, g2.getMembers().size());
        assertTrue(g2.getMembers().contains(joel));
        assertTrue(g2.getMembers().contains(dale));
        assertTrue(g2.getMembers().contains(vidya));

        // check that User.groups is updated
        validateUserGroups(joel.getMemberId(), cfid);
        validateUserGroups(dale.getMemberId(), cfid);
        validateUserGroups(vidya.getMemberId(), cfid);
    }

    @Test
    public void createGroupWithInvalidMembersFailsCorrectly() {
        ScimGroup g = new ScimGroup(null, cfid, IdentityZoneHolder.get().getId());
        ScimGroupMember m2 = new ScimGroupMember("wrongid");
        g.setMembers(Arrays.asList(vidya, m2));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> r = client.postForEntity(serverRunning.getUrl(groupEndpoint), g, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> g1 = r.getBody();
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertTrue(g1.containsKey("error"));
        assertTrue(g1.containsKey("message"));
        assertTrue(g1.get("message").contains("Invalid group member"));

        // check that the group was not created
        @SuppressWarnings("unchecked")
        Map<String, Object> g2 = client.getForObject(
                serverRunning.getUrl(groupEndpoint + "?filter=displayName eq \"{name}\""), Map.class, cfid);
        assertTrue(g2.containsKey("totalResults"));
        assertEquals(Integer.valueOf(0), (Integer) g2.get("totalResults"));
    }

    @Test
    public void createGroupWithMemberGroupSucceeds() {
        ScimGroup g1 = createGroup(cfid, vidya);
        ScimGroupMember m2 = new ScimGroupMember(g1.getId(), ScimGroupMember.Type.GROUP);
        ScimGroup g2 = createGroup(cfDev, m2);

        // Check we can GET the group
        ScimGroup g3 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g2.getId());
        assertEquals(g2, g3);
        assertEquals(1, g3.getMembers().size());
        assertTrue(g3.getMembers().contains(m2));

        // check that User.groups is updated
        validateUserGroups(vidya.getMemberId(), cfid, cfDev);
    }

    @Test
    public void createExistingGroupFailsCorrectly() {
        ScimGroup g1 = createGroup(cfid);
        @SuppressWarnings("unchecked")
        Map<String, String> g2 = client.postForEntity(serverRunning.getUrl(groupEndpoint), g1, Map.class).getBody();
        assertTrue(g2.containsKey("error"));
        assertEquals("scim_resource_already_exists", g2.get("error"));
    }

    @Test
    public void deleteGroupUpdatesUser() {
        ScimGroup g1 = createGroup(deleteMe, dale, vidya);
        validateUserGroups(dale.getMemberId(), deleteMe);
        validateUserGroups(vidya.getMemberId(), deleteMe);

        deleteResource(groupEndpoint, g1.getId());

        // check that the group does not exist anymore
        @SuppressWarnings("unchecked")
        Map<String, Object> g2 = client.getForObject(
                serverRunning.getUrl(groupEndpoint + "?filter=displayName eq \"{name}\""), Map.class, deleteMe);
        assertTrue(g2.containsKey("totalResults"));
        assertEquals(0, g2.get("totalResults"));

        // check that group membership is updated
        validateUserGroups(dale.getMemberId());
        validateUserGroups(vidya.getMemberId());
    }

    @Test
    public void deleteNonExistentGroupFailsCorrectly() {
        @SuppressWarnings("unchecked")
        Map<String, Object> g = deleteResource(groupEndpoint, deleteMe).getBody();
        assertTrue(g.containsKey("error"));
        assertEquals("scim_resource_not_found", g.get("error"));
    }

    @Test
    public void deleteMemberGroupUpdatesGroup() {
        ScimGroup g1 = createGroup(cfid, vidya);
        ScimGroupMember m2 = new ScimGroupMember(g1.getId(), ScimGroupMember.Type.GROUP);
        ScimGroup g2 = createGroup(cfDev, dale, m2);
        assertTrue(g2.getMembers().contains(m2));
        validateUserGroups(vidya.getMemberId(), cfid, cfDev);

        deleteResource(groupEndpoint, g1.getId());

        // check that parent group is updated
        ScimGroup g3 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g2.getId());
        assertEquals(1, g3.getMembers().size());
        assertFalse(g3.getMembers().contains(m2));
    }

    @Test
    public void testDeleteMemberUserUpdatesGroups() {
        ScimGroupMember toDelete = new ScimGroupMember(createUser(deleteMe, "Passwo3d").getId());
        ScimGroup g1 = createGroup(cfid, joel, dale, toDelete);
        ScimGroup g2 = createGroup(cfMgr, dale, toDelete);
        deleteResource(userEndpoint, toDelete.getMemberId());

        // check that membership has been updated
        ScimGroup g3 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g1.getId());
        assertEquals(2, g3.getMembers().size());
        assertFalse(g3.getMembers().contains(toDelete));

        g3 = client.getForObject(serverRunning.getUrl(groupEndpoint + "/{id}"), ScimGroup.class, g2.getId());
        assertEquals(1, g3.getMembers().size());
        assertFalse(g3.getMembers().contains(toDelete));
    }

    @Test
    public void testUpdateGroupUpdatesMemberUsers() {
        ScimGroup g1 = createGroup(cfid, joel, vidya);
        ScimGroup g2 = createGroup(cfMgr, dale);
        ScimGroupMember m1 = new ScimGroupMember(g1.getId(), ScimGroupMember.Type.GROUP);
        ScimGroupMember m2 = new ScimGroupMember(g2.getId(), ScimGroupMember.Type.GROUP);
        ScimGroup g3 = createGroup(cfDev, m1, m2);

        validateUserGroups(joel.getMemberId(), cfid, cfDev);
        validateUserGroups(vidya.getMemberId(), cfid, cfDev);
        validateUserGroups(dale.getMemberId(), cfMgr, cfDev);

        ScimGroup g4 = updateGroup(g3.getId(), "new_name", m1);

        // check that we did not create a new group, but only updated the
        // existing one
        assertEquals(g3, g4);
        // check that member users were updated
        validateUserGroups(dale.getMemberId(), cfMgr);
        validateUserGroups(joel.getMemberId(), cfid, "new_name");
        validateUserGroups(vidya.getMemberId(), cfid, "new_name");
    }

    @Test
    public void testAccessTokenReflectsGroupMembership() throws Exception {

        createTestClient(deleteMe, "secret", cfid);
        ScimUser user = createUser(deleteMe, "Passwo3d");
        createGroup(cfid, new ScimGroupMember(user.getId()));
        OAuth2AccessToken token = getAccessToken(deleteMe, "secret", deleteMe, "Passwo3d");
        assertTrue(token.getScope().contains(cfid), "Wrong token: " + token);

        deleteTestClient(deleteMe);
        deleteResource(userEndpoint, user.getId());

    }

    @Test
    public void testAccessTokenReflectsGroupMembershipForPasswordGrant() throws Exception {

        createTestClient(deleteMe, "secret", cfid);
        ScimUser user = createUser(deleteMe, "Passwo3d");
        createGroup(cfid, new ScimGroupMember(user.getId()));
        OAuth2AccessToken token = getAccessTokenWithPassword(deleteMe, "secret", deleteMe, "Passwo3d");
        assertTrue(token.getScope().contains(cfid), "Wrong token: " + token);

        deleteTestClient(deleteMe);
        deleteResource(userEndpoint, user.getId());

    }

    @BeforeEach
    public void initScimGroups() {
        scimGroups = new ArrayList<>();
    }

    @AfterEach
    public void teardownScimGroups() {
        for (ScimGroup scimGroup : scimGroups) {
            deleteResource(groupEndpoint, scimGroup.getId());
        }
    }

    @Test
    public void testExtremeGroupPagination() {
        for (int i = 0; i < 502; i++) {
            ScimUser user = createUser("deleteme_" + new RandomValueStringGenerator().generate().toLowerCase(), "Passwo3d");
            scimGroups.add(createGroup("cfid_" + new RandomValueStringGenerator().generate().toLowerCase(), new ScimGroupMember(user.getId())));
        }

        ResponseEntity<Map> response = client.getForEntity(serverRunning.getUrl(groupEndpoint + "?count=502"), Map.class);

        Map results = response.getBody();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat((Integer) results.get("totalResults"), greaterThan(500));
        assertThat((List<?>) results.get("resources"), hasSize(500));
        assertThat(results.get("itemsPerPage"), is(500));
        assertThat(results.get("startIndex"), is(1));

    }

    private void createTestClient(String name, String secret, String scope) {
        OAuth2AccessToken token = getClientCredentialsAccessToken("clients.read,clients.write,clients.admin");
        HttpHeaders headers = getAuthenticatedHeaders(token);
        UaaClientDetails client = new UaaClientDetails(name, "", scope, "authorization_code,password",
                "scim.read,scim.write", "http://redirect.uri");
        client.setClientSecret(secret);
        ResponseEntity<Void> result = serverRunning.getRestTemplate().exchange(serverRunning.getUrl("/oauth/clients"),
                HttpMethod.POST, new HttpEntity<>(client, headers), Void.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
    }

    private void deleteTestClient(String clientId) {
        OAuth2AccessToken token = getClientCredentialsAccessToken("clients.read,clients.write");
        HttpHeaders headers = getAuthenticatedHeaders(token);
        ResponseEntity<Void> result = serverRunning.getRestTemplate().exchange(
                serverRunning.getUrl("/oauth/clients/{client}"), HttpMethod.DELETE,
                new HttpEntity<Void>(headers),
                Void.class, clientId);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    private OAuth2AccessToken getClientCredentialsAccessToken(String scope) {

        String clientId = testAccounts.getAdminClientId();
        String clientSecret = testAccounts.getAdminClientSecret();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("scope", scope);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization",
                "Basic " + new String(Base64.encode("%s:%s".formatted(clientId, clientSecret).getBytes())));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = serverRunning.postForMap("/oauth/token", formData, headers);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        OAuth2AccessToken accessToken = DefaultOAuth2AccessToken.valueOf(response.getBody());
        return accessToken;

    }

    private HttpHeaders getAuthenticatedHeaders(OAuth2AccessToken token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token.getValue());
        return headers;
    }

    private OAuth2AccessToken getAccessTokenWithPassword(String clientId, String clientSecret, String username,
                                                         String password) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.set("Authorization", testAccounts.getAuthorizationHeader(clientId, clientSecret));
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> tokenResponse = serverRunning.postForMap("/oauth/token", formData, tokenHeaders);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        OAuth2AccessToken accessToken = DefaultOAuth2AccessToken.valueOf(tokenResponse.getBody());
        return accessToken;
    }

    private OAuth2AccessToken getAccessToken(String clientId, String clientSecret, String username, String password) throws URISyntaxException {
        BasicCookieStore cookies = new BasicCookieStore();

        URI uri = serverRunning.buildUri("/oauth/authorize").queryParam("response_type", "code")
                .queryParam("state", "mystateid").queryParam("client_id", clientId)
                .queryParam("redirect_uri", "http://redirect.uri").build();
        ResponseEntity<Void> result = serverRunning.createRestTemplate().exchange(
                uri.toString(), HttpMethod.GET, new HttpEntity<>(null, getHeaders(cookies)),
                Void.class);
        assertEquals(HttpStatus.FOUND, result.getStatusCode());
        String location = result.getHeaders().getLocation().toString();

        if (result.getHeaders().containsKey("Set-Cookie")) {
            for (String cookie : result.getHeaders().get("Set-Cookie")) {
                int nameLength = cookie.indexOf('=');
                cookies.addCookie(new BasicClientCookie(cookie.substring(0, nameLength), cookie.substring(nameLength + 1)));
            }
        }

        ResponseEntity<String> response = serverRunning.getForString(location, getHeaders(cookies));
        // should be directed to the login screen...
        assertTrue(response.getBody().contains("/login.do"));
        assertTrue(response.getBody().contains("username"));
        assertTrue(response.getBody().contains("password"));

        if (response.getHeaders().containsKey("Set-Cookie")) {
            String cookie = response.getHeaders().getFirst("Set-Cookie");
            int nameLength = cookie.indexOf('=');
            cookies.addCookie(new BasicClientCookie(cookie.substring(0, nameLength), cookie.substring(nameLength + 1)));
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, IntegrationTestUtils.extractCookieCsrf(response.getBody()));

        // Should be redirected to the original URL, but now authenticated
        result = serverRunning.postForResponse("/login.do", getHeaders(cookies), formData);
        assertEquals(HttpStatus.FOUND, result.getStatusCode());

        cookies.clear();
        if (result.getHeaders().containsKey("Set-Cookie")) {
            for (String cookie : result.getHeaders().get("Set-Cookie")) {
                int nameLength = cookie.indexOf('=');
                cookies.addCookie(new BasicClientCookie(cookie.substring(0, nameLength), cookie.substring(nameLength + 1)));
            }
        }

        response = serverRunning.createRestTemplate().exchange(
                new URI(result.getHeaders().getLocation().toString()),
                HttpMethod.GET,
                new HttpEntity<>(null, getHeaders(cookies)),
                String.class);
        if (response.getHeaders().containsKey("Set-Cookie")) {
            for (String cookie : response.getHeaders().get("Set-Cookie")) {
                int nameLength = cookie.indexOf('=');
                cookies.addCookie(new BasicClientCookie(cookie.substring(0, nameLength), cookie.substring(nameLength + 1)));
            }
        }
        if (response.getStatusCode() == HttpStatus.OK) {
            // The grant access page should be returned
            assertTrue(response.getBody().contains("<h1>Application Authorization</h1>"));

            formData.clear();
            formData.add(DEFAULT_CSRF_COOKIE_NAME, IntegrationTestUtils.extractCookieCsrf(response.getBody()));
            formData.add(USER_OAUTH_APPROVAL, "true");
            formData.add("scope.0", "scope." + cfid);
            result = serverRunning.postForResponse("/oauth/authorize", getHeaders(cookies), formData);
            assertEquals(HttpStatus.FOUND, result.getStatusCode());
            location = result.getHeaders().getLocation().toString();
        } else {
            // Token cached so no need for second approval
            assertEquals(HttpStatus.FOUND, response.getStatusCode());
            location = response.getHeaders().getLocation().toString();
        }
        assertTrue(location.matches("http://redirect.uri" + ".*code=.+"), "Wrong location: " + location);

        formData.clear();
        formData.add("client_id", clientId);
        formData.add("redirect_uri", "http://redirect.uri");
        formData.add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE);
        formData.add("code", location.split("code=")[1].split("&")[0]);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.set("Authorization", testAccounts.getAuthorizationHeader(clientId, clientSecret));
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> tokenResponse = serverRunning.postForMap("/oauth/token", formData, tokenHeaders);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        OAuth2AccessToken accessToken = DefaultOAuth2AccessToken.valueOf(tokenResponse.getBody());
        return accessToken;
    }

}