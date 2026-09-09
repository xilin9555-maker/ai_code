package com.tmz.aicode.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 代码文件保存流程的抽象模板。
 *
 * 所有生成模式都需要依次完成参数校验、创建唯一目录、写入文件和返回目录。模板方法把这套
 * 固定顺序集中在父类中，子类只负责声明代码类型以及各自需要写入哪些文件。
 *
 * @param <T> 当前生成模式对应的结构化代码结果类型
 */
public abstract class CodeFileSaverTemplate<T> {

    /**
     * 所有生成结果的统一保存根目录。
     */
    protected static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 按固定流程保存代码。final 可以防止子类改变步骤顺序，确保每种模式都会先校验、
     * 再创建目录并写入文件。
     *
     * @param result 当前生成模式的结构化代码结果
     * @param appId 代码所属的应用 id
     * @return 当前应用对应的代码目录
     */
    public final File saveCode(T result, Long appId) {
        validateInput(result);
        String baseDirPath = buildAppDir(appId);
        saveFiles(result, baseDirPath);
        return new File(baseDirPath);
    }

    /**
     * 执行所有模式共享的基础参数校验，子类可以覆盖并继续检查具体代码字段。
     *
     * @param result 当前生成模式的结构化代码结果
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 使用生成类型和应用 id 创建稳定目录。
     *
     * 一个应用再次生成时仍然写入自己的目录，后续预览和部署只要知道 appId 和生成类型，
     * 就能计算出代码位置，不必把每次生成的随机目录另外保存到数据库。
     *
     * @param appId 代码所属的应用 id
     * @return 已创建的应用代码目录路径
     */
    protected final String buildAppDir(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 必须大于 0");
        }
        String codeType = getCodeType().getValue();
        String appDirName = StrUtil.format("{}_{}", codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + appDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 使用 UTF-8 写入一个非空代码文件。允许跳过空的可选内容，例如没有独立样式的 CSS。
     *
     * @param dirPath 本次生成结果目录
     * @param filename 需要写入的文件名
     * @param content 文件代码内容
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 取得当前保存策略对应的生成类型，用于构造输出目录名称。
     *
     * @return 当前代码生成类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 写入当前模式需要的具体文件，由每个子类实现差异部分。
     *
     * @param result 当前生成模式的结构化代码结果
     * @param baseDirPath 已创建的独立输出目录
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}
