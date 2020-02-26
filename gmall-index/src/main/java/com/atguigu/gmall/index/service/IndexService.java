package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.annotation.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVo;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private GmallPmsClient gmallPmsClient;

    public List<CategoryEntity> queryLvl1Categories() {
        Resp<List<CategoryEntity>> listResp = this.gmallPmsClient.queryCategoryByPidOrLeverl(1, null);
        return listResp.getData();
    }

    @GmallCache(prefix = "index:cates:", timeout = 7200, random = 100)
    public List<CategoryVo> querySubCategories(Long pid) {
        //1.判断缓存中有没有
//        String cateJson = this.stringRedisTemplate.opsForValue().get(KEY_PREFIX + pid);
//        //2有，直接返回
//        if (!StringUtils.isEmpty(cateJson)) {
//            return JSON.parseArray(cateJson, CategoryVo.class);
//        }
//        //加在这，分布式锁,异步请求有可能在加入缓存的时候就进来了
//        RLock lock = redissonClient.getLock("lock" + pid);
//        lock.lock();
//
//        //1.判断缓存中有没有
//        String cateJson2 = this.stringRedisTemplate.opsForValue().get(KEY_PREFIX + pid);
//        //2有，直接返回
//        if (!StringUtils.isEmpty(cateJson2)) {
//            lock.unlock();
//            return JSON.parseArray(cateJson2, CategoryVo.class);
//        }

        Resp<List<CategoryVo>> listResp = this.gmallPmsClient.querySubCategory(pid);
        //3，查询完成后放入缓存
//        this.stringRedisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(listResp.getData()), 7 + new Random().nextInt(10), TimeUnit.DAYS);
//        lock.unlock();
        return listResp.getData();
    }

    public void testLock() {
        //给自己的锁生成随机id
        String uuid = UUID.randomUUID().toString();
        //执行redis的setnx的命令
        Boolean lock = this.stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid, 5, TimeUnit.SECONDS);
        if (lock) {
            String numString = this.stringRedisTemplate.opsForValue().get("num02");
            if (StringUtils.isEmpty(numString)) {
                return;
            }
            int num = Integer.parseInt(numString);
            this.stringRedisTemplate.opsForValue().set("num02", String.valueOf(++num));
            //释放锁,其他才可以拿到锁(lua脚本)
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            this.stringRedisTemplate.execute(new DefaultRedisScript<>(script), Arrays.asList("lock"), uuid);
//            if (StringUtils.equals(this.stringRedisTemplate.opsForValue().get("lock"),uuid)) {
//                this.stringRedisTemplate.delete("lock");
//            }
        } else {
            //其他请求重试
            this.testLock();
        }

    }

    public void testLock1() {

        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();
        String numString = this.stringRedisTemplate.opsForValue().get("num02");
        if (StringUtils.isEmpty(numString)) {
            return;
        }
        int num = Integer.parseInt(numString);
        this.stringRedisTemplate.opsForValue().set("num02", String.valueOf(++num));

        lock.unlock();
    }
}
