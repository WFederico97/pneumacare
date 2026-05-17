package wfederico.backendjavacoretemplate.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import wfederico.backendjavacoretemplate.core.web.ApiResponseBase;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SecurityFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    public SecurityFilter(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          RateLimitProperties rateLimitProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp    = request.getRemoteAddr();
        String redisKey    = "rate_limit:" + clientIp;
        String blackListKey = "blacklist:" + clientIp;
        String traceId     = MDC.get("traceId");

        if (Boolean.TRUE.equals(redisTemplate.hasKey(blackListKey))) {
            writeJson(response, 403, "IP is blacklisted", traceId);
            return;
        }

        Long requestCount = redisTemplate.opsForValue().increment(redisKey);

        if (requestCount != null && requestCount == 1) {
            redisTemplate.expire(redisKey, rateLimitProperties.getWindowSeconds(), TimeUnit.SECONDS);
        }

        if (requestCount != null && requestCount > rateLimitProperties.getThreshold()) {
            writeJson(response, 429, "Too many requests", traceId);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse response, int status, String message, String traceId)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ApiResponseBase<Void> body = ApiResponseBase.<Void>builder()
                .status(status)
                .message(message)
                .traceId(traceId)
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

