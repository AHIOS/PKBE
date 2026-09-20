package com.uci.pkbe

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object PasskeyDiagnostics {
    suspend fun runPreflight(context: Context, api: ApiClient): String {
        val lines = mutableListOf<String>()
        lines += "=== PKBE Android passkey preflight ==="
        lines += "package=${context.packageName}"
        lines += "Config.rpId=${Config.rpId}"
        lines += "Config.baseURL=${Config.baseUrl}"
        lines += "sdk=${Build.VERSION.SDK_INT}"
        signingSha256(context)?.let { lines += "debugSha256=$it" }

        try {
            val cfg = api.publicConfig()
            lines += "server.rpId=${cfg.rpId}"
            lines += "server.publicBaseUrl=${cfg.publicBaseUrl}"
            lines += "server.origins=${cfg.origins.joinToString()}"
            lines += "server.androidPackageName=${cfg.androidPackageName}"
            lines += "server.assetLinksConfigured=${cfg.assetLinksConfigured}"
            if (cfg.rpId != Config.rpId) {
                lines += "MISMATCH: server.rpId != Config.rpId"
            }
            if (cfg.androidPackageName.isNotBlank() && cfg.androidPackageName != context.packageName) {
                lines += "MISMATCH: server package (${cfg.androidPackageName}) != app (${context.packageName})"
            }
            if (!cfg.assetLinksConfigured) {
                lines += "WARN: assetlinks.json is empty. Set PKBE_ANDROID_PACKAGE_NAME and PKBE_ANDROID_SHA256_FINGERPRINTS so Credential Manager can use https://${cfg.rpId} as origin."
            }
        } catch (e: Exception) {
            lines += "public-config fetch FAILED: ${e.message}"
        }

        try {
            val (status, body) = api.fetchText("/.well-known/assetlinks.json")
            lines += "assetlinks status=$status bytes=${body.length}"
            lines += body.take(500)
        } catch (e: Exception) {
            lines += "assetlinks fetch FAILED: ${e.message}"
        }
        return lines.joinToString("\n")
    }

    fun signingSha256(context: Context): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val bytes = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
