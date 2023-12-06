package org.springframework.samples.petclinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;

@SpringBootApplication
public class PetClinicApplication extends SpringBootServletInitializer {

    private static volatile OpenTelemetry openTelemetry = OpenTelemetry.noop();

    public static void main(String[] args) {

        OpenTelemetrySdk openTelemetrySdk =
            AutoConfiguredOpenTelemetrySdk.builder().build().getOpenTelemetrySdk();
        PetClinicApplication.openTelemetry = openTelemetrySdk;


        SpringApplication.run(PetClinicApplication.class, args);

        // Setup log4j OpenTelemetryAppender
        // Normally this is done before the framework (Spring) is initialized. However, spring boot
        // erases any programmatic log configuration so we must initialize after Spring.
        // See this issue for tracking: https://github.com/spring-projects/spring-boot/issues/25847
        OpenTelemetryAppender.install(openTelemetrySdk);
    }

    @Bean
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }
}
