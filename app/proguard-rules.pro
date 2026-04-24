# Mantém as classes de criptografia do AndroidX e JCE
-keep class javax.crypto.** { *; }
-keep class com.notara.security.ShareSecurityHelper { *; }
-dontwarn javax.crypto.**

# Se usar a biblioteca de segurança do Google
-keep class androidx.security.crypto.** { *; }
