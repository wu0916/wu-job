package com.snailwu.job.admin.service;

import com.github.pagehelper.PageInfo;
import com.github.pagehelper.page.PageMethod;
import com.snailwu.job.admin.constant.JobConstants;
import com.snailwu.job.admin.controller.request.JobInfoSearchRequest;
import com.snailwu.job.admin.core.cron.CronExpression;
import com.snailwu.job.admin.mapper.JobInfoDynamicSqlSupport;
import com.snailwu.job.admin.mapper.JobInfoMapper;
import com.snailwu.job.admin.model.JobInfo;
import com.snailwu.job.core.enums.TriggerStatus;
import com.snailwu.job.core.exception.JobException;
import org.apache.commons.lang3.Validate;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import static org.mybatis.dynamic.sql.SqlBuilder.*;

/**
 * @author 吴庆龙
 * @date 2020/7/27 10:37 上午
 */
@Service
public class InfoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfoService.class);

    @Resource
    private JobInfoMapper jobInfoMapper;

    /**
     * 开始运行任务 任务在 {@link com.snailwu.job.admin.constant.JobConstants#SCAN_JOB_SLEEP_MS} 秒后生效
     */
    public void start(Integer id) {
        JobInfo jobInfo = jobInfoMapper.selectByPrimaryKey(id).orElse(null);
        if (jobInfo == null) {
            LOGGER.error("开始运行任务.无此任务:{}", id);
            return;
        }

        // 计算任务的下次执行时间
        CronExpression cronExpression;
        try {
            cronExpression = new CronExpression(jobInfo.getCron());
        } catch (ParseException e) {
            LOGGER.error("解析CRON表达式异常");
            return;
        }
        Date nextTimeAfter = new Date(System.currentTimeMillis() + JobConstants.SCAN_JOB_SLEEP_MS);
        Date triggerNextTime = cronExpression.getNextValidTimeAfter(nextTimeAfter);

        JobInfo updateJobInfo = new JobInfo();
        updateJobInfo.setId(id);
        updateJobInfo.setTriggerStatus(TriggerStatus.RUNNING.getValue());
        updateJobInfo.setTriggerLastTime(0L);
        updateJobInfo.setTriggerNextTime(triggerNextTime.getTime());
        jobInfoMapper.updateByPrimaryKeySelective(updateJobInfo);
    }

    /**
     * 停止任务
     */
    public void stop(Integer id) {
        JobInfo jobInfo = jobInfoMapper.selectByPrimaryKey(id).orElse(null);
        if (jobInfo == null) {
            LOGGER.error("开始运行任务.无此任务:{}", id);
            return;
        }

        JobInfo updateJobInfo = new JobInfo();
        updateJobInfo.setId(id);
        updateJobInfo.setTriggerStatus(TriggerStatus.STOPPED.getValue());
        updateJobInfo.setTriggerLastTime(0L);
        updateJobInfo.setTriggerNextTime(0L);
        jobInfoMapper.updateByPrimaryKeySelective(updateJobInfo);
    }

    /**
     * 分页查询
     */
    public PageInfo<JobInfo> list(JobInfoSearchRequest searchRequest) {
        String appName = searchRequest.getAppName();
        String name = searchRequest.getName();
        String author = searchRequest.getAuthor();

        SelectStatementProvider statementProvider =
                select(JobInfoDynamicSqlSupport.jobInfo.allColumns())
                        .from(JobInfoDynamicSqlSupport.jobInfo)
                        .where()
                        .and(JobInfoDynamicSqlSupport.name, isLikeWhenPresent(name))
                        .and(JobInfoDynamicSqlSupport.appName, isEqualToWhenPresent(appName))
                        .and(JobInfoDynamicSqlSupport.author, isLikeWhenPresent(author))
                        .build()
                        .render(RenderingStrategies.MYBATIS3);
        return PageMethod.startPage(searchRequest.getPage(), searchRequest.getLimit())
                .doSelectPageInfo(() -> jobInfoMapper.selectMany(statementProvider));
    }

    /**
     * 保存 或 更新
     */
    public void saveOrUpdate(JobInfo info) {
        if (info.getId() == null) {
            // 新增
            // Cron 表达式是否正确
            Validate.isTrue(CronExpression.isValidExpression(info.getCron()), "Cron表达式不正确");
            int ret = jobInfoMapper.insertSelective(info);
            if (ret == 1) {
                LOGGER.info("新增任务成功");
            } else {
                LOGGER.error("新增任务失败");
            }
        } else {
            // 更新
            info.setUpdateTime(new Date());
            int ret = jobInfoMapper.updateByPrimaryKeySelective(info);
            if (ret == 1) {
                LOGGER.info("更新任务成功");
            } else {
                LOGGER.error("更新任务失败");
            }
        }
    }

    /**
     * 删除
     */
    public void delete(Integer id) {
        int ret = jobInfoMapper.deleteByPrimaryKey(id);
        if (ret == 1) {
            LOGGER.info("删除成功");
        } else {
            LOGGER.info("删除失败,记录不存在");
        }
    }

    /**
     * 复制一个任务
     */
    public void copy(Integer id) {
        JobInfo jobInfo = jobInfoMapper.selectByPrimaryKey(id).orElse(null);
        if (jobInfo == null) {
            throw new JobException("任务不存在");
        }
        jobInfo.setId(null);
        jobInfo.setName(jobInfo.getName() + "-复制");
        jobInfo.setCreateTime(null);
        jobInfo.setUpdateTime(null);
        jobInfo.setTriggerStatus(TriggerStatus.STOPPED.getValue());
        jobInfo.setTriggerLastTime(0L);
        jobInfo.setTriggerNextTime(0L);
        jobInfoMapper.insertSelective(jobInfo);
    }

    /**
     * 列出分组下的所有任务
     */
    public List<JobInfo> listAll(String appName) {
        return jobInfoMapper.selectMany(
                select(JobInfoDynamicSqlSupport.id, JobInfoDynamicSqlSupport.name)
                        .from(JobInfoDynamicSqlSupport.jobInfo)
                        .where(JobInfoDynamicSqlSupport.appName, isEqualTo(appName))
                        .build()
                        .render(RenderingStrategies.MYBATIS3));
    }
}