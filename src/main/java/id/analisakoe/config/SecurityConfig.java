package id.analisakoe.config;

import id.analisakoe.auth.jwt.JwtAuthFilter;
import id.analisakoe.context.ContextFilter;
import id.analisakoe.ratelimit.RateLimitFilter;
import id.analisakoe.security.DemoReadOnlyFilter;
import id.analisakoe.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ContextFilter contextFilter;
    private final RateLimitFilter rateLimitFilter;
    private final DemoReadOnlyFilter demoReadOnlyFilter;
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ContextFilter contextFilter,
                          RateLimitFilter rateLimitFilter,
                          DemoReadOnlyFilter demoReadOnlyFilter,
                          UserDetailsServiceImpl userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.contextFilter = contextFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.demoReadOnlyFilter = demoReadOnlyFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** ContextFilter hanya berjalan di dalam SecurityFilterChain, bukan sebagai servlet filter. */
    @Bean
    public FilterRegistrationBean<ContextFilter> contextFilterRegistration(ContextFilter filter) {
        FilterRegistrationBean<ContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            String uri = request.getRequestURI();
            String ctx = request.getContextPath();
            String path = (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
                ? uri.substring(ctx.length()) : uri;

            if (path.startsWith("/ui/") && !path.equals("/ui/login")) {
                response.sendRedirect(ctx + "/ui/login");
                return;
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\"," +
                "\"message\":\"Token tidak ada atau tidak valid. Login di /api/auth/login\"," +
                "\"path\":\"" + path + "\"}"
            );
        };
    }

    @Bean
    public AccessDeniedHandler forbiddenHandler() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\"," +
                "\"message\":\"Anda tidak punya izin untuk endpoint ini\"," +
                "\"path\":\"" + request.getRequestURI() + "\"}"
            );
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ui/**", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/ping").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedEntryPoint())
                .accessDeniedHandler(forbiddenHandler())
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(demoReadOnlyFilter, JwtAuthFilter.class)
            .addFilterAfter(contextFilter, DemoReadOnlyFilter.class)
            .addFilterAfter(rateLimitFilter, ContextFilter.class);

        http.headers(headers -> headers
                .cacheControl(cache -> cache.disable())
                .frameOptions(frame -> frame.sameOrigin())
            );
        return http.build();
    }
}