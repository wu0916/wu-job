package com.snailwu.job.admin.controller;

import com.github.pagehelper.PageInfo;
import com.snailwu.job.admin.core.model.JobGroup;
import com.snailwu.job.admin.service.GroupService;
import com.snailwu.job.core.biz.model.ResultT;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author 吴庆龙
 * @date 2020/7/23 10:34 上午
 */
@RestController
@RequestMapping("/group")
public class GroupController {

    @Resource
    private GroupService groupService;

    /**
     * 分页列表
     */
    @GetMapping
    public ResultT<PageInfo<JobGroup>> list(Integer pageNum, Integer pageSize) {
        PageInfo<JobGroup> pageInfo = groupService.list(pageNum, pageSize);
        return new ResultT<>(pageInfo);
    }

    /**
     * 新增
     */
    @PostMapping
    public ResultT<String> save(JobGroup jobGroup) {
        groupService.saveOrUpdate(jobGroup);
        return ResultT.SUCCESS;
    }

    /**
     * 更新
     */
    @PutMapping
    public ResultT<String> update(JobGroup jobGroup) {
        groupService.saveOrUpdate(jobGroup);
        return ResultT.SUCCESS;
    }

    /**
     * 删除
     */
    @DeleteMapping
    public ResultT<String> delete(Integer id) {
        groupService.delete(id);
        return ResultT.SUCCESS;
    }

}
