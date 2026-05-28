# Preserve source file names and line numbers so crash stack traces
# (Timber, future Crashlytics, etc.) remain readable after minification.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin: preserve metadata used by reflection-free Kotlin features
# (coroutines, serialization, etc. bundle their own rules via AAR consumers).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
