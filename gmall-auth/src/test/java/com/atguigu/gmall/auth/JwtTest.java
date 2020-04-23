package com.atguigu.gmall.auth;

import com.atguigu.core.utils.JwtUtils;
import com.atguigu.core.utils.RsaUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {
    private static final String pubKeyPath = "C:\\project-0722\\rsa\\rsa.pub";

    private static final String priKeyPath = "C:\\project-0722\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @Before
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 1);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1ODI3MDQ5NzR9.YGwiT9dqdPfGttL-UJ7qFA31avD2qqVmiwNWY4ZFeUomxXGMbJ-to5srSZjmvh9eYNta8H0rzE6D1pguubbnP2B1YJCB9rOFpYjmCCYtZ2RYCF_RkR7L6yFHCUOlTMkxwJblEvN6yqZ8WWzTdzo9C8vpS0qZdasXtlXLO9UsidbbJ1u3Yf8dBrPgICVro5wqwK8jDoBhRAJnE2UtWDNZ8cRtYAbSMdqXVFtARRx6uJSf-P4Qg0kbPYJFS1ZZLlAQ8gHuxDcI4GV337cXfptGdRq1bnvlDO_1a3CVnvT9hXg4DdZZpR39lHI4EQz5Dg-TI0CX0C6ew3pE0512eokAng";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
