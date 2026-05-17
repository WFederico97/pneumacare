package wfederico.backendjavacoretemplate.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import wfederico.backendjavacoretemplate.core.web.ApiResponseBase;

import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SecurityFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    public SecurityFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, RateLimitProperties rateLimitProperties) {
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        String redisKey = "rate_limit:" + clientIp;
        String traceId = MDC.get("traceId");
        String blackListKey = "blacklist:" + clientIp;

        Boolean isBlacklisted = redisTemplate.hasKey(blackListKey);

        if (Boolean.TRUE.equals(isBlacklisted)) {
            response.setStatus(403);
            response.setContentType("application/json");

            ApiResponseBase<Void> apiResponse = ApiResponseBase.<Void>builder()
                    .status(403)
                    .message("IP is blacklisted")
                    .traceId(traceId)
                    .build();

            String jsonResponse = objectMapper.writeValueAsString(apiResponse);
            response.getWriter().write(jsonResponse);
            return;
        }

        Long requestCount = redisTemplate.opsForValue().increment(redisKey);

        if (requestCount != null && requestCount == 1) {
            redisTemplate.expire(redisKey, rateLimitProperties.getWindowSeconds(), TimeUnit.SECONDS);
        }

        if (requestCount != null && requestCount > rateLimitProperties.getThreshold()) {
            response.setStatus(429);
            response.setContentType("application/json");

            ApiResponseBase<Void> apiResponse = ApiResponseBase.<Void>builder()
                    .status(429)
                    .message("Too many requests")
                    .traceId(traceId)
                    .build();

            String jsonResponse = objectMapper.writeValueAsString(apiResponse);
            response.getWriter().write(jsonResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
