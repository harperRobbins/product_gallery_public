package com.szwego.gallery.service;

import com.szwego.gallery.domain.Category;
import com.szwego.gallery.dto.CategorySaveRequest;
import com.szwego.gallery.dto.CategoryTreeVO;

import java.util.List;
import java.util.Map;

public interface CategoryService {
    Long save(CategorySaveRequest request);

    void delete(Long id);

    List<CategoryTreeVO> tree(String lang);

    Map<Long, String> nameMap(String lang);

    Category getById(Long id);

    List<Long> listDescendantIds(Long categoryId);
}
