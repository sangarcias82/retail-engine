package com.retail.engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Retail Engine API")
                        .description("Unified REST contract for catalog, CRUD, CSV import, and purchase flows.")
                        .version("v1")
                        .contact(new Contact().name("Retail Engine")));
    }
}
