package com.atguigu.gmall.ums.service.impl;

import com.atguigu.core.exception.MemberException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.ums.dao.MemberDao;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.service.MemberService;


@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public Boolean checkData(String data, Integer type) {
        int count = this.count(new QueryWrapper<MemberEntity>().lambda()
                .eq(type == 1, MemberEntity::getUsername, data)
                .eq(type == 2, MemberEntity::getMobile, data)
                .eq(type == 3, MemberEntity::getEmail, data)
        );

        return count==0;
    }

    @Override
    public void register(MemberEntity memberEntity, String code) {
        //校验手机验证码
//        String codeMobile = stringRedisTemplate.opsForValue().get(memberEntity.getMobile());
//        if (!StringUtils.equals(codeMobile,code)) {
//            return;
//        }
        //生成盐
        String salt = UUID.randomUUID().toString().substring(0,6);
        memberEntity.setSalt(salt);
        //加盐
        String password = DigestUtils.md5Hex(memberEntity.getPassword() + salt);
        memberEntity.setPassword(password);
        //新增用户
        memberEntity.setGrowth(0);
        memberEntity.setIntegration(0);
        memberEntity.setLevelId(0L);
        memberEntity.setStatus(1);

        memberEntity.setCreateTime(new Date());
        this.save(memberEntity);
        //删除redis的验证码
    }

    @Override
    public MemberEntity queryUser(String username, String password) {
        MemberEntity memberEntity = this.getOne(new QueryWrapper<MemberEntity>().lambda().eq(MemberEntity::getUsername, username));
        if (memberEntity == null) {
            throw new MemberException("用户不存在");
        }
        //密码加密后比较
        String newPassword = DigestUtils.md5Hex(password + memberEntity.getSalt());
        if (StringUtils.equals(newPassword,memberEntity.getPassword())) {
            return memberEntity;
        }
        return null;
    }

}