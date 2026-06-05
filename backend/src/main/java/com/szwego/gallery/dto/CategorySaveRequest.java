package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CategorySaveRequest {
    private Long id;

    @NotNull(message = "parentId不能为空")
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private String enName;

    private Integer sort;
}
