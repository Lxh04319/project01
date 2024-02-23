package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
//import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page =query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId= UserHolder.getUser().getId();
        //判断是否点赞过
        String key=BLOG_LIKED_KEY+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null){
            //没点赞过--数据库++，保存到redis
            boolean isSuccess=update().setSql("liked=liked+1").eq("id",id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }
        else{
            //点赞过--取消点赞
            //数据库--，redis移除
            boolean isSuccess=update().setSql("liked=liked-1").eq("id",id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return null;
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        //查询前五名用户
        Set<String> top5= stringRedisTemplate.opsForZSet().range(key,0,4);
        if(top5==null|| top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析用户id
        List<Long> ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS=userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("博客不存在！");
        }
        queryBlogUser(blog);
        //查询是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user=UserHolder.getUser();
        if(user==null){
            //没登陆，返回登陆
            return;
        }
        //获取当前登录用户
        Long userId= user.getId();
        //判断是否点赞过
        String key=BLOG_LIKED_KEY+blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        blog.setIsLike(score!=null);
    }

    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
