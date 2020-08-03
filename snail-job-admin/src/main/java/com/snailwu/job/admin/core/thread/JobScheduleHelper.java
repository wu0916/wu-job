package com.snailwu.job.admin.core.thread;

import com.snailwu.job.admin.core.conf.AdminConfig;
import com.snailwu.job.admin.core.cron.CronExpression;
import com.snailwu.job.admin.core.model.JobInfo;
import com.snailwu.job.admin.trigger.TriggerTypeEnum;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.ThreadContext;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.snailwu.job.admin.constant.HttpConstants.JOB_LOG_ID;
import static com.snailwu.job.admin.constant.JobConstants.DATE_TIME_PATTERN;
import static com.snailwu.job.admin.constant.JobConstants.PRE_LOAD_SLEEP_MS;
import static com.snailwu.job.admin.mapper.JobInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

/**
 * 定时任务调度类
 * 扫描数据库里的定时任务，将马上要进行调度的任务加入到 key 为 秒，val 为任务集合的 Map 中
 * 根据秒获取对应的任务集合，进行任务的调度
 *
 * @author 吴庆龙
 * @date 2020/7/9 2:40 下午
 */
public class JobScheduleHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduleHelper.class);

    /**
     * 扫描可调度的任务线程
     */
    private static Thread scanJobThread;
    private static volatile boolean scanJobStopFlag = false;

    /**
     * 进行调度线程
     */
    private static Thread invokeJobThread;
    private static volatile boolean invokeJobStopFlag = false;

    /**
     * 缓存将要执行的任务
     * key: 秒
     * val: 任务ID集合
     */
    private static final Map<Long, Set<Integer>> INVOKE_JOB_MAP = new ConcurrentHashMap<>();

    /**
     * 每次获取任务的最大数量
     */
    private static final int MAX_LIMIT_PRE_READ = 100;

    /**
     * 启动线程
     */
    public static void start() {
        scanJobThread = new Thread(() -> {
            // 本线程在日志中的唯一标识
            ThreadContext.put(JOB_LOG_ID, "scanJobThread");

            // 提前准备任务调度的时间跨度
            final long preReadMs = 20000;

            while (!scanJobStopFlag) {
                // 使用 FOR UPDATE 实现分布式锁
                Connection connection = getConnection();
                PreparedStatement statement = lock(connection);
                if (connection == null) {
                    LOGGER.error("没有获取到数据库分布式锁.");
                    continue;
                }

                LOGGER.info("获取数据库锁成功.进行任务的扫描和整理.");

                // 当前时间戳
                long nowTimeTs = System.currentTimeMillis();
                try {
                    // 获取 当前时间 + preReadMs 内所有待调度的任务
                    long maxTriggerTime = nowTimeTs + preReadMs;

                    // 扫描将要待执行的任务，根据任务的执行时间从近到远排序
                    List<JobInfo> jobInfoList = AdminConfig.getInstance().getJobInfoMapper().selectMany(
                            select(jobInfo.id, jobInfo.cron, jobInfo.triggerNextTime)
                                    .from(jobInfo)
                                    .where(triggerStatus, isEqualTo((byte) 1))
                                    .and(triggerNextTime, isLessThanOrEqualTo(maxTriggerTime))
                                    .orderBy(jobInfo.triggerNextTime)
                                    .limit(MAX_LIMIT_PRE_READ)
                                    .build().render(RenderingStrategies.MYBATIS3)
                    );

                    // 遍历待执行的任务
                    for (JobInfo info : jobInfoList) {
                        Long triggerNextTime = info.getTriggerNextTime();
                        if (triggerNextTime < nowTimeTs) {
                            // 1. 过时的任务 - 忽略调度并更新下次的执行时间
                            LOGGER.warn("任务:{},错失最后一次的触发时间:{}", info.getId(),
                                    DateFormatUtils.format(triggerNextTime, DATE_TIME_PATTERN));

                            // 刷新下次调度时间
                            refreshNextValidTime(info, new Date());
                        } else if (((triggerNextTime / 1000) % 60) == ((nowTimeTs / 1000) % 60)) {
                            // 2. 当前秒要执行的任务, 立马进行调度
                            JobTriggerPoolHelper.push(info.getId(), TriggerTypeEnum.CRON, -1, null);

                            // 刷新下次调度时间
                            refreshNextValidTime(info, new Date(triggerNextTime));
                        }
                        triggerNextTime = info.getTriggerNextTime();

                        // 3. 在 [当前秒+1秒,当前秒+preReadMs] 之内要执行的调度任务
                        while (triggerNextTime < maxTriggerTime && triggerNextTime != 0L) {
                            // 任务在第几秒开始执行
                            long invokeSecond = (triggerNextTime / 1000) % 60;

                            // 放入执行队列
                            pushInvokeMap(invokeSecond, info.getId());

                            // 刷新下次调度时间
                            refreshNextValidTime(info, new Date(triggerNextTime));
                            triggerNextTime = info.getTriggerNextTime();
                        }
                    }

                    // 更新任务的下次执行时间
                    for (JobInfo info : jobInfoList) {
                        AdminConfig.getInstance().getJobInfoMapper().update(
                                update(jobInfo)
                                        .set(triggerLastTime).equalTo(info.getTriggerLastTime())
                                        .set(triggerNextTime).equalTo(info.getTriggerNextTime())
                                        .set(triggerStatus).equalTo(info.getTriggerStatus())
                                        .where(id, isEqualTo(info.getId()))
                                        .build().render(RenderingStrategies.MYBATIS3)
                        );
                    }
                } catch (Exception e) {
                    LOGGER.error("执行任务调度异常.原因:{}", e.getMessage());
                } finally {
                    commitConnection(connection);
                    closeConnection(connection, statement);
                }

                // 计算耗时
                long costMs = System.currentTimeMillis() - nowTimeTs;
                LOGGER.info("本次任务扫描整理耗时:{}毫秒", costMs);

                // 整理任务的耗时要控制在 (preReadMs - sleepMs) 毫秒内
                if (costMs > preReadMs - PRE_LOAD_SLEEP_MS) {
                    LOGGER.warn("整理任务时间过长,需要优化!!!");
                    continue;
                }

                // 休眠，任务执行一次的时间为 costMs + PRE_LOAD_SLEEP_MS
                try {
                    TimeUnit.MILLISECONDS.sleep(PRE_LOAD_SLEEP_MS);
                } catch (InterruptedException e) {
                    if (!scanJobStopFlag) {
                        LOGGER.error("对齐整秒，休眠异常", e);
                    }
                }
            }

            // 清除日志上下文内容
            ThreadContext.clearAll();
        });
        scanJobThread.setDaemon(true);
        scanJobThread.setName("ScheduleThread");
        scanJobThread.start();
        LOGGER.info("启动任务扫描整理线程成功.");

        // 执行调度任务线程
        invokeJobThread = new Thread(() -> {
            // 本线程在日志中的唯一标识
            ThreadContext.put(JOB_LOG_ID, "invokeJobThread");

            while (!invokeJobStopFlag) {
                long nowTimeTs = System.currentTimeMillis();

                // 当前的秒
                long nowSecond = (nowTimeTs / 1000) % 60;

                // 上一秒要执行的任务,几乎没有上一秒未执行的数据
                Set<Integer> preJobIdSet = INVOKE_JOB_MAP.remove(nowSecond - 1);
                if (preJobIdSet != null) {
                    for (Integer jobId : preJobIdSet) {
                        JobTriggerPoolHelper.push(jobId, TriggerTypeEnum.CRON, -1, null);
                    }
                }

                // 获取本秒要执行的任务
                Set<Integer> jobIdSet = INVOKE_JOB_MAP.remove(nowSecond);
                if (jobIdSet != null) {
                    for (Integer jobId : jobIdSet) {
                        JobTriggerPoolHelper.push(jobId, TriggerTypeEnum.CRON, -1, null);
                    }
                }

                // 计算耗时，耗时一定不能大于 1秒，否则会造成任务调度时间不准确
                long costMs = System.currentTimeMillis() - nowTimeTs;
                if (costMs > 1000) {
                    LOGGER.warn("本次任务调度耗时时间过长:{}毫秒", costMs);
                } else {
                    LOGGER.info("本次任务调度耗时:{}毫秒", costMs);
                }
            }

            // 清除日志上下文内容
            ThreadContext.clearAll();
        });
        invokeJobThread.setDaemon(true);
        // 最高优先级
        invokeJobThread.setPriority(Thread.MAX_PRIORITY);
        invokeJobThread.setName("RingThread");
        invokeJobThread.start();
        LOGGER.info("启动任务调度线程成功.");
    }

    /**
     * 计算任务下次的执行时间
     * 可以对 CronExpression 类进行缓存（淘汰策略为最近未使用的）
     */
    private static void refreshNextValidTime(JobInfo info, Date fromDate) {
        Date nextValidDate = null;
        try {
            nextValidDate = new CronExpression(info.getCron()).getNextValidTimeAfter(fromDate);
        } catch (ParseException e) {
            LOGGER.error("[SnailJob]-Cron表达式异常.jobId:{}", info.getId());
        }
        if (nextValidDate == null) {
            info.setTriggerLastTime(0L);
            info.setTriggerNextTime(0L);
            info.setTriggerStatus((byte) 0);
        } else {
            info.setTriggerLastTime(info.getTriggerNextTime());
            info.setTriggerNextTime(nextValidDate.getTime());
            info.setTriggerStatus((byte) 1);
        }
    }

    /**
     * 加入到执行队列中
     */
    private static void pushInvokeMap(long invokeSecond, int jobId) {
        Set<Integer> jobIdSet = INVOKE_JOB_MAP.computeIfAbsent(invokeSecond, k -> new HashSet<>());
        jobIdSet.add(jobId);
    }

    /**
     * Stop
     */
    public static void stop() {
        // 设置为停止标志
        scanJobStopFlag = true;
        scanJobThread.interrupt();
        try {
            scanJobThread.join();
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        LOGGER.info("任务扫描整理线程停止.");

        // 让线程完成剩余任务的调度
        while (!INVOKE_JOB_MAP.isEmpty()) {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        // 停止调度线程
        invokeJobStopFlag = true;
        invokeJobThread.interrupt();
        try {
            invokeJobThread.join();
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        LOGGER.info("任务调度线程停止.");
    }

    /**
     * 获取数据库连接
     */
    private static Connection getConnection() {
        Connection connection = null;
        try {
            connection = AdminConfig.getInstance().getDataSource().getConnection();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            LOGGER.error("获取数据库连接异常.原因:{}", e.getMessage());
        }
        return connection;
    }

    /**
     * 进行锁定
     */
    private static PreparedStatement lock(Connection connection) {
        if (connection == null) {
            return null;
        }
        PreparedStatement ps = null;
        try {
            String sql = "SELECT lock_name FROM job_lock WHERE lock_name='schedule_lock' FOR UPDATE";
            ps = connection.prepareStatement(sql);
        } catch (SQLException e) {
            LOGGER.error("执行[for update]锁定语句异常.原因:{}", e.getMessage());
        }
        return ps;
    }

    /**
     * 提交事物
     */
    private static void commitConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOGGER.error("提交数据库事物异常.原因:{}", e.getMessage());
        }
    }

    /**
     * 关闭连接
     */
    private static void closeConnection(Connection connection, PreparedStatement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                LOGGER.error("关闭Statement异常.原因:{}", e.getMessage());
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.error("关闭数据库连接异常.原因:{}", e.getMessage());
            }
        }
    }
}
