# REST version of Spring PetClinic Sample Application (spring-framework-petclinic extend ) 

This is a fork of the Spring PetClinic Sample Application.  It's been modified to illustrate several concepts with Splunk Observability Cloud. 

Specifically, it shows how the Splunk OpenTelemetry Java agent can be used to gather traces, metrics, and logs from the application and send them to an instance of the Splunk distribution of the OpenTelemetry collector running on a different host. 

It uses http/protobuf, rather than the default grpc protocol, to send metrics, logs, and traces to the collector. 

The Log4j2 log appender is used to send logs to the OpenTelemetry SDK. 

To build the application: 

````
git clone https://github.com/dmitchsplunk/spring-petclinic-rest.git
cd spring-petclinic-rest
mvn clean install
````

The following changes were made to instrument the application with OpenTelemetry. 

First, the pom.xml file was modified to include the opentelemetry-bom: 

````
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.opentelemetry</groupId>
                <artifactId>opentelemetry-bom</artifactId>
                <version>1.32.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
````

The following opentelemetry dependencies were also added to pom.xml: 

````
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-slf4j-impl</artifactId>
            <version>2.21.1</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk</artifactId>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-log4j-appender-2.17</artifactId>
            <version>1.32.0-alpha</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
            <version>1.32.0</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-extension-autoconfigure-spi</artifactId>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
        </dependency>
````

The following changes were also made to the pom.xml file to ensure the application is using Log4j2: 

````
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-logging</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
         ...
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-log4j2</artifactId>
        </dependency>
````

We also added the src/main/resources/log4j2.xml file with the following Log4j2 configuration: 

````
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" packages="io.opentelemetry.instrumentation.log4j.appender.v2_17">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout
                pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id: %X{trace_id} span_id: %X{span_id} trace_flags: %X{trace_flags} - %msg%n"/>
        </Console>
        <OpenTelemetry name="OpenTelemetryAppender"/>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="OpenTelemetryAppender" level="All"/>
            <AppenderRef ref="Console" level="All"/>
        </Root>
    </Loggers>
</Configuration>
````

Note that it includes the OpenTelemetry log appender, to send the log4j2 logs to the OpenTelemetry SDK. 

Then we modified the log appender to the main Java class (src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java):

````
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
````

The application assumes that an instance of the Splunk distribution of the OpenTelemetry collector is running on a separate host. 

Use the following commands to install the collector on a Linux host: 

````
curl -sSL https://dl.signalfx.com/splunk-otel-collector.sh > /tmp/splunk-otel-collector.sh && \
sudo sh /tmp/splunk-otel-collector.sh --realm <realm> -- <access token> --mode agent --without-fluentd --without-instrumentation
````

The modify the configuration file found at /etc/otel/collector/agent_config.yaml with the following config: 

````
extensions:
  health_check:
    endpoint: "${SPLUNK_LISTEN_INTERFACE}:13133"
  http_forwarder:
    ingress:
      endpoint: "${SPLUNK_LISTEN_INTERFACE}:6060"
    egress:
      endpoint: "${SPLUNK_API_URL}"
      # Use instead when sending to gateway
      #endpoint: "${SPLUNK_GATEWAY_URL}"
  smartagent:
    bundleDir: "${SPLUNK_BUNDLE_DIR}"
    collectd:
      configDir: "${SPLUNK_COLLECTD_DIR}"
  zpages:
    #endpoint: "${SPLUNK_LISTEN_INTERFACE}:55679"
  memory_ballast:
    # In general, the ballast should be set to 1/3 of the collector's memory, the limit
    # should be 90% of the collector's memory.
    # The simplest way to specify the ballast size is set the value of SPLUNK_BALLAST_SIZE_MIB env variable.
    size_mib: ${SPLUNK_BALLAST_SIZE_MIB}

receivers:
  hostmetrics:
    collection_interval: 10s
    scrapers:
      cpu:
      disk:
      filesystem:
      memory:
      network:
      # System load average metrics https://en.wikipedia.org/wiki/Load_(computing)
      load:
      # Paging/Swap space utilization and I/O metrics
      paging:
      # Aggregated system process count metrics
      processes:
      # System processes metrics, disabled by default
      # process:
  jaeger:
    protocols:
      grpc:
        endpoint: "${SPLUNK_LISTEN_INTERFACE}:14250"
      thrift_binary:
        endpoint: "${SPLUNK_LISTEN_INTERFACE}:6832"
      thrift_compact:
        endpoint: "${SPLUNK_LISTEN_INTERFACE}:6831"
      thrift_http:
        endpoint: "${SPLUNK_LISTEN_INTERFACE}:14268"
  otlp:
    protocols:
#      grpc:
#        endpoint: "${SPLUNK_LISTEN_INTERFACE}:4317"
      http:
        endpoint: "${SPLUNK_LISTEN_INTERFACE}:4318"
  # This section is used to collect the OpenTelemetry Collector metrics
  # Even if just a Splunk APM customer, these metrics are included
  prometheus/internal:
    config:
      scrape_configs:
      - job_name: 'otel-collector'
        scrape_interval: 10s
        static_configs:
        - targets: ["${SPLUNK_LISTEN_INTERFACE}:8888"]
        metric_relabel_configs:
          - source_labels: [ __name__ ]
            regex: '.*grpc_io.*'
            action: drop
  smartagent/signalfx-forwarder:
    type: signalfx-forwarder
    listenAddress: "${SPLUNK_LISTEN_INTERFACE}:9080"
  smartagent/processlist:
    type: processlist
  signalfx:
    endpoint: "${SPLUNK_LISTEN_INTERFACE}:9943"
    # Whether to preserve incoming access token and use instead of exporter token
    # default = false
    #access_token_passthrough: true
  zipkin:
    endpoint: "${SPLUNK_LISTEN_INTERFACE}:9411"

processors:
  batch:
  # Enabling the memory_limiter is strongly recommended for every pipeline.
  # Configuration is based on the amount of memory allocated to the collector.
  # For more information about memory limiter, see
  # https://github.com/open-telemetry/opentelemetry-collector/blob/main/processor/memorylimiter/README.md
  memory_limiter:
    check_interval: 2s
    limit_mib: ${SPLUNK_MEMORY_LIMIT_MIB}

  # Detect if the collector is running on a cloud system, which is important for creating unique cloud provider dimensions.
  # Detector order is important: the `system` detector goes last so it can't preclude cloud detectors from setting host/os info.
  # Resource detection processor is configured to override all host and cloud attributes because instrumentation
  # libraries can send wrong values from container environments.
  # https://docs.splunk.com/Observability/gdi/opentelemetry/components/resourcedetection-processor.html#ordering-considerations
  resourcedetection:
    detectors: [gcp, ecs, ec2, azure, system]
    override: true

  # Optional: The following processor can be used to add a default "deployment.environment" attribute to the logs and 
  # traces when it's not populated by instrumentation libraries.
  # If enabled, make sure to enable this processor in a pipeline.
  # For more information, see https://docs.splunk.com/Observability/gdi/opentelemetry/components/resource-processor.html
  #resource/add_environment:
    #attributes:
      #- action: insert
        #value: staging/production/...
        #key: deployment.environment

exporters:
  # Traces
  sapm:
    access_token: "${SPLUNK_ACCESS_TOKEN}"
    endpoint: "${SPLUNK_TRACE_URL}"
  # Metrics + Events
  signalfx:
    access_token: "${SPLUNK_ACCESS_TOKEN}"
    api_url: "${SPLUNK_API_URL}"
    ingest_url: "${SPLUNK_INGEST_URL}"
    # Use instead when sending to gateway
    #api_url: http://${SPLUNK_GATEWAY_URL}:6060
    #ingest_url: http://${SPLUNK_GATEWAY_URL}:9943
    sync_host_metadata: true
    correlation:
  # Logs
  splunk_hec:
    token: "${SPLUNK_HEC_TOKEN}"
    endpoint: "${SPLUNK_HEC_URL}"
    source: "otel"
    sourcetype: "otel"
    profiling_data_enabled: false
  # Profiling
  splunk_hec/profiling:
    token: "${SPLUNK_ACCESS_TOKEN}"
    endpoint: "${SPLUNK_INGEST_URL}/v1/log"
    log_data_enabled: false
  # Send to gateway
  otlp:
    endpoint: "${SPLUNK_GATEWAY_URL}:4317"
    tls:
      insecure: true
  logging:
      verbosity: detailed  

service:
  telemetry:
    metrics:
      address: "${SPLUNK_LISTEN_INTERFACE}:8888"
  extensions: [health_check, http_forwarder, zpages, memory_ballast, smartagent]
  pipelines:
    traces:
      receivers: [jaeger, otlp, smartagent/signalfx-forwarder, zipkin]
      processors:
      - memory_limiter
      - batch
      - resourcedetection
      exporters: [sapm, signalfx, logging]
    metrics:
      #receivers: [hostmetrics, otlp, signalfx, smartagent/signalfx-forwarder]
      #processors: [memory_limiter, batch, resourcedetection]
      receivers: [otlp, signalfx, smartagent/signalfx-forwarder]
      processors: [memory_limiter, batch]
      exporters: [signalfx, logging]
    metrics/internal:
      receivers: [prometheus/internal]
      processors: [memory_limiter, batch, resourcedetection]
      # When sending to gateway, at least one metrics pipeline needs
      # to use signalfx exporter so host metadata gets emitted
      exporters: [signalfx]
    logs/signalfx:
      receivers: [signalfx, smartagent/processlist]
      processors: [memory_limiter, batch, resourcedetection]
      exporters: [signalfx]
    logs:
      receivers: [otlp]
      processors:
      - memory_limiter
      - batch
      - resourcedetection
      #exporters: [splunk_hec, splunk_hec/profiling]
      exporters: [logging]
````

Restart the collector for the configuration changes to take effect: 

````
sudo systemctl restart splunk-otel-collector
````

Use the following command to view the collector logs (if needed):

````
journalctl -u splunk-otel-collector
````

The application can be launched as follows:

````
java -javaagent:./splunk-otel-javaagent.jar \
    -Dotel.javaagent.debug=false \
    -Dotel.resource.attributes="service.name=spring-petclinic-rest,environment=test" \
    -Dsplunk.profiler.enabled=true \
    -Dsplunk.profiler.memory.enabled=true \
    -Dotel.exporter.otlp.endpoint=http://<collector host>:4318 \
    -Dotel.exporter.otlp.protocol=http/protobuf \
    -Dsplunk.metrics.enabled=true \
    -Dsplunk.metrics.endpoint=http://<collector host>>:9943 \
    -jar target/spring-petclinic-rest-3.0.2.jar 2>&1 | tee out.txt
````

Replace <collector host> with the hostname where the OpenTelemetry collector is running.

To access the application UI and exercise functionality, use a browser and navigate to the following URL (replace localhost with the appropriate host name if the application is not running on localhost):

http://localhost:9966/petclinic/swagger-ui/index.html#/pettypes/listPetTypes
