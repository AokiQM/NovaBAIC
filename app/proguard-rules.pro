# Keep kotlinx.serialization generated serializers for our model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.verlintas.baic2.** {
    *** Companion;
}
-keepclasseswithmembers class com.verlintas.baic2.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.verlintas.baic2.**$$serializer { *; }
-keep class com.verlintas.baic2.core.model.** { *; }

# SnakeYAML references desktop java.beans introspection we never use.
-dontwarn java.beans.**
-keep class org.yaml.snakeyaml.** { *; }
