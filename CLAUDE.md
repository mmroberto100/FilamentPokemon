# Project Context: Pokemon Filament (Mobile GLB Viewer)

## Overview
Pokemon Filament is a mobile application (Android/iOS) designed to search, filter, temporarily store, and render 3D **Pokémon `.glb` models** retrieved from the Sketchfab API using **Google Filament** as the physically-based rendering (PBR) graphics engine.

The core goal of this project is to allow users to search and view Pokémon 3D models smoothly on mobile hardware by strictly filtering out assets that exceed mobile polygon count safety budgets.

---

## Technical Stack & Dependencies

- **Rendering Engine**: Google Filament (`com.google.android.filament:filament-android`, `gltfio-android`, `filament-utils-android`)
- **API Integrations**:
  - **Sketchfab Data API v3**: For model search, metadata parsing (`faceCount`), and filtering.
  - **Sketchfab Download API / OAuth 2.0**: For retrieving binary `.glb` asset URLs.
- **Model Formats & Extensions**: glTF 2.0 Binary (`.glb`) with support for `KHR_draco_mesh_compression` and `EXT_meshopt_compression`.
- **Target Platforms**: Android (Kotlin/Java) & iOS (Swift/C++).

---

## Core Requirements & Scope

### 1. Pokémon Model Filtering Only
- All queries to the Sketchfab Data API v3 must strictly filter for Pokémon 3D assets (`q=pokemon`, category/tag filtering).
- Exclude non-Pokémon or non-downloadable assets.

### 2. Triangle / Polygon Safety Thresholds
- Hardware target: Mobile GPUs (Mali, Adreno, Apple GPU).
- **Hard Limit**: Maximum **30,000 to 50,000 triangles** (`faceCount`) per character model.
- Before initiating a download, parse the Sketchfab API model metadata response. If `faceCount > 30000` (or user-configured threshold), reject/skip the model to prevent frame drops or out-of-memory (OOM) issues on mobile devices.

### 3. Temporary File Management
- Download `.glb` binary files strictly into the application's temporary cache directory:
  - **Android**: `context.cacheDir`
  - **iOS**: `NSTemporaryDirectory()`
- Never save `.glb` files to permanent user storage.
- Implement automatic cache eviction/cleanup when closing the viewer or when temporary storage limits are reached.

### 4. Filament Rendering Pipeline
- Initialize Filament engine (`Filament.init()`).
- Bind `SurfaceView` / `TextureView` (Android) or `CAEAGLLayer` / `CAMetalLayer` (iOS) via `UiHelper`.
- Use `gltfio` (`AssetLoader`) to parse local `.glb` files from cache into Filament entities.
- Set up physically-based camera, IBL (Image-Based Lighting) environment, directional light, and frame loop (`beginFrame` -> `render` -> `endFrame`).

---

## Architecture Flow

```
[ Sketchfab Data API v3 ]
        │
        ▼ (Metadata: q="pokemon", downloadable=true)
[ Triangle Count Check ] ──(faceCount > 30k)──► [ Skip / Filter Out ]
        │
        ▼ (faceCount <= 30k)
[ Sketchfab Download API ] ──► Fetch direct .glb URL
        │
        ▼
[ Local Temporary Cache ] ──► Write to cacheDir / temp directory
        │
        ▼
[ Filament gltfio Loader ] ──► Load .glb asset into Scene
        │
        ▼
[ Mobile Render Surface ] ──► 60 FPS PBR View
```

---

## Key Sketchfab & Filament References

- **Sketchfab Search API Endpoint**: `https://api.sketchfab.com/v3/search?q=pokemon&type=models&downloadable=true`
- **Sketchfab Model Metadata**: Check `model.faceCount` or `model.vertexCount` in the API payload.
- **Filament Android Dependencies**:
  ```groovy
  implementation 'com.google.android.filament:filament-android:1.76.1'
  implementation 'com.google.android.filament:gltfio-android:1.76.1'
  implementation 'com.google.android.filament:filament-utils-android:1.76.1'
  ```

---

## Development Guidelines for AI / Claude Code
- Keep all network operations non-blocking (async / coroutines / async-await).
- Ensure memory safety: Destroy Filament assets (`assetLoader.destroyAsset`, `engine.destroy()`) when leaving the Activity/View.
- Handle compressed glTF meshes gracefully using built-in `gltfio` Draco/meshopt decoders.
