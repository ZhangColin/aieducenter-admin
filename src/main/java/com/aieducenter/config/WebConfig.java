package com.aieducenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Web 配置类
 *
 * <p>配置跨域资源共享（CORS）支持
 */
@Configuration
public class WebConfig {

    /**
     * 配置 CORS 过滤器
     *
     * <p>允许来自 localhost:3001（管理后台前端开发服务器）的跨域请求
     *
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 允许携带认证信息（cookies, authorization headers）
        config.setAllowCredentials(true);

        // 允许的前端域名
        config.addAllowedOrigin("http://localhost:3001");
        config.addAllowedOrigin("http://127.0.0.1:3001");

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");

        // 预检请求的缓存时间（秒）
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
