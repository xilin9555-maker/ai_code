package com.tmz.aicode.generator;

import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

/**
 * 根据数据库表生成基础代码，运行时工作目录应为后端项目根目录。
 */
public class MyBatisCodeGenerator {

    private static final String[] TABLE_NAMES = {"user"};

    public static void main(String[] args) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(
                new ResourcePropertySource("classpath:application.properties"));

        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setDriverClassName(environment.getRequiredProperty("spring.datasource.driver-class-name"));
            dataSource.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
            dataSource.setUsername(environment.getRequiredProperty("spring.datasource.username"));
            dataSource.setPassword(environment.getRequiredProperty("spring.datasource.password"));

            Generator generator = new Generator(dataSource, createGlobalConfig());
            generator.generate();
        }
    }

    public static GlobalConfig createGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        //先生成到独立包，检查后再移动到业务包。
        globalConfig.getPackageConfig()
                .setBasePackage("com.tmz.aicode.genresult");
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                .setLogicDeleteColumn("isDelete");
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21);
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();
        globalConfig.enableService();
        globalConfig.enableServiceImpl();
        globalConfig.enableController();
        globalConfig.getJavadocConfig()
                .setAuthor("tmz")
                .setSince("");
        return globalConfig;
    }
}
