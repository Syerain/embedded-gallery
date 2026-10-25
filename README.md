# Gallery Natyz (embedded gallery)

私人定制的 Android 画廊应用（Kotlin + Jetpack Compose + Material 3）。
画集数据在构建时打包进 APK assets，运行时直接读取。

## 功能
- 开屏：红底白字 `natyz`
- 主页双视图：竖屏瀑布流 / 纵向条目列表（右上角切换；右侧滑动时显示滚动条）
- 画集页：磁贴网格、长按拖动排序（可导出/导入 JSON）、画集与 item 双级收藏、标题重命名
- 查看器：
  - 双指缩放/平移；双指旋转实时跟手、松手 90° 吸附；长按解锁自由旋转（顶部横幅提示）
  - 90°/270° 自适应比例填充；缩小到小于屏幕松手自动恢复全屏；双击复位/放大
  - 左右滑动切换上一张/下一张；进入时按陀螺仪单次采样锁定横竖屏
  - 视频循环播放、0.5x–3x 倍速、长按慢放滑条（按住左右滑动调速率）、双击暂停
- 设置页：动画速率、系统动画接口开关
- 沉浸全屏（隐藏状态栏）、OLED 纯黑背景、高刷新率适配

## 数据打包

媒体文件不入库（见 `.gitignore`），用 `tools/pack_data.py` 重新生成：

```
python tools/pack_data.py \
  --src <包含 zh/ 与 en/ 的数据根目录> \
  --dst app/src/main/assets/gallery \
  --titles-zh tools/titles_zh.json \
  --img-quality 90 --thumbs \
  --video-dir <视频目录> --video-gallery <目标画集目录名>
```

规则：en 为全集基准，zh 优先（en 独有文件并入）；图片转 webp 有损并生成缩略图；
封面取编号最小者。

## 构建

Android Studio 打开工程根目录，或命令行 `./gradlew assembleDebug`。
minSdk 26 / targetSdk 34。
