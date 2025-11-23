package com.mentoai.mentoai;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MentoaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MentoaiApplication.class, args);
	}

	@Bean
	public OpenAPI projectOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Mentoai API")
				.description("Mentoai service API documentation")
				.version("v1")
				.contact(new Contact()
					.name("Mentoai Team")
					.url("https://example.com")
					.email("support@example.com"))
				.license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
			.externalDocs(new ExternalDocumentation()
				.description("OpenAPI Spec")
				.url("/v3/api-docs"));
	}

}
