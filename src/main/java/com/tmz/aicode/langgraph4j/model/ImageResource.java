package com.tmz.aicode.langgraph4j.model;

import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流中可复用的图片资源。
 *
 * 图片收集节点生成该对象，后续提示词增强和代码生成节点根据分类与描述决定图片的使用位置。
 * 实现 Serializable 是为了让 LangGraph4j 在复制或持久化工作流状态时能够安全处理该对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 图片在网页中的业务用途。 */
    private ImageCategoryEnum category;

    /** 对图片内容和预期使用场景的自然语言描述。 */
    private String description;

    /** 图片的可访问地址。 */
    private String url;
}
