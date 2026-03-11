package com.openforge.aimate.agent.dto;

/**
 * 用户手动操作自己容器（启动/重启/关闭）的接口返回。
 */
public record ContainerControlResult(boolean success, String message, String containerName) {
    public static ContainerControlResult ok(String containerName) {
        return new ContainerControlResult(true, null, containerName);
    }

    public static ContainerControlResult fail(String message) {
        return new ContainerControlResult(false, message, null);
    }
}
