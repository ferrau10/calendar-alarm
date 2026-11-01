# Credential Manager loads its Play Services provider by reflection (documented requirement for R8).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Keep line numbers in crash stack traces; the mapping file is attached to each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
