package com.connecthub.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiGatewayApplication Tests")
class ApiGatewayApplicationTest {

    @Test
    @DisplayName("ApiGatewayApplication class can be instantiated")
    void classCanBeInstantiated() {
        assertThat(ApiGatewayApplication.class).isNotNull();
    }
}