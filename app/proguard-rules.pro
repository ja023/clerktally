# CrewTally ProGuard / R8 rules.
#
# Baseline: Room generates its own keep rules and Compose is handled by the AGP defaults, so
# nothing is needed for those. The rules below cover the one thing R8 CANNOT infer on its own
# in this app: kotlinx.serialization.

# ---------------------------------------------------------------------------------------------
# kotlinx.serialization — REQUIRED, or backup restore breaks in release builds ONLY.
# ---------------------------------------------------------------------------------------------
# WHY THIS IS NEEDED (and why debug never shows the bug):
# The backup DTOs in com.vague.crewtally.backup (@Serializable CompanyDto, ClerkDto, ProjectDto,
# RosterEntryDto, AttendanceEntryDto, ExtraPayLineDto, PaymentDto, BackupPayload) are serialized
# and deserialized reflectively via their compiler-generated `$serializer` companions. R8 sees
# those synthetic serializer classes and their `Companion` as unused (nothing calls them by name
# in source) and, under minifyEnabled, strips or renames them. Debug builds don't minify, so the
# JSON export/import round-trips fine there and the breakage is invisible until a RELEASE build —
# where restore then throws SerializationException / can't find the serializer at runtime.
#
# These keeps preserve the generated serializers and the @Serializable classes' shape so both
# export and restore keep working after R8 runs. Field NAMES matter too: the JSON is keyed by
# property name, so renaming fields would silently produce backups that a future build can't read.
#
# Scoped to com.vague.crewtally.backup.** — the ONLY package with @Serializable classes
# (backup/BackupModels.kt: CompanyDto, ClerkDto, ProjectDto, RosterEntryDto, AttendanceEntryDto,
# ExtraPayLineDto, PaymentDto, BackupPayload). Scoping to the backup package instead of the whole
# app lets R8 shrink the ~27 unrelated Companion objects elsewhere in com.vague.crewtally.

# Keep the runtime's own annotations/infrastructure.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep every @Serializable class in the backup package, its generated $serializer, and its
# Companion.
-keep,includedescriptorclasses class com.vague.crewtally.backup.**$$serializer { *; }
-keepclassmembers class com.vague.crewtally.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.vague.crewtally.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Preserve @Serializable class field names so the on-disk JSON keys stay stable across builds.
-keepclassmembers @kotlinx.serialization.Serializable class com.vague.crewtally.backup.** {
    <fields>;
}

# ---------------------------------------------------------------------------------------------
# Enum values() / valueOf() — Converters and DTOs round-trip status strings, but the enum
# reflection R8 default rule already covers standard enums; kept explicit here for the app
# package as documentation of intent.
# ---------------------------------------------------------------------------------------------
-keepclassmembers enum com.vague.crewtally.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
