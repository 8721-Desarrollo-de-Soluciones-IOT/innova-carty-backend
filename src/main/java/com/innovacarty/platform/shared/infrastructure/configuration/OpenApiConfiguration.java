package com.innovacarty.platform.shared.infrastructure.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html (Services Documentation of the report). */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI innovaCartyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Innova Carty Platform API")
                        .version("v1")
                        .description("RESTful API of Innova Carty: smart carts (ESP32), Web Console and mobile app. "
                                + "Use POST /api/v1/authentication/sign-in and paste the token in Authorize. "
                                + "Device endpoints use the X-Device-Key header and the payment webhook uses X-Webhook-Secret."))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("deviceKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Device-Key"))
                        .addSecuritySchemes("webhookSecret", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Webhook-Secret")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .addSecurityItem(new SecurityRequirement().addList("deviceKey"))
                .addSecurityItem(new SecurityRequirement().addList("webhookSecret"));
    }
}
