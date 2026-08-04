package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstans.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现类
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 生成六位随机验证码
        String code = RandomUtil.randomString(6);

        // 将验证码保存到 Redis
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                LONG_CODE_TTL,
                TimeUnit.MINUTES
        );

        log.debug("验证码：{}", code);

        return Result.ok();
    }

    /**
     * 手机号验证码登录
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 获取手机号
        String phone = loginForm.getPhone();

        // 从 Redis 获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(
                LOGIN_CODE_KEY + phone
        );

        // 获取用户提交的验证码
        String code = loginForm.getCode();

        // 校验验证码
        if (cacheCode == null || code == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 根据手机号查询用户
        User user = query()
                .eq("phone", phone)
                .one();

        // 用户不存在则创建新用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 生成登录 Token
        String token = UUID.randomUUID().toString(true);

        // 将 User 转换为 UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(
                user,
                UserDTO.class
        );

        /*
         * 将 UserDTO 转换为 Map。
         *
         * StringRedisTemplate 使用 StringRedisSerializer，
         * 因此 Hash 中的 value 必须转换为 String。
         *
         * 例如：
         * Long 类型的 id：1L
         * 转换后："1"
         */
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) ->
                                        fieldValue == null
                                                ? null
                                                : fieldValue.toString()
                        )
        );

        // Redis 中保存登录用户信息的 Key
        String tokenKey = LOGIN_USER_KEY + token;

        // 将用户信息写入 Redis Hash
        stringRedisTemplate.opsForHash().putAll(
                tokenKey,
                userMap
        );

        // 设置 Token 有效期
        stringRedisTemplate.expire(
                tokenKey,
                LONG_USER_TTL,
                TimeUnit.MINUTES
        );

        // 登录成功后删除验证码，避免验证码被重复使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 将 Token 返回给前端
        return Result.ok(token);
    }

    /**
     * 根据手机号创建新用户
     */
    private User createUserWithPhone(String phone) {

        User user = new User();

        user.setPhone(phone);

        user.setNickName(
                USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)
        );

        save(user);

        return user;
    }
}