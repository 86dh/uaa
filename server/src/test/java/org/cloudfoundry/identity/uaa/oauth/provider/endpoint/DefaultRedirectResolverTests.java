package org.cloudfoundry.identity.uaa.oauth.provider.endpoint;

import org.cloudfoundry.identity.uaa.client.UaaClientDetails;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidGrantException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidRequestException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.RedirectMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Moved test class of from spring-security-oauth2 into UAA
 * Scope: Test class
 */
class DefaultRedirectResolverTests {

    private DefaultRedirectResolver resolver;

    private UaaClientDetails client;

    @BeforeEach
    void setup() {
        client = new UaaClientDetails();
        client.setAuthorizedGrantTypes(Collections.singleton("authorization_code"));
        resolver = new DefaultRedirectResolver();
    }

    @Test
    void testRedirectMatchesRegisteredValue() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    @Test
    void testRedirectWithNoRegisteredValue() {
        assertThrows(InvalidRequestException.class, () -> {
            String requestedRedirect = "https://anywhere.com/myendpoint";
            resolver.resolveRedirect(requestedRedirect, client);
        });
    }

    // If only one redirect has been registered, then we should use it
    @Test
    void testRedirectWithNoRequestedValue() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com"));
        client.setRegisteredRedirectUri(redirectUris);
        resolver.resolveRedirect(null, client);
    }

    // If multiple redirects registered, then we should get an exception
    @Test
    void testRedirectWithNoRequestedValueAndMultipleRegistered() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com", "https://nowhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect(null, client);
        });
    }

    @Test
    void testNoGrantType() {
        assertThrows(InvalidGrantException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com", "https://nowhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            client.setAuthorizedGrantTypes(Collections.<String>emptyList());
            resolver.resolveRedirect(null, client);
        });
    }

    @Test
    void testWrongGrantType() {
        assertThrows(InvalidGrantException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com", "https://nowhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            client.setAuthorizedGrantTypes(Collections.singleton("client_credentials"));
            resolver.resolveRedirect(null, client);
        });
    }

    @Test
    void testWrongCustomGrantType() {
        assertThrows(InvalidGrantException.class, () -> {
            resolver.setRedirectGrantTypes(Collections.singleton("foo"));
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com", "https://nowhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect(null, client);
        });
    }

    @Test
    void testRedirectNotMatching() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://nowhere.com"));
            String requestedRedirect = "https://anywhere.com/myendpoint";
            client.setRegisteredRedirectUri(redirectUris);
            assertEquals(redirectUris.iterator().next(), resolver.resolveRedirect(requestedRedirect, client));
        });
    }

    @Test
    void testRedirectNotMatchingWithTraversal() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/foo"));
            String requestedRedirect = "https://anywhere.com/foo/../bar";
            client.setRegisteredRedirectUri(redirectUris);
            assertEquals(redirectUris.iterator().next(), resolver.resolveRedirect(requestedRedirect, client));
        });
    }

    // gh-1331
    @Test
    void testRedirectNotMatchingWithHexEncodedTraversal() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/foo"));
            client.setRegisteredRedirectUri(redirectUris);
            String requestedRedirect = "https://anywhere.com/foo/%2E%2E";    // hexadecimal encoding of '..' represents '%2E%2E'
            resolver.resolveRedirect(requestedRedirect, client);
        });
    }

    // gh-747
    @Test
    void testRedirectNotMatchingSubdomain() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/foo"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://2anywhere.com/foo", client);
        });
    }

    // gh-747
    // gh-747
    @Test
    void testRedirectMatchingSubdomain() {
        resolver.setMatchSubdomains(true);
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/foo"));
        String requestedRedirect = "https://2.anywhere.com/foo";
        client.setRegisteredRedirectUri(redirectUris);
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    @Test
    void testRedirectMatchSubdomainsDefaultsFalse() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://2.anywhere.com", client);
        });
    }

    // gh-746
    @Test
    void testRedirectNotMatchingPort() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com:90"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com:91/foo", client);
        });
    }

    // gh-746
    @Test
    void testRedirectMatchingPort() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com:90"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com:90";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-746
    @Test
    void testRedirectRegisteredPortSetRequestedPortNotSet() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com:90"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com/foo", client);
        });
    }

    // gh-746
    @Test
    void testRedirectRegisteredPortNotSetRequestedPortSet() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com:8443/foo", client);
        });
    }

    // gh-746
    @Test
    void testRedirectMatchPortsFalse() {
        resolver.setMatchPorts(false);
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com:90"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com:90";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1386
    @Test
    void testRedirectNotMatchingReturnsGenericErrorMessage() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://nowhere.com"));
        String requestedRedirect = "https://anywhere.com/myendpoint";
        client.setRegisteredRedirectUri(redirectUris);
        try {
            resolver.resolveRedirect(requestedRedirect, client);
            fail();
        } catch (RedirectMismatchException ex) {
            assertEquals("Invalid redirect uri does not match one of the registered values.", ex.getMessage());
        }
    }

    // gh-1566
    @Test
    void testRedirectRegisteredUserInfoNotMatching() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://userinfo@anywhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://otheruserinfo@anywhere.com", client);
        });
    }

    // gh-1566
    @Test
    void testRedirectRegisteredNoUserInfoNotMatching() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://userinfo@anywhere.com"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com", client);
        });
    }

    // gh-1566
    @Test
    void testRedirectRegisteredUserInfoMatching() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://userinfo@anywhere.com"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://userinfo@anywhere.com";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredFragmentIgnoredAndStripped() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://userinfo@anywhere.com/foo/bar#baz"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://userinfo@anywhere.com/foo/bar";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect + "#bar", client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsMatching() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1&p2=v2"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com/?p1=v1&p2=v2";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsMatchingIgnoringAdditionalParams() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1&p2=v2"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com/?p1=v1&p2=v2&p3=v3";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsMatchingDifferentOrder() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1&p2=v2"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com/?p2=v2&p1=v1";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsWithDifferentValues() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1&p2=v2"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com/?p1=v1&p2=v3", client);
        });
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsNotMatching() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com/?p2=v2", client);
        });
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsPartiallyMatching() {
        assertThrows(RedirectMismatchException.class, () -> {
            Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v1&p2=v2"));
            client.setRegisteredRedirectUri(redirectUris);
            resolver.resolveRedirect("https://anywhere.com/?p2=v2&p3=v3", client);
        });
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsMatchingWithMultipleValuesInRegistered() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1=v11&p1=v12"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com/?p1=v11&p1=v12";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1566
    @Test
    void testRedirectRegisteredQueryParamsMatchingWithParamWithNoValue() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("https://anywhere.com/?p1&p2=v2"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "https://anywhere.com/?p1&p2=v2";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }

    // gh-1618
    @Test
    void testRedirectNoHost() {
        Set<String> redirectUris = new HashSet<>(Arrays.asList("scheme:/path"));
        client.setRegisteredRedirectUri(redirectUris);
        String requestedRedirect = "scheme:/path";
        assertEquals(requestedRedirect, resolver.resolveRedirect(requestedRedirect, client));
    }
}
