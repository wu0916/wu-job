CREATE database if NOT EXISTS `wu_job` default character set utf8mb4;
USE `wu_job`;

SHOW TABLES;

-- 节点注册信息
DROP TABLE IF EXISTS `job_node`;
CREATE TABLE IF NOT EXISTS `job_node`
(
    `id`          INT(11)     NOT NULL AUTO_INCREMENT,
    `app_name`    VARCHAR(32) NOT NULL COMMENT '所属应用',
    `address`     VARCHAR(64) NOT NULL COMMENT '节点地址',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY `pk_id` (`id`),
    INDEX `idx_g_a` (`group_name`, `address`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 应用
DROP TABLE IF EXISTS `job_app`;
CREATE TABLE IF NOT EXISTS `job_app`
(
    `id`          INT(11)      NOT NULL AUTO_INCREMENT,
    `app_name`    VARCHAR(32)  NOT NULL COMMENT '应用名称',
    `title`       VARCHAR(64)  NOT NULL COMMENT '标题',
    `type`        TINYINT      NOT NULL COMMENT '注册类型.自动注册=0;手动注册=1',
    `addresses`   VARCHAR(512) NOT NULL COMMENT '执行器节点地址列表，多地址用逗号分隔',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_id` (`id`),
    UNIQUE KEY `uk_app_name` (`app_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 任务信息
DROP TABLE IF EXISTS `job_info`;
CREATE TABLE IF NOT EXISTS `job_info`
(
    `id`                    INT(11)      NOT NULL AUTO_INCREMENT,
    `name`                  VARCHAR(255) NOT NULL COMMENT '任务名称',
    `app_name`              VARCHAR(32)  NOT NULL COMMENT '组名称',
    `cron`                  VARCHAR(50)  NOT NULL COMMENT 'CRON表达式',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',

    `author`                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '负责人',
    `alarm_email`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '报警邮箱',

    `exec_route_strategy`   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '执行路由策略',
    `exec_handler`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '执行任务handler',
    `exec_param`            VARCHAR(512) NOT NULL DEFAULT '' COMMENT '执行任务参数',
    `exec_timeout`          INT(11)      NOT NULL DEFAULT 0 COMMENT '执行超时时间，单位秒',
    `exec_fail_retry_count` TINYINT      NOT NULL DEFAULT 0 COMMENT '失败重试次数',

    `trigger_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '调度状态：0-停止，1-运行',
    `trigger_last_time`     BIGINT(13)   NOT NULL DEFAULT 0 COMMENT '上次调度时间',
    `trigger_next_time`     BIGINT(13)   NOT NULL DEFAULT 0 COMMENT '下次调度时间',

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 任务日志
DROP TABLE IF EXISTS `job_log`;
CREATE TABLE IF NOT EXISTS `job_log`
(
    `id`               BIGINT(20)    NOT NULL AUTO_INCREMENT,
    `job_id`           INT(11)       NOT NULL COMMENT '任务，主键ID',
    `app_name`         VARCHAR(32)   NOT NULL COMMENT '任务组名',

    `exec_address`     VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '本次执行的地址',
    `exec_handler`     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '执行任务handler',
    `exec_param`       VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '执行任务参数',
    `fail_retry_count` TINYINT       NOT NULL DEFAULT 0 COMMENT '失败重试次数',

    `trigger_time`     DATETIME      NULL COMMENT '调度-时间',
    `trigger_code`     INT(11)       NOT NULL DEFAULT 0 COMMENT '调度-结果码',
    `trigger_msg`      VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '调度-结果信息',

    `exec_time`        DATETIME      NULL COMMENT '执行-时间',
    `exec_code`        INT(11)       NOT NULL DEFAULT 0 COMMENT '执行-结果码',
    `exec_msg`         VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '执行-结果信息',

    `alarm_status`     TINYINT       NOT NULL DEFAULT '0' COMMENT '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败',
    PRIMARY KEY `pk_id` (`id`),
    INDEX `idx_tt` (`trigger_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 首页任务执行报告
DROP TABLE IF EXISTS `job_log_report`;
CREATE TABLE IF NOT EXISTS `job_log_report`
(
    `id`            INT(11) NOT NULL AUTO_INCREMENT,
    `trigger_day`   DATE    NOT NULL DEFAULT CURRENT_DATE COMMENT '调度-时间',
    `running_count` INT(11) NOT NULL DEFAULT 0 COMMENT '运行中-日志数量',
    `success_count` INT(11) NOT NULL DEFAULT 0 COMMENT '执行成功-日志数量',
    `fail_count`    INT(11) NOT NULL DEFAULT 0 COMMENT '执行失败-日志数量',
    PRIMARY KEY `pk_id` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 任务锁
DROP TABLE IF EXISTS `job_lock`;
CREATE TABLE IF NOT EXISTS `job_lock`
(
    `lock_name` VARCHAR(50) NOT NULL COMMENT '锁名称',
    PRIMARY KEY `pk_lock_name` (`lock_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO `job_lock` (`lock_name`)
VALUES ('schedule_lock');

# CREATE TABLE `snail_job_logglue`
# (
#     `id`          int(11)      NOT NULL AUTO_INCREMENT,
#     `job_id`      int(11)      NOT NULL COMMENT '任务，主键ID',
#     `glue_type`   varchar(50) DEFAULT NULL COMMENT 'GLUE类型',
#     `glue_source` mediumtext COMMENT 'GLUE源代码',
#     `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE备注',
#     `add_time`    datetime    DEFAULT NULL,
#     `update_time` datetime    DEFAULT NULL,
#     PRIMARY KEY (`id`)
# ) ENGINE = InnoDB
#   DEFAULT CHARSET = utf8mb4;

-- 用户表
# CREATE TABLE `snail_job_user`
# (
#     `id`         int(11)     NOT NULL AUTO_INCREMENT,
#     `username`   varchar(50) NOT NULL COMMENT '账号',
#     `password`   varchar(50) NOT NULL COMMENT '密码',
#     `role`       tinyint(4)  NOT NULL COMMENT '角色：0-普通用户、1-管理员',
#     `permission` varchar(255) DEFAULT NULL COMMENT '权限：执行器ID列表，多个逗号分割',
#     PRIMARY KEY (`id`),
#     UNIQUE KEY `i_username` (`username`) USING BTREE
# ) ENGINE = InnoDB
#   DEFAULT CHARSET = utf8mb4;
