package com.campusstore.api.dto;

import java.util.List;

public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public PagedResponse() {}

    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <T> PagedResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    /**
     * Alias for {@link #getPage()} named to match Spring {@code Page#getNumber()} so
     * existing Thymeleaf templates that read {@code page.number} continue to work
     * unchanged. Marked {@link com.fasterxml.jackson.annotation.JsonIgnore} so the wire
     * JSON remains exactly {@code page/size/totalPages/totalElements/content}.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getNumber() { return page; }

    /** Template convenience — true when this is the first page. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFirst() { return page <= 0; }

    /** Template convenience — true when this is the last page. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isLast() { return totalPages == 0 || page >= totalPages - 1; }

    /** Template convenience — true when a next page exists. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isHasNext() { return page < totalPages - 1; }

    /** Template convenience — true when a previous page exists. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isHasPrevious() { return page > 0; }

    /** Template convenience — true when content is non-empty. Matches Spring {@code Page#hasContent()}. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasContent() { return content != null && !content.isEmpty(); }
}
