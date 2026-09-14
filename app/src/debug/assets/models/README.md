`sample.glb` is Khronos glTF-Sample-Assets "BoxTextured" (CC-BY 4.0, © Cesium),
bundled in **debug builds only** as a smoke-test model for the Filament renderer.

`duck.glb` is Khronos glTF-Sample-Assets "Duck" (CC-BY 4.0, © Sony), from
https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/Duck
(`glTF-Binary/Duck.glb`). The other ducks are the same model re-encoded, all debug-only
smoke-test assets that prove gltfio's built-in decoders on device
(see `androidTest/.../viewer/filament/GltfDecoderTest`):

- `duck_draco.glb` – `@gltf-transform/cli draco`; requires `KHR_draco_mesh_compression`.
- `duck_meshopt.glb` – `@gltf-transform/cli meshopt`; requires `EXT_meshopt_compression` and,
  because that command quantizes vertex attributes, `KHR_mesh_quantization` (positions are
  normalized `SHORT`s dequantized by the mesh node's scale/translation).
- `duck_meshopt_unquantized.glb` – `EXT_meshopt_compression` applied through the
  gltf-transform API to the untouched float32 attributes, so it exercises the meshopt decoder
  alone. It exists because gltfio 1.75.1 derives `FilamentAsset.boundingBox` from the raw
  accessor min/max and ignores `normalized`, so the quantized duck decodes and renders correctly
  but reports bounds 32767x too large, which breaks `ModelViewer.transformToUnitCube()`.

`corrupt.glb` is hand-made: a valid 12-byte glTF-Binary header (magic, version 2, declared
length equal to the file size) followed by a `JSON` chunk header whose payload is not JSON.
It passes `GlbHeader.isValid` and fails inside the gltfio parser, so it exercises the
renderer-failure path rather than the header check.
