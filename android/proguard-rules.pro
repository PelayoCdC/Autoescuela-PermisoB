# libGDX
-verbose
-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.BuildTarget*

-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
   <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}

# gdx-bullet: el wrapper JNI resuelve clases y metodos por nombre
-keep class com.badlogic.gdx.physics.bullet.** { *; }

# gdx-controllers en Android
-keep class com.badlogic.gdx.controllers.android.AndroidControllers

# POJOs serializados/deserializados por reflexion con com.badlogic.gdx.utils.Json
-keep class com.pelayo.autoescuela.data.** { *; }
