package com.example.gemma4ondevicetest.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

object AppUsageAllowlistPolicy {
    private val allowedPackageNames: Set<String> = setOf(
        "com.nhn.android.webtoon",
        "com.naver.linewebtoon",
        "com.naverfin.payapp",
        "com.sampleapp",
        "com.btckorea.bithumb",
        "com.samsungpop.android.mpop",
        "com.shinhan.sbanking",
        "com.shcard.smartpay",
        "com.kiwoom.heromts",
        "com.kakao.taxi",
        "com.locnall.KimGiSa",
        "net.daum.android.map",
        "com.kakao.bus",
        "net.orizinal.subway",
        "com.kakao.talk",
        "com.catchtable",
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.korail.talk",
        "viva.republica.toss",
        "com.openai.chatgpt",
        "com.android.chrome",
        "com.anthropic.claude",
        "com.instagram.android",
        "com.kbcard.cxh.appcard",
        "kr.co.kfcc.mobilebank",
        "com.samsung.android.monimo",
        "com.nhn.android.search",
        "com.sktelecom.tmembership",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music"
    ).map { it.lowercase(Locale.ROOT) }.toSet()

    private val allowedPackageTokens: List<String> = listOf(
        "webtoon",
        "naverfin",
        "bithumb",
        "samsungpop",
        "shinhan",
        "shcard",
        "kiwoom",
        "kakao.taxi",
        "kakao.bus",
        "kakao.talk",
        "kakaonavi",
        "catchtable",
        "korail",
        "openai.chatgpt",
        "anthropic.claude",
        "instagram",
        "kbpay",
        "kbcard",
        "kfcc",
        "monimo",
        "tmembership",
        "youtube.music"
    )

    private val allowedAppNames: List<String> = listOf(
        "네이버웹툰",
        "네이버페이",
        "배달의민족",
        "빗썸",
        "삼성증권",
        "신한은행",
        "신한카드",
        "영웅문sglobal",
        "카카오t",
        "카카오내비",
        "카카오맵",
        "카카오버스",
        "카카오지하철",
        "카카오톡",
        "캐치테이블",
        "캘린더",
        "코레일톡",
        "토스",
        "gpt",
        "chrome",
        "claude",
        "instagram",
        "kbpay",
        "새마을금고",
        "monimo",
        "네이버",
        "t멤버십",
        "youtube",
        "youtubemusic"
    ).map(::normalize)

    fun shouldCollect(context: Context, packageName: String): Boolean {
        val normalizedPackageName = packageName.lowercase(Locale.ROOT)
        if (normalizedPackageName in allowedPackageNames) return true
        if (allowedPackageTokens.any { normalizedPackageName.contains(it) }) return true

        val appName = normalize(resolveAppLabel(context, packageName))
        return allowedAppNames.any { allowed ->
            appName == allowed || appName.contains(allowed) || allowed.contains(appName)
        }
    }

    fun resolveCategory(context: Context, packageName: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ApplicationInfo.CATEGORY_UNDEFINED
        }
        return try {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            ).category
        } catch (_: Throwable) {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).category
            } catch (_: Throwable) {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
        }
    }

    fun categoryLabel(context: Context, category: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "미분류"
        return ApplicationInfo.getCategoryTitle(context, category)?.toString() ?: "미분류"
    }

    fun allowedAppNamesLabel(): String = listOf(
        "네이버 웹툰", "네이버페이", "배달의민족", "빗썸", "삼성증권", "신한은행", "신한카드",
        "영웅문S글로벌", "카카오T", "카카오내비", "카카오맵", "카카오버스", "카카오지하철",
        "카카오톡", "캐치테이블", "캘린더", "코레일톡", "토스", "GPT", "Chrome", "Claude",
        "Instagram", "KB Pay", "새마을금고", "monimo", "네이버", "T멤버십", "YouTube", "YouTube Music"
    ).joinToString(", ")

    fun resolveAppLabel(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        return runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), "")
            .replace("[^a-z0-9가-힣]".toRegex(), "")
}
