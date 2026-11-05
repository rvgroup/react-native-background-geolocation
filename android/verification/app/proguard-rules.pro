# This build fixture has no JS bundle. Keep the RN module entry points reachable
# so R8 checks the actual geolocation code, rather than removing the whole API.
-keep class com.marianhello.bgloc.react.BackgroundGeolocationModule { *; }
-keep class com.marianhello.bgloc.react.BackgroundGeolocationPackage { *; }
