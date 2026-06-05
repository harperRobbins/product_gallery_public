package com.szwego.gallery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.domain.Category;
import com.szwego.gallery.domain.CategoryI18n;
import com.szwego.gallery.domain.Product;
import com.szwego.gallery.dto.CategorySaveRequest;
import com.szwego.gallery.dto.CategoryTreeVO;
import com.szwego.gallery.mapper.CategoryI18nMapper;
import com.szwego.gallery.mapper.CategoryMapper;
import com.szwego.gallery.mapper.ProductMapper;
import com.szwego.gallery.service.CategoryService;
import com.szwego.gallery.util.LanguageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final CategoryI18nMapper categoryI18nMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(CategorySaveRequest request) {
        Category parent = null;
        if (request.getParentId() != null && request.getParentId() != 0L) {
            parent = categoryMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException("父级分类不存在");
            }
        }

        if (request.getId() == null) {
            Category category = new Category();
            BeanUtils.copyProperties(request, category);
            category.setSort(request.getSort() == null ? 0 : request.getSort());
            category.setLevel(parent == null ? 1 : parent.getLevel() + 1);
            category.setPath("/");
            categoryMapper.insert(category);

            String path = (parent == null ? "" : parent.getPath()) + "/" + category.getId();
            categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                    .set(Category::getPath, path)
                    .eq(Category::getId, category.getId()));
            saveEnglishName(category.getId(), request.getEnName());
            return category.getId();
        }

        Category existing = categoryMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        existing.setName(request.getName());
        existing.setSort(request.getSort() == null ? 0 : request.getSort());
        categoryMapper.updateById(existing);
        saveEnglishName(existing.getId(), request.getEnName());
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return;
        }
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除子分类");
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (productCount > 0) {
            throw new BusinessException("该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeVO> tree(String lang) {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getLevel)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, String> enNameMap = loadCategoryNameMap(LanguageUtil.EN_LANG);
        Map<Long, String> currentLangMap = loadCategoryNameMap(lang);

        Map<Long, CategoryTreeVO> nodeMap = new HashMap<Long, CategoryTreeVO>();
        List<CategoryTreeVO> roots = new ArrayList<CategoryTreeVO>();

        for (Category category : categories) {
            CategoryTreeVO node = new CategoryTreeVO();
            BeanUtils.copyProperties(category, node);
            node.setEnName(enNameMap.get(category.getId()));
            String translated = currentLangMap.get(category.getId());
            if (translated != null && !translated.trim().isEmpty()) {
                node.setName(translated);
            }
            nodeMap.put(node.getId(), node);
        }

        for (Category category : categories) {
            CategoryTreeVO current = nodeMap.get(category.getId());
            if (category.getParentId() == null || category.getParentId() == 0L) {
                roots.add(current);
            } else {
                CategoryTreeVO parent = nodeMap.get(category.getParentId());
                if (parent != null) {
                    parent.getChildren().add(current);
                }
            }
        }

        sortTree(roots);
        return roots;
    }

    private void sortTree(List<CategoryTreeVO> nodes) {
        nodes.sort(Comparator.comparing(CategoryTreeVO::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CategoryTreeVO::getId));
        for (CategoryTreeVO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> nameMap(String lang) {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .select(Category::getId, Category::getName));
        Map<Long, String> map = new HashMap<Long, String>();
        for (Category category : categories) {
            map.put(category.getId(), category.getName());
        }
        Map<Long, String> translated = loadCategoryNameMap(lang);
        for (Map.Entry<Long, String> entry : translated.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listDescendantIds(Long categoryId) {
        if (categoryId == null) {
            return new ArrayList<Long>();
        }
        Category root = categoryMapper.selectById(categoryId);
        if (root == null) {
            return new ArrayList<Long>();
        }
        if (root.getPath() == null || root.getPath().trim().isEmpty()) {
            List<Long> onlySelf = new ArrayList<Long>();
            onlySelf.add(categoryId);
            return onlySelf;
        }

        String prefix = root.getPath() + "/";
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .select(Category::getId)
                .and(w -> w.eq(Category::getId, categoryId)
                        .or()
                        .likeRight(Category::getPath, prefix)));

        Set<Long> ids = new LinkedHashSet<Long>();
        for (Category category : categories) {
            ids.add(category.getId());
        }
        if (ids.isEmpty()) {
            ids.add(categoryId);
        }
        return new ArrayList<Long>(ids);
    }

    private void saveEnglishName(Long categoryId, String enName) {
        if (categoryId == null) {
            return;
        }
        String value = enName == null ? "" : enName.trim();
        CategoryI18n exists = categoryI18nMapper.selectOne(new LambdaQueryWrapper<CategoryI18n>()
                .eq(CategoryI18n::getCategoryId, categoryId)
                .eq(CategoryI18n::getLangCode, LanguageUtil.EN_LANG)
                .last("limit 1"));
        if (value.isEmpty()) {
            if (exists != null) {
                categoryI18nMapper.deleteById(exists.getId());
            }
            return;
        }

        if (exists == null) {
            CategoryI18n row = new CategoryI18n();
            row.setCategoryId(categoryId);
            row.setLangCode(LanguageUtil.EN_LANG);
            row.setName(value);
            categoryI18nMapper.insert(row);
        } else {
            exists.setName(value);
            categoryI18nMapper.updateById(exists);
        }
    }

    private Map<Long, String> loadCategoryNameMap(String lang) {
        String normalized = LanguageUtil.normalize(lang);
        if (LanguageUtil.isDefaultLang(normalized)) {
            return new HashMap<Long, String>();
        }
        List<CategoryI18n> list = categoryI18nMapper.selectList(new LambdaQueryWrapper<CategoryI18n>()
                .eq(CategoryI18n::getLangCode, normalized));
        Map<Long, String> map = new HashMap<Long, String>();
        for (CategoryI18n row : list) {
            map.put(row.getCategoryId(), row.getName());
        }
        return map;
    }
}
