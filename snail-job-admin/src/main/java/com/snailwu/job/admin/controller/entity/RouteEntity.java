package com.snailwu.job.admin.controller.entity;

/**
 * @author 吴庆龙
 * @date 2020/8/17 3:33 下午
 */
public class RouteEntity {
    private String name;
    private String desc;

    public RouteEntity() {
    }

    public RouteEntity(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
