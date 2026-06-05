package com.szwego.gallery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szwego.gallery.domain.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper extends BaseMapper<Product> {

    Long countForGalleryPage(@Param("status") Integer status,
                             @Param("topOnly") Integer topOnly,
                             @Param("keyword") String keyword,
                             @Param("sku") String sku,
                             @Param("categoryIds") List<Long> categoryIds,
                             @Param("translatedMatches") List<Long> translatedMatches);

    List<Long> selectGalleryPageIds(@Param("status") Integer status,
                                    @Param("topOnly") Integer topOnly,
                                    @Param("keyword") String keyword,
                                    @Param("sku") String sku,
                                    @Param("categoryIds") List<Long> categoryIds,
                                    @Param("translatedMatches") List<Long> translatedMatches,
                                    @Param("offset") long offset,
                                    @Param("size") long size);
}
