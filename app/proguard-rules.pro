# Keep Room generated implementations reachable through reflection-free codegen.
-keepclassmembers class * extends androidx.room.RoomDatabase { public <init>(); }

# Enum constants are persisted by name (Room converters, the Direct Boot mirror and the pending-occurrence
# store all call valueOf), so R8 must not rewrite or drop them.
-keepclassmembers enum dev.upyet.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
