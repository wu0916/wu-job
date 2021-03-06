package com.snailwu.job.admin.core.route;

import com.snailwu.job.admin.core.route.strategy.ExecRouteBusyOver;
import com.snailwu.job.admin.core.route.strategy.ExecRouteFailOver;
import com.snailwu.job.admin.core.route.strategy.ExecRouteFirst;
import com.snailwu.job.admin.core.route.strategy.ExecRouteLast;

/**
 * 任务路由策略
 *
 * @author 吴庆龙
 * @date 2020/6/4 11:23 上午
 */
public enum ExecRouteStrategyEnum {

    FIRST("first", (byte) 1, "第一个", new ExecRouteFirst()),
    LAST("last", (byte) 2, "最后一个", new ExecRouteLast()),
//    ROUND("round", (byte) 3, "轮询", new ExecutorRouteRound()),
//    RANDOM("random", (byte) 4, "随机", new ExecutorRouteRandom()),
//    CONSISTENT_HASH("consistent_hash", (byte) 5, "一致性HASH", new ExecutorRouteConsistentHash()),
//    LEAST_FREQUENTLY_USED("least_frequently_used", (byte) 6, "最不经常使用", new ExecutorRouteLFU()),
//    LEAST_RECENTLY_USED("least_recently_used", (byte) 7, "最近最久未使用", new ExecutorRouteLRU()),
    FAIL_OVER("fail_over", (byte) 8, "故障转移", new ExecRouteFailOver()),
    BUSY_OVER("busy_over", (byte) 9, "忙碌转移", new ExecRouteBusyOver()),
    ;

    private final String name;
    private final Byte value;
    private final String desc;
    private final ExecRouter router;

    ExecRouteStrategyEnum(String name, Byte value, String desc, ExecRouter router) {
        this.name = name;
        this.value = value;
        this.desc = desc;
        this.router = router;
    }

    public String getName() {
        return name;
    }

    public Byte getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public ExecRouter getRouter() {
        return router;
    }

    public static ExecRouteStrategyEnum match(String name) {
        if (name != null) {
            for (ExecRouteStrategyEnum item : ExecRouteStrategyEnum.values()) {
                if (item.getName().equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }

}
