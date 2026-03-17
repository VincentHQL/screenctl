package com.scrctl.client.ui.file

/**
 * 存储位置选项
 */
enum class StorageTab {
    Local,  // 本地存储
    Remote  // 远程设备
}

/**
 * 文件/文件夹类型
 */
enum class EntryKind(val isFolder: Boolean) {
    Folder(true),       // 普通文件夹
    ImageFolder(true),  // 图片文件夹
    SharedFolder(true), // 共享文件夹
    Image(false),       // 图片文件
    Video(false),       // 视频文件
    Doc(false),         // 文档文件
}

/**
 * 文件/文件夹条目数据类
 */
data class FileEntry(
    val id: String,
    val kind: EntryKind,
    val name: String,
    val meta: String,           // 元数据信息（大小、日期等）
    val checked: Boolean = false, // 是否被选中
)

/**
 * FileManager UI状态数据类
 */
data class FileManagerUiState(
    val selectedStorage: StorageTab,
    val breadcrumb: String,
    val searchVisible: Boolean,
    val searchQuery: String,
    val entries: List<FileEntry>,
    val visibleEntries: List<FileEntry>,
    val selectedCount: Int,
)