package com.oakfield.clinic.svc;

import java.util.List;

/**
 * Simple pagination envelope shared by all list endpoints/pages.
 * Exposes both record-style accessors (items(), total()) and bean-style
 * getters (getItems(), getTotal()) so EL and Java can read it either way.
 */
public class PageResult<T> {

    private final List<T> items;
    private final long total;
    private final int page;
    private final int pageSize;

    public PageResult(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> items() { return items; }
    public long total() { return total; }
    public int page() { return page; }
    public int pageSize() { return pageSize; }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public int totalPages() {
        return pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
    }

    public int getTotalPages() {
        return totalPages();
    }
}
