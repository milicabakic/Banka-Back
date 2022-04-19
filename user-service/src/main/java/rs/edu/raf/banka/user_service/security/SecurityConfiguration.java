package rs.edu.raf.banka.user_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import rs.edu.raf.banka.user_service.filter.CustomAuthenticationFilter;
import rs.edu.raf.banka.user_service.filter.CustomAuthorizationFilter;
import rs.edu.raf.banka.user_service.model.Permissions;
import rs.edu.raf.banka.user_service.security.otp.OtpAuthenticationManager;
import rs.edu.raf.banka.user_service.service.implementation.UserServiceImplementation;

import java.util.Arrays;

import static org.springframework.http.HttpMethod.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final UserServiceImplementation userServiceImplementation;
    private final UserDetailsService userDetailsService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(bCryptPasswordEncoder);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();
        CustomAuthenticationFilter authenticationFilter = new CustomAuthenticationFilter(authenticationManagerBean());
        authenticationFilter.setFilterProcessesUrl("/api/login");

        http.authorizeRequests()
                .antMatchers("/api/login").permitAll()
                .antMatchers(GET, "/api/users").hasAuthority(String.valueOf(Permissions.LIST_USERS))
                .antMatchers(POST, "/api/user/create").hasAuthority(String.valueOf(Permissions.CREATE_USER))
                .antMatchers(POST, "/api/user/edit/**").hasAnyAuthority(String.valueOf(Permissions.EDIT_USER), String.valueOf(Permissions.MY_EDIT))
                .antMatchers(POST, "/api/otp/clear/**").hasAnyAuthority(String.valueOf(Permissions.EDIT_USER), String.valueOf(Permissions.MY_EDIT))
                .antMatchers(POST, "/api/otp/set/**").hasAnyAuthority(String.valueOf(Permissions.EDIT_USER), String.valueOf(Permissions.MY_EDIT))
                .antMatchers(DELETE, "/api/user/delete/**").hasAuthority(String.valueOf(Permissions.DELETE_USER));

        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.cors(Customizer.withDefaults());
        http.headers().frameOptions().disable();
        http.addFilter(authenticationFilter);
        http.addFilterBefore(new CustomAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception{
        return new OtpAuthenticationManager(userServiceImplementation, super.authenticationManagerBean());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET","POST", "PATCH", "PUT"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
