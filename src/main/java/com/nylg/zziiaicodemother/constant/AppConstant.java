package com.nylg.zziiaicodemother.constant;

public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名（本地开发走 Spring Boot 内置静态资源接口，生产环境可通过 nginx 代理到 80 端口）
     */
    String CODE_DEPLOY_HOST = "http://127.0.0.1:8123/api/deploy";

}
