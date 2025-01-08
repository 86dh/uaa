package org.cloudfoundry.identity.uaa.oauth;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeyInfoTest {

    private static final String sampleRsaPrivateKey = """
            -----BEGIN RSA PRIVATE KEY-----
            MIICXgIBAAKBgQDfTLadf6QgJeS2XXImEHMsa+1O7MmIt44xaL77N2K+J/JGpfV3
            AnkyB06wFZ02sBLB7hko42LIsVEOyTuUBird/3vlyHFKytG7UEt60Fl88SbAEfsU
            JN1i1aSUlunPS/NCz+BKwwKFP9Ss3rNImE9Uc2LMvGy153LHFVW2zrjhTwIDAQAB
            AoGBAJDh21LRcJITRBQ3CUs9PR1DYZPl+tUkE7RnPBMPWpf6ny3LnDp9dllJeHqz
            a3ACSgleDSEEeCGzOt6XHnrqjYCKa42Z+Opnjx/OOpjyX1NAaswRtnb039jwv4gb
            RlwT49Y17UAQpISOo7JFadCBoMG0ix8xr4ScY+zCSoG5v0BhAkEA8llNsiWBJF5r
            LWQ6uimfdU2y1IPlkcGAvjekYDkdkHiRie725Dn4qRiXyABeaqNm2bpnD620Okwr
            sf7LY+BMdwJBAOvgt/ZGwJrMOe/cHhbujtjBK/1CumJ4n2r5V1zPBFfLNXiKnpJ6
            J/sRwmjgg4u3Anu1ENF3YsxYabflBnvOP+kCQCQ8VBCp6OhOMcpErT8+j/gTGQUL
            f5zOiPhoC2zTvWbnkCNGlqXDQTnPUop1+6gILI2rgFNozoTU9MeVaEXTuLsCQQDC
            AGuNpReYucwVGYet+LuITyjs/krp3qfPhhByhtndk4cBA5H0i4ACodKyC6Zl7Tmf
            oYaZoYWi6DzbQQUaIsKxAkEA2rXQjQFsfnSm+w/9067ChWg46p4lq5Na2NpcpFgH
            waZKhM1W0oB8MX78M+0fG3xGUtywTx0D4N7pr1Tk2GTgNw==
            -----END RSA PRIVATE KEY-----""";


    @Test
    public void HmacKeyShouldSetFieldsCorrectly() {
        KeyInfo hmacKeyInfo = new KeyInfo("key-id", "secret", "https://localhost");

        assertThat(hmacKeyInfo.type(), is("MAC"));
    }

    @Test
    public void HmacKeyShouldSetKeyUrlWithASecureProtocol() {
        KeyInfo hmacKeyInfo = new KeyInfo("key-id", "secret", "http://localhost/path2");

        assertThat(hmacKeyInfo.keyURL(), is("https://localhost/path2/token_keys"));
    }

    @Test
    public void RsaKeyShouldSetFieldsCorrectly() {
        KeyInfo keyInfo = new KeyInfo("key-id", sampleRsaPrivateKey, "https://localhost");

        assertThat(keyInfo.type(), is("RSA"));
    }

    @Test
    public void Rsa512KeyShouldSetFieldsCorrectly() {
        KeyInfo keyInfo = new KeyInfo("key-id", sampleRsaPrivateKey, "https://localhost", "RS512", null);

        assertThat(keyInfo.type(), is("RSA"));
        assertThat(keyInfo.algorithm(), is("RS512"));
    }

    @Test
    public void RsaKeyShouldSetKeyUrlWithASecureProtocol() {
        KeyInfo keyInfo = new KeyInfo("key-id", sampleRsaPrivateKey, "http://localhost/path");

        assertThat(keyInfo.keyURL(), is("https://localhost/path/token_keys"));
    }

    @Test
    public void creatingHmacKeyWithInvalidUrlShouldFail() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

            new KeyInfo("id", "secret", "foo bar");
        });
        assertTrue(exception.getMessage().contains("Invalid Key URL"));
    }


    @Test
    public void creatingRsaKeyWithInvalidUrlShouldFail() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

            new KeyInfo("id", "secret", "foo bar");
        });
        assertTrue(exception.getMessage().contains("Invalid Key URL"));
    }
}