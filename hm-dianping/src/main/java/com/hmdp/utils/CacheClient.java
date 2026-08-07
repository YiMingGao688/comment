package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit Unit){
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        //命中缓存
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, type);
        }

        if (Json != null) {
            return null;
        }
            //未命中缓存 去数据库找有没有
            R r = dbFallBack.apply(id);

            //数据库没有写入空值
            if (r == null) {
                //缓存写入空值x
                stringRedisTemplate.opsForValue().set(key, "",2L, TimeUnit.MINUTES);
                return null;
            }
            //数据库有只是缓存没命中 写入缓存
            this.set(key, r, time, Unit);

            return r;
        }

}

