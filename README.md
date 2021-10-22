#  EF-proxy  [![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

## 介绍

ef-proxy是java 版本的反向代理服务

命令：仅支持服务连接与五大基本类型相关的命令

存储：仅支持AOF日志

多路复用：支持 epoll，kqueue，select 默认优先级由高到低，同时支持本地和单路复用

强烈推荐使用单路select线程模型
#### 解决问题

1,启动简单，方便测试

2,解除喜欢刨根问底的开发者，对大厂面试官面试nginx相关问题的迷惑侧重点的迷惑

3,为开发网络代理的开发者提供简单/简洁的反向代理实现

#### EF-proxy功能介绍

支持tcp与http代理功能

#### EF-proxy架构简介

集群架构方式：服务端路由

见作者知乎文档：
[微服务集群架构实现一览](https://zhuanlan.zhihu.com/p/368407754)



####  EF-proxy涉及技术

1,Netty

####  EF-proxy开发框架

Java+Netty

####  EF-proxy入门知识
1，Java基础

b站搜索 “韩顺平java”

2，Netty基础

b站搜索 “韩顺平netty”

3，http 协议


####  EF-proxy启动步骤

idea内部直接启动MyproxyServer

jar运行方式 ，参见ef-zab




###  EF-proxy 压测结果

还未开始压测

####  秒吞吐量

盲猜会出现40k/s 的qps

####  延迟

盲猜会出现0.05%以上的消息高延迟






