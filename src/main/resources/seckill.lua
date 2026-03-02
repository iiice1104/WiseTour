--参数
--1.1优惠券id
local voucherId =ARGV[1]
--1.2用户id
local userId = ARGV[2]
--1.3订单id
local orderId = ARGV[3]

--数据key
--2.1库存key
local stockKey = 'seckill:stock:'..voucherId
--2.2订单key
local orderKey = 'seckill:order:'..voucherId

--判断库存是否充足
if tonumber(redis.call('get', stockKey)) <= 0 then
    --库存不足，返回1
    return 1
end
--判断用户是否买过
if redis.call('sismember', orderKey, userId) then
    --存在说明重复下单,返回2
    return 2
end
--扣库存
redis.call('incrby',stockKey,-1)
--下单
redis.call('sadd',orderKey,userId)
--发送消息到消息队列 XADD stream.orders * k1 v1 k2 v2...
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0