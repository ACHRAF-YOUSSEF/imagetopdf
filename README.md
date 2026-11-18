# Image to PDF Converter (Jetpack Compose)

An offline **PDF toolkit and image-to-PDF converter** built with Kotlin and Jetpack Compose. Featuring a **dark-themed Material 3 UI with blue accents**, the app lets you convert images, scan documents with CameraX, import, compress, merge, split, and export PDFs, and convert DOCX or plain text files — all processed and stored locally on your device.

---

## Features

- **Image to PDF & Reorder:** Pick multiple images from your gallery, preview full-screen with pinch-to-zoom, drag to reorder pages, select `A4`, `Letter`, or `Fit Image` page sizes, and save with custom naming and overwrite or auto-increment conflict handling.
- **Smart Scan (CameraX):** Capture document pages with flash and lens toggles, an A4 alignment overlay, gallery fallback, EXIF orientation correction, and local image filters (`Original`, `Magic Contrast`, `Crisp B&W`) before sending scans straight to reorder and export.
- **Import PDF:** Bring existing PDF documents into the app via the system picker and manage them alongside generated files.
- **Compress PDF:** Reduce PDF file sizes locally across three quality presets (`Low`, `Balanced`, `Extreme`) with byte-savings reporting.
- **Merge PDF:** Combine multiple PDFs in a customizable drag-and-drop sequence.
- **Split & Extract PDF:** Select individual page thumbnails or enter page range expressions (for example, `1-3, 5`) to extract a single combined PDF or split into separate files.
- **PDF to JPG:** Export selected or all PDF pages to `Pictures/ImageToPdf` in `Standard` or `HD` resolution and share exported images directly.
- **DOCX to PDF:** Convert Word `.docx` files offline (headings, paragraphs, lists, tables, and embedded images) using `ZipInputStream`, `XmlPullParser`, and `StaticLayout` pagination without heavy desktop JVM dependencies.
- **Text to PDF:** Turn plain text notes or documents into paginated `A4` or `Letter` PDFs.
- **Favorites, Search & Sort:** Star favorite PDFs (persisted with DataStore Preferences), filter by favorites, search in real time across files, and sort by date, name, or size.
- **Share, Rename & Delete:** Open, share, rename, or delete PDFs safely using `MediaStore` and scoped `FileProvider` URIs across Home, Files, and Search views.
- **Settings & Info:** View build details, app information, persistent folder storage link controls, and source links.
- **Persistent Folder Storage:** PDFs save to `Download/ImageToPdf` by default, and linking `Download/ImageToPdf` (from the Home card, Files toolbar, or Settings) preserves full access to previously generated PDFs across new APK builds, signing key changes, and reinstalls.
- **Offline-First & Hardened:** Zero internet dependency (`android:usesCleartextTraffic="false"`), canonical file-path confinement, and R8 code minification and resource shrinking enabled for release builds.

---

## Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
- **Camera & Enhancement:** [CameraX](https://developer.android.com/training/camerax) + [AndroidX ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface)
- **PDF Generation & Rendering:** [PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) & [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)
- **DOCX Parsing:** [XmlPullParser](https://developer.android.com/reference/org/xmlpull/v1/XmlPullParser) + `ZipInputStream` + `StaticLayout`
- **Persistence & Media:** [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore), [MediaStore](https://developer.android.com/reference/android/provider/MediaStore) (Android 10+), `FileProvider`
- **Image Loading & Drag-and-Drop:** [Coil](https://coil-kt.github.io/coil/) & [Reorderable](https://github.com/Calvin-LL/Reorderable)
- **Architecture & Navigation:** MVVM (`StateFlow` + `ViewModel`) & [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- **Testing & Code Quality:** JUnit 4, Robolectric, Android Lint, JaCoCo, and SonarQube
- **Min SDK:** 26 · **Target SDK:** 35

---

## Design System

| Token              | Value       |
|--------------------|-------------|
| Background         | `#0D1117`   |
| Card / Surface     | `#161B22`   |
| Primary Text       | `#E6EDF3`   |
| Secondary Text     | `#8B949E`   |
| Accent (Blue)      | `#2F81F7`   |
| PDF Badge (Red)    | `#E74C3C`   |
| Corner Radius      | 12 - 16 dp  |

Dark-only theme with clean sans-serif typography.

---

## Project Structure

```
tech.youssefachraf.image_to_pdf/
├── MainActivity.kt
├── data/
│   └── FavoritesRepository.kt
├── navigation/
│   └── AppNavGraph.kt
├── ui/
│   ├── common/
│   │   ├── PdfIntentActions.kt
│   │   └── ToolUiComponents.kt
│   ├── splash/SplashScreen.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── components/
│   │       ├── MoreToolsSheet.kt
│   │       ├── PdfCard.kt
│   │       └── ToolGrid.kt
│   ├── files/FilesScreen.kt
│   ├── search/SearchScreen.kt
│   ├── settings/SettingsScreen.kt
│   ├── scan/SmartScanScreen.kt
│   ├── picker/
│   │   ├── ImagePickerSheet.kt
│   │   └── PreviewReorderScreen.kt
│   ├── tools/
│   │   ├── CompressPdfScreen.kt
│   │   ├── DocxToPdfScreen.kt
│   │   ├── MergePdfScreen.kt
│   │   ├── PdfToJpgScreen.kt
│   │   ├── SplitPdfScreen.kt
│   │   └── TextToPdfScreen.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── viewmodel/
│   ├── MainViewModel.kt
│   ├── PickerViewModel.kt
│   └── ToolsViewModel.kt
└── utils/
    ├── DocumentEnhancer.kt
    ├── DocxToPdfConverter.kt
    ├── PdfConverter.kt
    ├── PdfPageCanvasWriter.kt
    ├── PdfStorageFolder.kt
    ├── PdfThumbnailCache.kt
    └── PdfToolkit.kt
```

---

## Screenshots

### Home & File Management
Browse your tools and recently created PDFs, search, share, rename, or delete.

<p align="center">
  <img src="screenshots/home_screen_1.jpg" width="30%" alt="Home Screen 1">
  <img src="screenshots/home_screen_2.jpg" width="30%" alt="Home Screen 2">
  <img src="screenshots/home_screen_3.jpg" width="30%" alt="Home Screen 3">
</p>

### Image Selection, Reorder & Generation
Pick images, preview them full-screen, drag to reorder pages, and generate your PDF.

<p align="center">
  <img src="screenshots/picker_snackbar.jpg" width="30%" alt="Image Picker">
  <img src="screenshots/preview_and_reorder_screen.jpg" width="30%" alt="Preview & Reorder">
  <img src="screenshots/preview_image.jpg" width="30%" alt="Full-Screen Preview">
</p>
<p align="center">
  <img src="screenshots/generate_pdf.jpg" width="30%" alt="Generate PDF">
  <img src="screenshots/generating_pdf.jpg" width="30%" alt="Generating PDF">
</p>

### Settings
View app info and access the source code.

<p align="center">
  <img src="screenshots/settings_screen.jpg" width="30%" alt="Settings Screen">
</p>

---

## Build & Run

```bash
# Clone
git clone https://github.com/ACHRAF-YOUSSEF/imagetopdf.git
cd imagetopdf

# Run unit tests and Android Lint
./gradlew testDebugUnitTest lintDebug

# Build debug or R8-minified release APK
./gradlew assembleDebug
./gradlew assembleRelease

# Install debug build on a connected device
./gradlew installDebug
```

---

## License

This project is open-source and available under the [MIT License](LICENSE).
