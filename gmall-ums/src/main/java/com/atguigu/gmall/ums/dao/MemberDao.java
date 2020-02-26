package com.atguigu.gmall.ums.dao;

import com.atguigu.gmall.ums.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-25 22:27:16
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
