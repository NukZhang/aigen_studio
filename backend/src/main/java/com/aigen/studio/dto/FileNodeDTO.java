package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件节点 DTO
 * 用于表示文件树中的节点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileNodeDTO {
    private String name;
    private String path;
    private boolean isDirectory;
    private String type;
    private Long size;
    private java.util.List<FileNodeDTO> children;
}