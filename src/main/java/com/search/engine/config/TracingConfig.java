package com.search.engine.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class TracingConfig {

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    @Bean(destroyMethod = "close")
    SdkTracerProvider sdkTracerProvider(
            @Value("${spring.application.name:search-engine}") String applicationName,
            @Value("${management.otlp.tracing.endpoint:http://127.0.0.1:4317}") String endpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(SERVICE_NAME, applicationName)));

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(5))
                .build();

        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();
    }

    @Bean
    OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.noop())
                .build();
    }

    @Bean
    WebFilter otelRequestTracingFilter(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer("search-engine-webflux");
        return (exchange, chain) -> Mono.defer(() -> {
            Span span = startServerSpan(tracer, exchange);
            Scope scope = span.makeCurrent();
            return chain.filter(exchange)
                    .doOnError(error -> {
                        span.recordException(error);
                        span.setAttribute(ERROR_TYPE, error.getClass().getName());
                        span.setStatus(StatusCode.ERROR, error.getMessage() == null ? "Request failed" : error.getMessage());
                    })
                    .doFinally(signalType -> {
                        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                        if (statusCode != null) {
                            span.setAttribute(HTTP_RESPONSE_STATUS_CODE, (long) statusCode.value());
                            if (statusCode.is5xxServerError()) {
                                span.setStatus(StatusCode.ERROR);
                            }
                        }
                        span.end();
                        scope.close();
                    });
        });
    }

    private Span startServerSpan(Tracer tracer, ServerWebExchange exchange) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HTTP_REQUEST_METHOD, method)
                .setAttribute(URL_PATH, path)
                .startSpan();
    }
}
