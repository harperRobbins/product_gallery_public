package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.CategorySaveRequest;
import com.szwego.gallery.dto.CategoryTreeVO;
import com.szwego.gallery.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/api/categories/tree")
    public ApiResponse<List<CategoryTreeVO>> tree(@RequestParam(value = "lang", required = false) String lang) {
        return ApiResponse.success(categoryService.tree(lang));
    }

    @PostMapping("/api/admin/categories/save")
    public ApiResponse<Long> save(@Validated @RequestBody CategorySaveRequest request) {
        return ApiResponse.success("保存成功", categoryService.save(request));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        categoryService.delete(id);
        return ApiResponse.success(null);
    }
}
