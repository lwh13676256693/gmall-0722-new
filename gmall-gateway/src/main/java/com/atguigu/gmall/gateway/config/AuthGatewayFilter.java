package com.atguigu.gmall.gateway.config;

import com.atguigu.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class AuthGatewayFilter implements GatewayFilter {

    @Autowired
    private JwtProperties properties;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        //1.获取jwt类型的token信息
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        if (CollectionUtils.isEmpty(cookies)) {
            //拦截告诉客户
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        HttpCookie cookie = cookies.getFirst(this.properties.getCookieName());
        //2.判断jwt类型的token是否为null
        if (cookie == null) {
            //拦截告诉客户
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        //3.解析jwt，如果正常解析放行
        try {
            JwtUtils.getInfoFromToken(cookie.getValue(),this.properties.getPublicKey());
        } catch (Exception e) {
            e.printStackTrace();
            //拦截告诉客户
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();

        }

        return chain.filter(exchange);
    }
}
