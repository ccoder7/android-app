-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

-dontobfuscate

# prevent multi dex caused NoSuchProviderException
-keep class org.whispersystems.** { *; }

-keep class one.mixin.android.** { *; }

-keep class io.jsonwebtoken.** { *; }

# webrtc
-dontwarn org.webrtc.NetworkMonitorAutoDetect
-dontwarn android.net.Network
-keep class org.webrtc.** { *; }

# androidx paging
-keep class androidx.paging.PagedListAdapter.** { *; }
-keep class androidx.paging.AsyncPagedListDiffer.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
