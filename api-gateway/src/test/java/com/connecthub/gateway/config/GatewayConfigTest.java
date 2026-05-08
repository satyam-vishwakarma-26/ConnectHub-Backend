package com.connecthub.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayConfig Tests")
class GatewayConfigTest {

    @Test
    @DisplayName("GatewayConfig can be instantiated")
    void canBeInstantiated() {
        GatewayConfig config = new GatewayConfig();
        assertThat(config).isNotNull();
    }
}