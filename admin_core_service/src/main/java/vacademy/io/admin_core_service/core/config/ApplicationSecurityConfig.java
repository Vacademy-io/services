package vacademy.io.admin_core_service.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import vacademy.io.common.auth.filter.InternalAuthFilter;
import vacademy.io.common.auth.filter.JwtAuthFilter;

@Configuration
@EnableMethodSecurity
public class ApplicationSecurityConfig {


    private static final String[] INTERNAL_PATHS = {"/admin-core-service/internal/**"};

    private static final String[] ALLOWED_PATHS = { "/admin-core-service/institute/v1/internal/create","/admin-core-service/actuator/**", "/admin-core-service/swagger-ui.html", "/admin-core-service/v1/report/alert/**", "/admin-core-service/v3/api-docs/**", "/admin-core-service/swagger-ui/**", "/admin-core-service/webjars/swagger-ui/**", "/admin-core-service/api-docs/**"};

    @Autowired
    JwtAuthFilter jwtAuthFilter;
    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    InternalAuthFilter internalAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeHttpRequests()
                .requestMatchers(ALLOWED_PATHS).permitAll()
                .requestMatchers(INTERNAL_PATHS).authenticated()
                .anyRequest().authenticated()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }


    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}