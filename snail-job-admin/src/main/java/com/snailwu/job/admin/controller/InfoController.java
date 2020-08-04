package com.snailwu.job.admin.controller;

import com.github.pagehelper.PageInfo;
import com.snailwu.job.admin.core.model.JobInfo;
import com.snailwu.job.admin.core.thread.JobTriggerPoolHelper;
import com.snailwu.job.admin.service.InfoService;
import com.snailwu.job.admin.trigger.TriggerTypeEnum;
import com.snailwu.job.core.biz.model.ResultT;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author 吴庆龙
 * @date 2020/7/23 3:12 下午
 */
@RestController
@RequestMapping("/info")
public class InfoController {

    @Resource
    private InfoService infoService;

    /**
     * 分页查询
     */
    @GetMapping
    public ResultT<PageInfo<JobInfo>> list(Integer pageNum, Integer pageSize) {
        PageInfo<JobInfo> pageInfo = infoService.list(pageNum, pageSize);
        return new ResultT<>(pageInfo);
    }

    /**
     * 新增
     */
    @PostMapping
    public ResultT<String> save(JobInfo jobInfo) {
        infoService.saveOrUpdate(jobInfo);
        return ResultT.SUCCESS;
    }

    /**
     * 更新
     */
    @PutMapping
    public ResultT<String> update(JobInfo jobInfo) {
        infoService.saveOrUpdate(jobInfo);
        return ResultT.SUCCESS;
    }

    /**
     * 删除
     */
    @DeleteMapping
    public ResultT<String> delete(Integer id) {
        infoService.delete(id);
        return ResultT.SUCCESS;
    }

    /**
     * 启动任务
     */
    @PostMapping("/start")
    public ResultT<String> start(Integer id) {
        infoService.start(id);
        return ResultT.SUCCESS;
    }

    /**
     * 停止任务
     */
    @PostMapping("/stop")
    public ResultT<String> stop() {
        return ResultT.SUCCESS;
    }

    /**
     * 执行一次
     */
    @PostMapping("/exec_once")
    public ResultT<String> execOnce(Integer id) {
        JobTriggerPoolHelper.push(id, TriggerTypeEnum.API, -1, null);
        return ResultT.SUCCESS;
    }

}