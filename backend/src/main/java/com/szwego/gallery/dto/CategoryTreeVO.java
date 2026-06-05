package com.szwego.gallery.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryTreeVO {
    private Long id;
    private Long parentId;
    private String name;
    private String enName;
    private Integer level;
    private Integer sort;
    private String path;
    private List<CategoryTreeVO> children = new ArrayList<CategoryTreeVO>();
}
