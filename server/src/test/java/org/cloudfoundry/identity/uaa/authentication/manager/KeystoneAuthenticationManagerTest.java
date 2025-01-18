package org.cloudfoundry.identity.uaa.authentication.manager;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeystoneAuthenticationManagerTest {

    private String remoteUrl;
    private RestAuthenticationManager restAuthenticationManager;
    private UsernamePasswordAuthenticationToken input;
    private final String username = "testUserName";

    public void initKeystoneAuthenticationManagerTest(RestAuthenticationManager authzManager, String url) {
        restAuthenticationManager = authzManager;
        remoteUrl = url;
        String password = "testpassword";
        input = new UsernamePasswordAuthenticationToken(username, password);
        setUpRestAuthenticationManager(HttpStatus.OK);
    }

    private void setUpRestAuthenticationManager(HttpStatus status) {
        Map<String, Object> restResult = new HashMap<>();
        if (remoteUrl.contains("/v3")) {
            Map<String, Object> token = new HashMap<>();
            Map<String, Object> user = new HashMap<>();
            restResult.put("token", token);
            token.put("user", user);
            user.put("name", username);
        } else if (remoteUrl.contains("/v2.0")) {
            Map<String, Object> user = new HashMap<>();
            Map<String, Object> access = new HashMap<>();
            user.put("username", username);
            access.put("user", user);
            restResult.put("access", access);
        } else {
            restResult.put("username", username);
        }

        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq(remoteUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(restResult, status));

        restAuthenticationManager.setNullPassword(false);
        restAuthenticationManager.setRemoteUrl(remoteUrl);
        restAuthenticationManager.setRestTemplate(restTemplate);
    }

    public static Stream<Arguments> parameters() {
        return Stream.of(
                arguments(new KeystoneAuthenticationManager(), "http://this.is.not.used/v3"),
                arguments(new KeystoneAuthenticationManager(), "http://this.is.not.used/v2.0"),
                arguments(new RestAuthenticationManager(), "http://this.is.not.used/authenticate")
        );
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void v3Authentication(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        restAuthenticationManager.authenticate(input);
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void unknownVersion(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        Assumptions.assumeTrue(restAuthenticationManager instanceof KeystoneAuthenticationManager);
        remoteUrl = "http://this.is.not.used/v4";
        setUpRestAuthenticationManager(HttpStatus.OK);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
                restAuthenticationManager.authenticate(input));
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void unauthorized(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        setUpRestAuthenticationManager(HttpStatus.UNAUTHORIZED);
        assertThatExceptionOfType(BadCredentialsException.class).isThrownBy(() ->
                restAuthenticationManager.authenticate(input));
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void test500Error(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        setUpRestAuthenticationManager(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() ->
                restAuthenticationManager.authenticate(input));
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void unknownError(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        setUpRestAuthenticationManager(HttpStatus.BAD_GATEWAY);
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() ->
                restAuthenticationManager.authenticate(input));
    }

    @MethodSource("parameters")
    @ParameterizedTest
    void checkNullPassword(RestAuthenticationManager authzManager, String url) {
        initKeystoneAuthenticationManagerTest(authzManager, url);
        assertThat(restAuthenticationManager.isNullPassword()).isFalse();
    }
}
