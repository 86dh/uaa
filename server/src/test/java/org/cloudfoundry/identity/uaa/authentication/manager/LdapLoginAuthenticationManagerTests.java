package org.cloudfoundry.identity.uaa.authentication.manager;

import org.cloudfoundry.identity.uaa.authentication.UaaAuthentication;
import org.cloudfoundry.identity.uaa.constants.OriginKeys;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.provider.IdentityProvider;
import org.cloudfoundry.identity.uaa.provider.IdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.provider.LdapIdentityProviderDefinition;
import org.cloudfoundry.identity.uaa.provider.ldap.extension.ExtendedLdapUserImpl;
import org.cloudfoundry.identity.uaa.provider.ldap.extension.LdapAuthority;
import org.cloudfoundry.identity.uaa.user.UaaAuthority;
import org.cloudfoundry.identity.uaa.user.UaaUser;
import org.cloudfoundry.identity.uaa.user.UaaUserDatabase;
import org.cloudfoundry.identity.uaa.user.UaaUserPrototype;
import org.cloudfoundry.identity.uaa.user.UserInfo;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.cloudfoundry.identity.uaa.provider.ExternalIdentityProviderDefinition.USER_ATTRIBUTE_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PollutionPreventionExtension.class)
class LdapLoginAuthenticationManagerTests {

    private static final String DN = "cn=marissa,ou=Users,dc=test,dc=com";
    private static final String LDAP_EMAIL = "test@ldap.org";
    private static final String TEST_EMAIL = "email@email.org";
    private static final String USERNAME = "username";
    private static final String EMAIL_ATTRIBUTE = "email";
    private static final String GIVEN_NAME_ATTRIBUTE = "firstname";
    private static final String FAMILY_NAME_ATTRIBUTE = "surname";
    private static final String PHONE_NUMBER_ATTTRIBUTE = "digits";
    private static LdapUserDetails userDetails;

    private static final String DENVER_CO = "Denver,CO";
    private static final String COST_CENTER = "costCenter";
    private static final String COST_CENTERS = "costCenters";
    private static final String JOHN_THE_SLOTH = "John the Sloth";
    private static final String KARI_THE_ANT_EATER = "Kari the Ant Eater";
    private static final String UAA_MANAGER = "uaaManager";
    private static final String MANAGERS = "managers";

    private LdapLoginAuthenticationManager am;
    private ApplicationEventPublisher publisher;
    private String origin = "test";
    private Map<String, String[]> info = new HashMap<>();
    private UaaUser dbUser;
    private Authentication auth;
    private ExtendedLdapUserImpl authUserDetail;
    private IdentityProviderProvisioning provisioning;
    private IdentityProvider provider;
    private LdapIdentityProviderDefinition definition;

    private static LdapUserDetails mockLdapUserDetails() {
        userDetails = mock(LdapUserDetails.class);
        setupGeneralExpectations(userDetails);
        when(userDetails.getDn()).thenReturn(DN);
        return userDetails;
    }

    private static UserDetails mockNonLdapUserDetails() {
        UserDetails userDetails = mock(UserDetails.class);
        setupGeneralExpectations(userDetails);
        return userDetails;
    }

    private static void setupGeneralExpectations(UserDetails userDetails) {
        when(userDetails.getUsername()).thenReturn(USERNAME);
        when(userDetails.getPassword()).thenReturn("koala");
        when(userDetails.getAuthorities()).thenReturn(null);
        when(userDetails.isAccountNonExpired()).thenReturn(true);
        when(userDetails.isAccountNonLocked()).thenReturn(true);
        when(userDetails.isCredentialsNonExpired()).thenReturn(true);
        when(userDetails.isEnabled()).thenReturn(true);
    }

    @BeforeEach
    void setUp() {
        IdentityZoneHolder.setProvisioning(null);

        dbUser = getUaaUser();
        provisioning = mock(IdentityProviderProvisioning.class);
        am = new LdapLoginAuthenticationManager(provisioning);
        publisher = mock(ApplicationEventPublisher.class);
        am.setApplicationEventPublisher(publisher);
        am.setOrigin(origin);
        authUserDetail = getAuthDetails(LDAP_EMAIL, "Marissa", "Bloggs", "8675309");
        auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(authUserDetail);

        UaaUserDatabase db = mock(UaaUserDatabase.class);
        when(db.retrieveUserById(anyString())).thenReturn(dbUser);
        am.setUserDatabase(db);
        when(auth.getAuthorities()).thenReturn(null);

        provider = mock(IdentityProvider.class);

        when(provisioning.retrieveByOrigin(anyString(), anyString())).thenReturn(provider);
        Map attributeMappings = new HashMap<>();
        definition = LdapIdentityProviderDefinition.searchAndBindMapGroupToScopes(
                "baseUrl",
                "bindUserDn",
                "bindPassword",
                "userSearchBase",
                "userSearchFilter",
                "grouSearchBase",
                "groupSearchFilter",
                "mailAttributeName",
                "mailSubstitute",
                false,
                false,
                false,
                1,
                false
        );
        definition.addAttributeMapping(USER_ATTRIBUTE_PREFIX + MANAGERS, UAA_MANAGER);
        definition.addAttributeMapping(USER_ATTRIBUTE_PREFIX + COST_CENTERS, COST_CENTER);
        when(provider.getConfig()).thenReturn(definition);
    }

    @Test
    void testGetUserWithExtendedLdapInfo() {
        UaaUser user = am.getUser(auth, null);
        assertEquals(DN, user.getExternalId());
        assertEquals(LDAP_EMAIL, user.getEmail());
        assertEquals(origin, user.getOrigin());
        assertFalse(user.isVerified());
    }

    @Test
    void testGetUserWithNonLdapInfo() {
        UserDetails mockNonLdapUserDetails = mockNonLdapUserDetails();
        when(mockNonLdapUserDetails.getUsername()).thenReturn(TEST_EMAIL);
        when(auth.getPrincipal()).thenReturn(mockNonLdapUserDetails);
        UaaUser user = am.getUser(auth, null);
        assertEquals(TEST_EMAIL, user.getExternalId());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertEquals(origin, user.getOrigin());
    }

    @Test
    void testUserAuthenticated() {
        UaaUser user = getUaaUser();
        UaaUser userFromRequest = am.getUser(auth, null);
        definition.setAutoAddGroups(true);
        UaaUser result = am.userAuthenticated(auth, user, userFromRequest);
        assertSame(dbUser, result);
        verify(publisher, times(1)).publishEvent(ArgumentMatchers.any());

        definition.setAutoAddGroups(false);
        result = am.userAuthenticated(auth, userFromRequest, user);
        assertSame(dbUser, result);
        verify(publisher, times(2)).publishEvent(ArgumentMatchers.any());
    }

    @Test
    void shadowUserCreationDisabledWillNotAddShadowUser() {
        definition.setAddShadowUserOnLogin(false);
        assertFalse(am.isAddNewShadowUser());
    }

    @Test
    void update_existingUser_if_attributes_different() {
        ExtendedLdapUserImpl authDetails = getAuthDetails(LDAP_EMAIL, "MarissaChanged", "BloggsChanged", "8675309");
        when(auth.getPrincipal()).thenReturn(authDetails);

        UaaUser user = getUaaUser();
        UaaUser userFromRequest = am.getUser(auth, null);
        am.userAuthenticated(auth, userFromRequest, user);
        ArgumentCaptor<ExternalGroupAuthorizationEvent> captor = ArgumentCaptor.forClass(ExternalGroupAuthorizationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());

        assertEquals(LDAP_EMAIL, captor.getValue().getUser().getEmail());
        assertEquals("MarissaChanged", captor.getValue().getUser().getGivenName());
        assertEquals("BloggsChanged", captor.getValue().getUser().getFamilyName());
    }

    @Test
    void dontUpdate_existingUser_if_attributes_same() {
        UaaUser user = getUaaUser();
        ExtendedLdapUserImpl authDetails = getAuthDetails(user.getEmail(), user.getGivenName(), user.getFamilyName(), user.getPhoneNumber());
        when(auth.getPrincipal()).thenReturn(authDetails);

        UaaUser userFromRequest = am.getUser(auth, null);
        am.userAuthenticated(auth, userFromRequest, user);
        ArgumentCaptor<ExternalGroupAuthorizationEvent> captor = ArgumentCaptor.forClass(ExternalGroupAuthorizationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());

        assertEquals(user.getModified(), captor.getValue().getUser().getModified());
    }

    @Test
    void test_authentication_attributes() {
        test_authentication_attributes(false);
    }

    @Test
    void test_authentication_attributes_store_custom_attributes() {
        test_authentication_attributes(true);
    }

    @Test
    void test_group_white_list_with_wildcard() {
        UaaUser user = getUaaUser();
        ExtendedLdapUserImpl authDetails =
                getAuthDetails(
                        user.getEmail(),
                        user.getGivenName(),
                        user.getFamilyName(),
                        user.getPhoneNumber(),
                        new AttributeInfo(UAA_MANAGER, new String[]{KARI_THE_ANT_EATER, JOHN_THE_SLOTH}),
                        new AttributeInfo(COST_CENTER, new String[]{DENVER_CO})
                );
        Map<String, String[]> role1 = new HashMap<>();
        role1.put("cn", new String[]{"ldap.role.1.a", "ldap.role.1.b", "ldap.role.1"});
        Map<String, String[]> role2 = new HashMap<>();
        role2.put("cn", new String[]{"ldap.role.2.a", "ldap.role.2.b", "ldap.role.2"});
        authDetails.setAuthorities(
                Arrays.asList(
                        new LdapAuthority("role1", "cn=role1,ou=test,ou=com", role1),
                        new LdapAuthority("role2", "cn=role2,ou=test,ou=com", role2)

                )
        );


        definition.setExternalGroupsWhitelist(emptyList());
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder()
        );

        definition.setExternalGroupsWhitelist(null);
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder()
        );

        definition.setExternalGroupsWhitelist(Collections.singletonList("ldap.role.1.a"));
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder("ldap.role.1.a")
        );

        definition.setExternalGroupsWhitelist(Arrays.asList("ldap.role.1.a", "ldap.role.2.*"));
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder("ldap.role.1.a", "ldap.role.2.a", "ldap.role.2.b")
        );


        definition.setExternalGroupsWhitelist(Collections.singletonList("ldap.role.*.*"));
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder("ldap.role.1.a", "ldap.role.1.b", "ldap.role.2.a", "ldap.role.2.b")
        );

        definition.setExternalGroupsWhitelist(Arrays.asList("ldap.role.*.*", "ldap.role.*"));
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder("ldap.role.1.a", "ldap.role.1.b", "ldap.role.1", "ldap.role.2.a", "ldap.role.2.b", "ldap.role.2")
        );

        definition.setExternalGroupsWhitelist(Collections.singletonList("ldap*"));
        assertThat(am.getExternalUserAuthorities(authDetails),
                containsInAnyOrder("ldap.role.1.a", "ldap.role.1.b", "ldap.role.1", "ldap.role.2.a", "ldap.role.2.b", "ldap.role.2")
        );
    }

    void test_authentication_attributes(boolean storeUserInfo) {

        UaaUser user = getUaaUser();
        ExtendedLdapUserImpl authDetails =
                getAuthDetails(
                        user.getEmail(),
                        user.getGivenName(),
                        user.getFamilyName(),
                        user.getPhoneNumber(),
                        new AttributeInfo(UAA_MANAGER, new String[]{KARI_THE_ANT_EATER, JOHN_THE_SLOTH}),
                        new AttributeInfo(COST_CENTER, new String[]{DENVER_CO})
                );

        Map<String, String[]> role1 = new HashMap<>();
        role1.put("cn", new String[]{"ldap.role.1.a", "ldap.role.1.b", "ldap.role.1"});
        Map<String, String[]> role2 = new HashMap<>();
        role2.put("cn", new String[]{"ldap.role.2.a", "ldap.role.2.b", "ldap.role.2"});
        authDetails.setAuthorities(
                Arrays.asList(
                        new LdapAuthority("role1", "cn=role1,ou=test,ou=com", role1),
                        new LdapAuthority("role2", "cn=role2,ou=test,ou=com", role2)

                )
        );
        definition.setExternalGroupsWhitelist(Collections.singletonList("*"));
        when(auth.getPrincipal()).thenReturn(authDetails);

        UaaUserDatabase db = mock(UaaUserDatabase.class);
        when(db.retrieveUserByName(anyString(), eq(OriginKeys.LDAP))).thenReturn(user);
        when(db.retrieveUserById(anyString())).thenReturn(user);
        am.setOrigin(OriginKeys.LDAP);
        am.setUserDatabase(db);


        //set the config flag
        definition.setStoreCustomAttributes(storeUserInfo);

        UaaAuthentication authentication = (UaaAuthentication) am.authenticate(auth);
        UserInfo info = new UserInfo()
                .setUserAttributes(authentication.getUserAttributes())
                .setRoles(Arrays.asList("ldap.role.1.a", "ldap.role.1.b", "ldap.role.1", "ldap.role.2.a", "ldap.role.2.b", "ldap.role.2"));
        if (storeUserInfo) {
            verify(db, times(1)).storeUserInfo(anyString(), eq(info));
        } else {
            verify(db, never()).storeUserInfo(anyString(), eq(info));
        }

        assertEquals(2, authentication.getUserAttributes().size(), "Expected two user attributes");
        assertNotNull(authentication.getUserAttributes().get(COST_CENTERS), "Expected cost center attribute");
        assertEquals(DENVER_CO, authentication.getUserAttributes().getFirst(COST_CENTERS));

        assertNotNull(authentication.getUserAttributes().get(MANAGERS), "Expected manager attribute");
        assertEquals(2, authentication.getUserAttributes().get(MANAGERS).size(), "Expected 2 manager attribute values");
        assertThat(authentication.getUserAttributes().get(MANAGERS), containsInAnyOrder(JOHN_THE_SLOTH, KARI_THE_ANT_EATER));

        assertThat(authentication.getAuthenticationMethods(), containsInAnyOrder("ext", "pwd"));


    }

    private ExtendedLdapUserImpl getAuthDetails(String email, String givenName, String familyName, String phoneNumber, AttributeInfo... attributes) {
        String[] emails = {email};
        String[] givenNames = {givenName};
        String[] familyNames = {familyName};
        String[] phoneNumbers = {phoneNumber};
        info.put(EMAIL_ATTRIBUTE, emails);
        info.put(GIVEN_NAME_ATTRIBUTE, givenNames);
        info.put(FAMILY_NAME_ATTRIBUTE, familyNames);
        info.put(PHONE_NUMBER_ATTTRIBUTE, phoneNumbers);
        for (AttributeInfo i : attributes) {
            info.put(i.getName(), i.getValues());
        }

        authUserDetail = new ExtendedLdapUserImpl(mockLdapUserDetails(), info);
        authUserDetail.setMailAttributeName(EMAIL_ATTRIBUTE);
        authUserDetail.setGivenNameAttributeName(GIVEN_NAME_ATTRIBUTE);
        authUserDetail.setFamilyNameAttributeName(FAMILY_NAME_ATTRIBUTE);
        authUserDetail.setPhoneNumberAttributeName(PHONE_NUMBER_ATTTRIBUTE);
        return authUserDetail;
    }

    UaaUser getUaaUser() {
        return new UaaUser(new UaaUserPrototype()
                .withId("id")
                .withUsername(USERNAME)
                .withPassword("password")
                .withEmail(TEST_EMAIL)
                .withAuthorities(UaaAuthority.USER_AUTHORITIES)
                .withGivenName("givenname")
                .withFamilyName("familyname")
                .withPhoneNumber("8675309")
                .withCreated(new Date())
                .withModified(new Date())
                .withOrigin(OriginKeys.ORIGIN)
                .withExternalId(DN)
                .withVerified(false)
                .withZoneId(IdentityZoneHolder.get().getId())
                .withSalt(null)
                .withPasswordLastModified(null));
    }


    public static class AttributeInfo {
        final String name;
        final String[] values;

        AttributeInfo(String name, String[] values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public String[] getValues() {
            return values;
        }
    }
}
