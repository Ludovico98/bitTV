# Keep nothing extra: Compose and Coil ship their own consumer rules.

# ComposeA11y: the Compose internals it reaches by reflection (names and the two fields)
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView { *** composeAccessibilityDelegate; }
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat { *** _enabledServices; }
-keepnames class androidx.compose.ui.platform.AndroidComposeView
-keepnames class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat
