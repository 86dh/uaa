package org.cloudfoundry.identity.uaa.oauth.common;

import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidClientException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidGrantException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidRequestException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidScopeException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.InvalidTokenException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.OAuth2Exception;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.RedirectMismatchException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.UnauthorizedClientException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.UnsupportedGrantTypeException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.UnsupportedResponseTypeException;
import org.cloudfoundry.identity.uaa.oauth.common.exceptions.UserDeniedAuthorizationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moved test class of from spring-security-oauth2 into UAA
 * Scope: Test class
 */
public class DefaultOAuth2SerializationServiceTests {

    @Test
    public void testDefaultDeserialization() {
        Map<String, String> accessToken = Map.of("access_token", "FOO", "expires_in", "100", "token_type", "mac", "scope", "test,ok", "refresh_token", "");
        OAuth2AccessToken result = DefaultOAuth2AccessToken.valueOf(accessToken);
        // System.err.println(result);
        assertEquals("FOO", result.getValue());
        assertEquals("mac", result.getTokenType());
        assertTrue(result.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    public void testDefaultDeserializationException() {
        Map<String, String> accessToken = Map.of("access_token", "FOO", "expires_in", "x");
        DefaultOAuth2AccessToken result = (DefaultOAuth2AccessToken) DefaultOAuth2AccessToken.valueOf(accessToken);
        assertNotEquals(0, result.getExpiration().getTime());
        assertEquals(0, result.getExpiresIn());
        result.setExpiresIn(300);
        assertEquals(0, result.getExpiresIn());
        assertNotEquals(0, result.hashCode());
    }

    @Test
    public void testDefaultDeserializationEquals() {
        Map<String, String> accessToken = Map.of("access_token", "FOO", "expires_in", "x");
        DefaultOAuth2AccessToken result = (DefaultOAuth2AccessToken) DefaultOAuth2AccessToken.valueOf(accessToken);
        DefaultOAuth2AccessToken result2 = new DefaultOAuth2AccessToken("bar");
        assertNotEquals(result, result2);
        result2.setValue("FOO");
        assertEquals(result, result2);
        DefaultOAuth2RefreshToken refreshToken = new DefaultOAuth2RefreshToken("bar");
        assertNotEquals(refreshToken, result2);
        assertNotEquals(refreshToken.hashCode(), result2.hashCode());
        assertNotEquals(refreshToken.toString(), result2.toString());
    }

    @Test
    public void testExceptionDeserialization() {
        Map<String, String> exception = MapBuilder.create("error", "invalid_client").add("error_description", "FOO")
                .build();
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        // System.err.println(result);
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_client", result.getOAuth2ErrorCode());
        assertTrue(result instanceof InvalidClientException);
    }

    @Test
    public void testExceptionDeserialization2() {
        Map<String, String> exception = Map.of("error", "unauthorized_client", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("unauthorized_client", result.getOAuth2ErrorCode());
        assertTrue(result instanceof UnauthorizedClientException);
    }

    @Test
    public void testExceptionDeserializationInvalid_grant() {
        Map<String, String> exception = Map.of("error", "invalid_grant", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        result.addAdditionalInformation("hint", "unknown code");
        assertNotNull(result.getSummary());
        assertNotNull(result.toString());
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_grant", result.getOAuth2ErrorCode());
        assertTrue(result instanceof InvalidGrantException);
    }


    @Test
    public void testExceptionInvalidTokenException() {
        Map<String, String> exception = Map.of("error", "invalid_token", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_token", result.getOAuth2ErrorCode());
        assertTrue(result instanceof InvalidTokenException);
    }

    @Test
    public void testExceptionInvalidRequestException() {
        Map<String, String> exception = Map.of("error", "invalid_request", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_request", result.getOAuth2ErrorCode());
        assertTrue(result instanceof InvalidRequestException);
    }

    @Test
    public void testExceptionUnsupportedGrantTypeException() {
        Map<String, String> exception = Map.of("error", "unsupported_grant_type", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("unsupported_grant_type", result.getOAuth2ErrorCode());
        assertTrue(result instanceof UnsupportedGrantTypeException);
    }

    @Test
    public void testExceptionUnsupportedResponseTypeException() {
        Map<String, String> exception = Map.of("error", "unsupported_response_type", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("unsupported_response_type", result.getOAuth2ErrorCode());
        assertTrue(result instanceof UnsupportedResponseTypeException);
    }

    @Test
    public void testExceptionRedirectMismatchException() {
        Map<String, String> exception = Map.of("error", "redirect_uri_mismatch", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_grant", result.getOAuth2ErrorCode());
        assertTrue(result instanceof RedirectMismatchException);
    }

    @Test
    public void testExceptionUserDeniedAuthorizationException() {
        Map<String, String> exception = Map.of("error", "access_denied", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("access_denied", result.getOAuth2ErrorCode());
        assertTrue(result instanceof UserDeniedAuthorizationException);
    }

    @Test
    public void testExceptionInvalidScopeException() {
        Map<String, String> exception = Map.of("error", "invalid_scope", "error_description", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("FOO", result.getMessage());
        assertEquals("invalid_scope", result.getOAuth2ErrorCode());
        assertTrue(result instanceof InvalidScopeException);
    }

    @Test
    public void testExceptionBadException() {
        Map<String, String> exception = Map.of("errortest", "xx", "bar", "FOO");
        OAuth2Exception result = OAuth2Exception.valueOf(exception);
        assertNotNull(result.getSummary());
        assertEquals("OAuth Error", result.getMessage());
        assertEquals("invalid_request", result.getOAuth2ErrorCode());
        assertTrue(result instanceof OAuth2Exception);
    }

    private static final class MapBuilder {

        private final HashMap<String, String> map = new HashMap<>();

        private MapBuilder(String key, String value) {
            map.put(key, value);
        }

        public static MapBuilder create(String key, String value) {
            return new MapBuilder(key, value);
        }

        public MapBuilder add(String key, String value) {
            map.put(key, value);
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }
}
