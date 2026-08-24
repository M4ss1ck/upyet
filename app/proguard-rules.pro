# Keep Room generated implementations reachable through reflection-free codegen.
-keepclassmembers class * extends androidx.room.RoomDatabase { public <init>(); }
