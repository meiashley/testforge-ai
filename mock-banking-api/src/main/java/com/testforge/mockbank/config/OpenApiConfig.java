package com.testforge.mockbank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mockBankingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mock Banking Payment API")
                        .description("""
                                A mock financial payment API used as the demo target for **TestForge AI** \
                                automated test generation.

                                Supports payment creation, retrieval, and full/partial refunds. \
                                All data is stored in-memory and reset on restart.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TestForge AI")
                                .email("testforge@example.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development")
                ));
    }
}
