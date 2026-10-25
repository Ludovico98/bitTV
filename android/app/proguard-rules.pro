# Keep nothing extra: Compose and Coil ship their own consumer rules.

# Anthropic Java SDK (general answers): Jackson reads its models by reflection
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**
-dontwarn java.beans.**
# the SDK's JSON-schema generator (unused here) references desktop-only reflection types
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
# ComposeA11y: the Compose internals it reaches by reflection (names and the two fields)
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView { *** composeAccessibilityDelegate; }
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat { *** _enabledServices; }
-keepnames class androidx.compose.ui.platform.AndroidComposeView
-keepnames class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat
