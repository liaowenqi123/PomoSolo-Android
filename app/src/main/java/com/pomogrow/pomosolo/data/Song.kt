package com.pomogrow.pomosolo.data

/**
 * 一首歌的目录行。
 *
 * @param file 唯一标识：源文件名（服务器曲库 = 云端 mp3 名；内置曲 = assets/tracks 名；
 *             本地导入 = 落盘文件名）。
 * @param title 展示名（去扩展名）。
 * @param tag 标签（仅内置曲带有：运动 / 学习 / 主题曲）。
 * @param bundled 是否随 APK 内置（assets/tracks/ 下可找到原始 mp3，可无网下载）。
 */
data class Song(
    val file: String,
    val title: String,
    val tag: String?,
    val bundled: Boolean,
)

/** 本地导入的歌曲（可能不在服务器曲库目录中）。 */
data class LocalImport(
    val file: String,
    val title: String,
)
