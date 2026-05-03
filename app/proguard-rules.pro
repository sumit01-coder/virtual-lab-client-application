
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit service annotations and Gson DTO fields are used reflectively.
-keep interface com.virtuallab.client.api.ApiService { *; }
-keep class com.virtuallab.client.api.dto.** { *; }
-keep class com.virtuallab.client.update.GitHubReleaseInfo { *; }
-keep class com.virtuallab.client.update.GitHubUpdateManager$GitHubReleaseResponse { *; }
-keep class com.virtuallab.client.update.GitHubUpdateManager$ReleaseAsset { *; }
-keep class com.virtuallab.client.offline.SimulationMeta { *; }

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn okhttp3.**
-dontwarn okio.**
