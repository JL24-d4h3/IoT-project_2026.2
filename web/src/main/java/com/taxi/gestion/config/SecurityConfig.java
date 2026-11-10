package com.taxi.gestion.config;

import com.taxi.gestion.security.JwtAuthenticationFilter;
import com.taxi.gestion.security.FiltroSuperadminFirebase;
import com.taxi.gestion.security.VerificadorSuperadminFirebase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Tres cadenas de filtros independientes:
 *  - /api/admin/**: ID token Firebase y UID autorizado en el servidor, sin sesion.
 *  - /api/**  : sin sesion, sin CSRF. Publica (consulta y registro) salvo las
 *               acciones que un taxista hace sobre si mismo o su servicio
 *               asignado, que exigen el token emitido en /api/auth/login.
 *  - resto    : formulario de login para el panel del Superadmin.
 *               El taxista no tiene ninguna ruta bajo /admin/**.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** El filtro JWT existente pertenece solo a su cadena, tampoco al servlet global. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> registroServletJwt(
            JwtAuthenticationFilter filtro) {
        FilterRegistrationBean<JwtAuthenticationFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    /** La API administrativa solo acepta ID tokens Firebase autorizados por UID de servidor. */
    @Bean
    @org.springframework.core.annotation.Order(0)
    public SecurityFilterChain adminApiFilterChain(HttpSecurity http,
            VerificadorSuperadminFirebase verificador, ObjectMapper json) throws Exception {
        http.securityMatcher("/api/admin/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("SUPERADMIN"))
            .exceptionHandling(errores -> errores
                    .authenticationEntryPoint((req, res, fallo) -> FiltroSuperadminFirebase.responder(
                            json, req, res, HttpStatus.UNAUTHORIZED,
                            "Inicia sesion de nuevo para gestionar los taxistas"))
                    .accessDeniedHandler((req, res, fallo) -> FiltroSuperadminFirebase.responder(
                            json, req, res, HttpStatus.FORBIDDEN,
                            "Tu cuenta no esta autorizada para gestionar los taxistas")))
            .addFilterBefore(new FiltroSuperadminFirebase(verificador, json),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Hash de contrasenas. BCrypt con factor de coste 12. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Cuenta del Superadmin. En produccion conviene moverla a base de datos
     * junto con un UserDetailsService propio.
     */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.admin.usuario}") String usuario,
            @Value("${app.admin.password}") String password) {
        return new InMemoryUserDetailsManager(
                User.withUsername(usuario)
                        .password(encoder.encode(password))
                        .roles("SUPERADMIN")
                        .build());
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Publicas: login, consultas y lo que hace el cliente
                    // (pedir un taxi, ver el estado del viaje, dejar una valoracion).
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/taxistas/disponibles",
                            "/api/taxistas/*", "/api/taxistas/*/ubicacion").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/taxistas/*/valoracion").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/servicios").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/servicios/pendientes",
                            "/api/servicios/*", "/api/servicios/*/estado").permitAll()
                    // Todo lo demas (reportar ubicacion, editar datos propios, aceptar o
                    // avanzar un servicio, ver el propio historial) exige el token del login.
                    .anyRequest().hasRole("TAXISTA"))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    new AntPathRequestMatcher("/h2-console/**")))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())) // consola H2
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/registro", "/registro/**", "/css/**", "/js/**",
                            "/img/**", "/fotos/**", "/webjars/**", "/favicon.ico").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs/**", "/h2-console/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/login").permitAll()
                    .requestMatchers("/admin/**").hasRole("SUPERADMIN")
                    .anyRequest().authenticated())
            .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/admin/taxistas", true)
                    .permitAll())
            .logout(logout -> logout
                    .logoutSuccessUrl("/login?salida")
                    .permitAll());
        return http.build();
    }
}
