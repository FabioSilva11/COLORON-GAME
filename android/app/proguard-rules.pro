# Métodos nativos chamados via JNI
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.coloron3d.game.engine.NativeBridge { *; }
