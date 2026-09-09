package com.tmz.aicode.constant;

import java.io.File;

/**
 * 应用模块共用的固定值。
 *
 * 优先级既用于控制应用展示顺序，也用于区分普通应用和精选应用。将这些数字集中管理，
 * 可以避免控制器和服务层散落含义不清楚的数字。
 */
public interface AppConstant {

    /**
     * AI 生成代码的工作目录。
     *
     * 这里保存的是仍可继续生成和修改的文件，预览接口也会直接读取这个目录。
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir")
            + File.separator + "tmp" + File.separator + "code_output";

    /**
     * 已部署网站的静态文件根目录。
     *
     * 部署时会把工作目录中的代码复制到这里，并使用 deployKey 作为子目录名，方便
     * Nginx 只暴露稳定版本，而不直接读取正在生成的文件。
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir")
            + File.separator + "tmp" + File.separator + "code_deploy";

    /**
     * 静态网站对外访问地址。
     *
     * 本地环境约定由 Nginx 监听 80 端口，因此不需要在地址中额外写端口。
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * 精选应用使用的优先级。
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 新建应用使用的默认优先级。
     */
    Integer DEFAULT_APP_PRIORITY = 0;
}
