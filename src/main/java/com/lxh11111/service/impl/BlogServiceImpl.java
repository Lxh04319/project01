package com.lxh11111.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lxh11111.dto.Result;
import com.lxh11111.dto.ScrollResult;
import com.lxh11111.dto.UserDTO;
import com.lxh11111.entity.Blog;
import com.lxh11111.entity.Follow;
import com.lxh11111.entity.User;
import com.lxh11111.mapper.BlogMapper;
import com.lxh11111.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lxh11111.service.IFollowService;
import com.lxh11111.service.IUserService;
import com.lxh11111.utils.SystemConstants;
import com.lxh11111.utils.UserHolder;
//import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lxh11111.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.lxh11111.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IFollowService followService;
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
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess=save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //推送给粉丝--查询粉丝
        List<Follow> follows=followService.query().eq("follow_user_id",user.getId()).list();
        //推送笔记id
        for(Follow follow:follows){
            //获取粉丝id
            Long userId=follow.getUserId();
            //推送--sortedset
            String key=FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId=UserHolder.getUser().getId();
        //查询收件箱
        String key=FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples=stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key,0,max,offset,2);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据 blogId 时间戳 offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        int cnt=1;
        long minTime=0;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples){
            //获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //获取时间戳
            Long time=tuple.getScore().longValue();
            if(time==minTime){
                cnt++;
            }
            else{
                minTime=time;
                cnt=1;
            }
        }
        //根据blogId查询blog--数据库查询顺序问题，改变
        String idStr= StrUtil.join(",",ids);
        List<Blog> blogs=query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list();
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            //查询是否被点赞
            isBlogLiked(blog);
        }
        //封装返回
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setMinTime(minTime);
        r.setOffset(cnt);
        return Result.ok(r);
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
