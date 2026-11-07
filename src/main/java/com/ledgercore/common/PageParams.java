package com.ledgercore.common;

/** The validated page number and page size of a list request. */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageParams of(Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0) {
            throw new InvalidQueryException("page must be 0 or greater.");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new InvalidQueryException("size must be between 1 and " + MAX_SIZE + ".");
        }
        return new PageParams(resolvedPage, resolvedSize);
    }
}