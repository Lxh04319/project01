--比较一致
if (redis.call('GET',KEY[1])==ARGV[1]) then
    --释放锁
    return redis.call('del',KEY[1])
end
--不一致，返回
return 0