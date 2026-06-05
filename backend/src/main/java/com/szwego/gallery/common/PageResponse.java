package com.szwego.gallery.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {
    private long page;
    private long size;
    private long total;
    private long pages;
    private List<T> records;
}
