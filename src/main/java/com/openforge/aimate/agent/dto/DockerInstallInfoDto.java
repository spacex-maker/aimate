package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 当前系统安装 Docker 的说明与链接，供前端展示「安装 Docker」指引。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DockerInstallInfoDto(
        String os,
        String instructions,
        String scriptUrl,
        String docUrl,
        String copyCommand
) {
    public static DockerInstallInfoDto linux(String copyCommand) {
        return new DockerInstallInfoDto(
                "linux",
                "在终端执行以下命令安装 Docker（需 root 或 sudo）。安装后启动：sudo systemctl start docker",
                "https://get.docker.com",
                "https://docs.docker.com/engine/install/",
                copyCommand
        );
    }

    public static DockerInstallInfoDto windows() {
        return new DockerInstallInfoDto(
                "windows",
                "下载并安装 Docker Desktop for Windows，安装完成后启动 Docker Desktop。",
                null,
                "https://docs.docker.com/desktop/install/windows-install/",
                null
        );
    }

    public static DockerInstallInfoDto mac() {
        return new DockerInstallInfoDto(
                "mac",
                "下载并安装 Docker Desktop for Mac（Apple Silicon 或 Intel 按机型选择），安装完成后启动 Docker Desktop。",
                null,
                "https://docs.docker.com/desktop/install/mac-install/",
                null
        );
    }

    public static DockerInstallInfoDto unknown() {
        return new DockerInstallInfoDto(
                "unknown",
                "请访问 Docker 官网根据您的操作系统选择安装方式。",
                null,
                "https://docs.docker.com/get-docker/",
                null
        );
    }
}
