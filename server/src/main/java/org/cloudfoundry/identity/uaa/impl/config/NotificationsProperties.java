package org.cloudfoundry.identity.uaa.impl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "notifications")
record NotificationsProperties(
        String url,
        @DefaultValue("true") boolean sendInDefaultZone
) {
}
