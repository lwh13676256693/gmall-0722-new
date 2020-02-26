package com.atguigu.gmall.index.aspect;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.annotation.GmallCache;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@Aspect
public class CacheAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(com.atguigu.gmall.index.annotation.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = null;
        //获取目标方法
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        Method method = signature.getMethod();
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        //获取目标方法的返回值
        Class<?> returnType = method.getReturnType();
        //前缀
        String prefix = gmallCache.prefix();
        //获取方法里面的参数
        Object[] args = joinPoint.getArgs();
        String key = prefix+Arrays.asList(args).toString();

        result = this.cacheHit(key, returnType);
        if (result!=null) {
            return result;
        }
        //没有命中，加分布式锁
        RLock lock = this.redissonClient.getLock("lock" + Arrays.asList(args).toString());
        lock.lock();
        //3没有则再查询多义词缓存
        result = this.cacheHit(key, returnType);
        if (result!=null) {
            lock.unlock();//释放分布式锁
            return result;
        }
        //这个会进入加注解的方法运行。还是没有才执行目标方法
        result = joinPoint.proceed();
        //加入缓存，释放分布式锁
        this.stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(result),gmallCache.timeout()+(int)(Math.random()*gmallCache.random()));
        lock.unlock();

        return result;
    }

    private Object cacheHit(String key,Class<?> returnType){
        //1.先查询缓存
        String json = this.stringRedisTemplate.opsForValue().get(key);
        //2.有则，返回
        if (StringUtils.isNoneBlank(json)) {
            return JSON.parseObject(json, returnType);
        }
        return null;
    }
}
