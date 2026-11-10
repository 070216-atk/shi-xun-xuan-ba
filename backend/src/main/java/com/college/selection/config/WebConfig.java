package com.college.selection.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 两件事：
 * 1. 注册登录拦截器，并放行公开接口
 * 2. 配置跨域（前端 5173 → 后端 8080）
 *
 * 如果前端配了 Vite 代理（/api → localhost:8080），跨域其实用不上；
 * 保留是为了让没配代理时也能直接调通。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                // 公开接口：不登录也能看
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/labs",
                        "/api/labs/**",
                        "/api/stages"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
