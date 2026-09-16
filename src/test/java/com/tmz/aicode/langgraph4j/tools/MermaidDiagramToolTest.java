package com.tmz.aicode.langgraph4j.tools;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Mermaid CLI 转换和真实对象存储上传。
 *
 * 测试会在 COS 中保留生成的 SVG，控制台可以通过日志中的公开地址定位该对象。
 */
@SpringBootTest
class MermaidDiagramToolTest {

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    /** 有效 Mermaid 代码应生成 SVG、上传并返回架构图资源。 */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void generatesAndUploadsMermaidDiagram() {
        String mermaidCode = """
                flowchart LR
                    Start([开始]) --> Input[输入数据]
                    Input --> Process[处理数据]
                    Process --> Decision{是否有效}
                    Decision -->|是| Output[输出结果]
                    Decision -->|否| Error[错误处理]
                    Output --> End([结束])
                    Error --> End
                """;
        List<ImageResource> diagrams = mermaidDiagramTool
                .generateMermaidDiagram(mermaidCode, "数据处理架构图");

        assertNotNull(diagrams);
        assertFalse(diagrams.isEmpty());
        assertEquals(1, diagrams.size());
        ImageResource diagram = diagrams.getFirst();
        assertEquals(ImageCategoryEnum.ARCHITECTURE, diagram.getCategory());
        assertEquals("数据处理架构图", diagram.getDescription());
        assertNotNull(diagram.getUrl());
        assertTrue(diagram.getUrl().startsWith("http"));
        System.out.println("架构图上传地址：" + diagram.getUrl());
    }

    /** 空代码应直接返回空列表且不调用对象存储。 */
    @Test
    void skipsGenerationWhenMermaidCodeIsBlank() {
        List<ImageResource> diagrams =
                mermaidDiagramTool.generateMermaidDiagram("  ", "空图表");

        assertTrue(diagrams.isEmpty());
    }
}
