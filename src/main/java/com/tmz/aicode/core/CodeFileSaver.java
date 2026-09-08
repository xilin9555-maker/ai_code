package com.tmz.aicode.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 将结构化的网页代码写入本地文件。
 *
 * 每次保存都会创建独立目录，目录名称由生成方式和雪花 ID 组成。这样连续生成多个网站时
 * 不会相互覆盖，也方便后续预览、压缩或发布指定的一次生成结果。
 */
public final class CodeFileSaver {

    /**
     * 所有生成结果统一保存在项目运行目录下的 tmp/code_output 中。
     */
    private static final String FILE_SAVE_ROOT_DIR =
            System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output";

    private CodeFileSaver() {
        // 工具类只提供静态方法，不需要创建实例。
    }

    /**
     * 保存单文件 HTML 生成结果。
     *
     * @param result 已完成结构化转换的单文件生成结果
     * @return 本次生成结果所在的独立目录
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result) {
        Objects.requireNonNull(result, "单文件生成结果不能为 null");
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        return new File(baseDirPath);
    }

    /**
     * 保存 HTML、CSS 和 JavaScript 三个文件。
     *
     * @param result 已完成结构化转换的多文件生成结果
     * @return 本次生成结果所在的独立目录
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        Objects.requireNonNull(result, "多文件生成结果不能为 null");
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 创建不会与其他生成任务冲突的输出目录。
     *
     * @param businessType 当前生成方式的稳定值
     * @return 已创建的输出目录绝对路径
     */
    private static String buildUniqueDir(String businessType) {
        String uniqueDirName = StrUtil.format("{}_{}", businessType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 使用 UTF-8 编码写入一个代码文件。
     *
     * 写入前检查内容可以让结构化输出缺少字段时尽早暴露问题，避免生成看似成功的空文件。
     *
     * @param dirPath 本次生成结果的目录
     * @param filename 要写入的文件名
     * @param content 对应文件的完整代码
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        Objects.requireNonNull(content, filename + " 的代码内容不能为 null");
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
