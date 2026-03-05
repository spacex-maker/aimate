-- 宿主机资源负载历史表：每分钟记录一条 CPU / 内存 / 磁盘使用情况
CREATE TABLE IF NOT EXISTS host_load_metric (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    host_name             VARCHAR(128) NOT NULL COMMENT '宿主机名称，如 hostname 或 COMPUTERNAME',
    cpu_load_percent      DOUBLE       NULL     COMMENT '系统 CPU 负载百分比（0-100，保留一位小数）',
    mem_available_percent INT          NULL     COMMENT '可用内存百分比（0-100）',
    root_fs_used_percent  INT          NULL     COMMENT '根分区磁盘已用百分比（0-100）',
    version               INT          NOT NULL DEFAULT 0,
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_host_load_metric_host_time (host_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
 COMMENT='宿主机资源负载历史';

