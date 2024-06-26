package jakarta.observability.filters;

import com.google.gson.Gson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.observability.prometheus.PrometheusRegistry;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@WebFilter(urlPatterns = "/*", filterName = "Request Metrics")
public class RequestMetricsFilter implements Filter {

    private final PrometheusRegistry registry = PrometheusRegistry.getInstance();
    private static final Gson gson;

    static {
        gson = new Gson();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        Timer.Sample timer = Timer.start(registry.getPrometheusMeterRegistry());
        String endpoint = httpServletRequest.getRequestURI();
        long startTime = System.nanoTime();
        try {
            Counter.builder("jakarta_http_requests_endpoint")
                    .description("Total number of requests for a specific endpoint")
                    .tag("endpoint", endpoint)
                    .tag("params", gson.toJson(request.getParameterMap()))
                    .register(registry.getPrometheusMeterRegistry())
                    .increment();

            chain.doFilter(request, response);
        } finally {
            timer.stop(Timer.builder("jakarta_http_requests_timer")
                    .description("Request processing time")
                    .tag("endpoint", endpoint)
                    .tag("params", gson.toJson(request.getParameterMap()))
                    .register(registry.getPrometheusMeterRegistry()));

            Timer.builder("jakarta_http_requests_duration")
                    .description("Sum and average request processing time")
                    .tag("endpoint", endpoint)
                    .tag("params", gson.toJson(request.getParameterMap()))
                    .register(registry.getPrometheusMeterRegistry())
                    .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
