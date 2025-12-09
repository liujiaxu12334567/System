package com.project.system.dto;

import lombok.Data;
import java.util.List;

/**
 * 通用分页返回数据的包装类 (DTO)
 */
@Data
public class PaginationResponse<T> {
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public PaginationResponse(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}