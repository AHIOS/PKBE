package com.uci.pkbe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pkbeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PKBE API")
                        .version("0.1.0")
                        .description(
                                """
                                Proximity passkey activation relying party.

                                Stub-login, enroll a platform passkey, then hand activation to a nearby
                                device with WebAuthn hybrid (caBLE). One ACTIVE device at a time.

                                Authenticated `/v1/*` routes (except login and public-config)
                                require `Authorization: Bearer <token>` from `POST /v1/login`.
                                """))
                .addServersItem(new Server().url("/").description("This instance"))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("opaque")
                                        .description("Session token from POST /v1/login")));
    }
}
