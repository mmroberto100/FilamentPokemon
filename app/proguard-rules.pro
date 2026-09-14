# Project R8 rules for the release build. AGP's proguard-android-optimize defaults and the
# consumer rules bundled with the dependencies are applied automatically; only what they lack
# belongs here. Already covered, do not repeat:
#   Filament JNI (@UsedByNative/@UsedByReflection members, KTX1Loader, HDRLoader): filament-*.aar
#   kotlinx.serialization (Companion, serializer(), INSTANCE, runtime annotations): serialization-core
#   Ktor engines and atomic fields, OkHttp, Coil, Koin, DataStore, coroutines: their own jars/aars

# Keep line numbers so release crash stacks retrace through app/build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable

# Hide real source file names in stack traces; line numbers still resolve through mapping.txt.
-renamesourcefileattribute SourceFile
