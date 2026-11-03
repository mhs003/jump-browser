# R8 configuration for Jump.
#
# The goal is a small artifact: everything is compiled against and then
# stripped down to the members the app actually reaches. Only genuinely
# reflectively-accessed entry points are kept here.

# --- WebView JavaScript bridge -------------------------------------------
# @JavascriptInterface methods are looked up by name from WebView, so their
# names must survive both renaming and shrinking.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# --- AndroidX / Compose ---------------------------------------------------
# These libraries ship consumer rules for everything they reflect on. The
# blanket -dontwarn only silences optional integrations that are absent from
# this dependency set.
-dontwarn androidx.**

# --- Size -----------------------------------------------------------------
# Letting R8 relax access modifiers and repackage every class into one flat
# namespace enables horizontal class merging and better inlining, which is
# worth several percent of dex. Nothing here is reached reflectively.
-allowaccessmodification
-repackageclasses ''
