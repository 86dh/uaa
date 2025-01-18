package org.cloudfoundry.identity.uaa.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.cloudfoundry.identity.uaa.metrics.UrlGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class JsonUtilsTest {
    private static final String JSON_TEST_OBJECT_STRING = "{\"pattern\":\"/pattern\",\"group\":\"group\",\"limit\":1000,\"category\":\"category\"}";

    @Test
    void writeValueAsString() {
        String testObjectString = JsonUtils.writeValueAsString(getTestObject());
        assertThat(testObjectString).isEqualTo(JSON_TEST_OBJECT_STRING);
    }

    @Test
    void writeValueAsBytes() {
        byte[] testObject = JsonUtils.writeValueAsBytes(getTestObject());
        assertThat(testObject).isNotNull();
        assertThat(new String(testObject)).isEqualTo(JSON_TEST_OBJECT_STRING);
    }

    @Test
    void testreadValueStringClass() {
        assertThat(JsonUtils.readValue(JSON_TEST_OBJECT_STRING, UrlGroup.class)).isNotNull();
        assertThat(JsonUtils.readValue((String) null, UrlGroup.class)).isNull();
    }

    @Test
    void readValueByteClass() {
        assertThat(JsonUtils.readValue(JSON_TEST_OBJECT_STRING.getBytes(), UrlGroup.class)).isNotNull();
        assertThat(JsonUtils.readValue((byte[]) null, UrlGroup.class)).isNull();
    }

    @Test
    void readValueAsMap() {
        final String jsonInput = "{\"prop1\":\"abc\",\"prop2\":{\"prop2a\":\"def\",\"prop2b\":\"ghi\"},\"prop3\":[\"jkl\",\"mno\"]}";
        final Map<String, Object> map = JsonUtils.readValueAsMap(jsonInput);
        assertThat(map).containsEntry("prop1", "abc")
                .containsKey("prop3");
        assertThat(((Map<String, Object>) map.get("prop2")))
                .isInstanceOf(Map.class)
                .containsEntry("prop2a", "def")
                .containsEntry("prop2b", "ghi");
        assertThat((List<String>) map.get("prop3"))
                .isInstanceOf(List.class)
                .containsExactly("jkl", "mno");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "}", "{\"prop1\":\"abc\","})
    void readValueAsMapInvalid(final String input) {
        assertThatExceptionOfType(JsonUtils.JsonUtilException.class)
                .isThrownBy(() -> JsonUtils.readValueAsMap(input));
    }

    @Test
    void readValueBytes() {
        assertThat(JsonUtils.readValue(JSON_TEST_OBJECT_STRING.getBytes(), new TypeReference<Map<String, Object>>() {
        })).isNotNull();
        assertThat(JsonUtils.readValue((byte[]) null, new TypeReference<Map<String, Object>>() {
        })).isNull();
    }

    @Test
    void readValueString() {
        assertThat(JsonUtils.readValue(JSON_TEST_OBJECT_STRING, new TypeReference<Map<String, Object>>() {
        })).isNotNull();
        assertThat(JsonUtils.readValue((String) null, new TypeReference<Map<String, Object>>() {
        })).isNull();
    }

    @Test
    void convertValue() {
        assertThat(JsonUtils.convertValue(null, UrlGroup.class)).isNull();
    }

    @Test
    void serializeExcludingProperties() {
        Map<String, String> groupProperties = JsonUtils.readValue(JSON_TEST_OBJECT_STRING, new TypeReference<>() {
        });
        String resultString = JsonUtils.serializeExcludingProperties(groupProperties, "group", "pattern", "any.limit", "category");
        assertThat(resultString).isEqualTo("{\"limit\":\"1000\"}");
    }

    @Test
    void serializeExcludingPropertiesInnerCallFails() {
        Map<String, String> groupProperties = JsonUtils.readValue(JSON_TEST_OBJECT_STRING, new TypeReference<>() {
        });
        assertThatExceptionOfType(JsonUtils.JsonUtilException.class).isThrownBy(() ->
                JsonUtils.serializeExcludingProperties(groupProperties, "limit.unknown"));
    }

    @Test
    void hasLength() {
        assertThat(JsonUtils.hasLength("X")).isTrue();
        assertThat(JsonUtils.hasLength("")).isFalse();
    }

    @Test
    void hasText() {
        assertThat(JsonUtils.hasText("X")).isTrue();
        assertThat(JsonUtils.hasText(" ")).isFalse();
    }

    private Object getTestObject() {
        return new UrlGroup().setCategory("category").setGroup("group").setPattern("/pattern").setLimit(1_000L);
    }
}
