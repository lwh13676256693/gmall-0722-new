package com.atguigu.gmall.order.interceptors;


import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.utils.CookieUtils;
import com.atguigu.core.utils.JwtUtils;
import com.atguigu.gmall.order.config.JwtProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private JwtProperties jwtProperties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserInfo userInfo = new UserInfo();
        //获取cookie中的token信息（jwt）及userKey信息
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());

        //判断有没有这个token
        if (StringUtils.isNotBlank(token)) {
            //解析token信息
            Map<String, Object> infoFromToken = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
            userInfo.setId(Long.parseLong(infoFromToken.get("id").toString()));
        }
        THREAD_LOCAL.set(userInfo);

        return super.preHandle(request, response, handler);
    }

    public static UserInfo getUserInfo() {
        return THREAD_LOCAL.get();

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //必须手动清楚threadLocal中线程变量，因为使用的是tomcat线程池
        THREAD_LOCAL.remove();
    }

}
