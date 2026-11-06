package com.focus.guard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.util.Locale

// ---- Design tokens ----
class Pal(val bg: Color, val card: Color, val tx: Color, val mu: Color, val bd: Color)
val Pr = Color(0xFF4F6BFF); val Pr2 = Color(0xFF7C5CFF); val Ok = Color(0xFF1FA971); val Er = Color(0xFFE5484D)
val LightP = Pal(Color(0xFFF4F5F8), Color.White, Color(0xFF12151C), Color(0xFF6A7282), Color(0xFFE5E8EE))
val DarkP = Pal(Color(0xFF0B0D12), Color(0xFF141821), Color(0xFFECEEF3), Color(0xFF8E96A6), Color(0xFF242A36))
val LC = staticCompositionLocalOf { LightP }
val LL = compositionLocalOf { "en" }
val Grad = Brush.linearGradient(listOf(Pr, Pr2))
val R20 = RoundedCornerShape(20.dp); val R14 = RoundedCornerShape(14.dp); val R13 = RoundedCornerShape(13.dp)

// ---- Translations: ar / en / fr ----
private val T = mapOf(
    "home" to listOf("الرئيسية", "Home", "Accueil"), "apps" to listOf("التطبيقات", "Apps", "Apps"),
    "settings" to listOf("الإعدادات", "Settings", "Réglages"),
    "running" to listOf("المراقبة تعمل", "Monitoring is on", "Suivi actif"),
    "paused" to listOf("التطبيقات موقوفة", "Apps are paused", "Apps en pause"),
    "nightNow" to listOf("الوضع الليلي نشط", "Night mode is on", "Mode nuit actif"),
    "used" to listOf("استخدام التطبيقات المراقبة", "Monitored usage", "Usage suivi"),
    "min" to listOf("د", "min", "min"), "backIn" to listOf("تعود بعد", "Back in", "Retour dans"),
    "limit" to listOf("حد الاستخدام", "Usage limit", "Limite d’usage"),
    "limitD" to listOf("الحد الأقصى 90 دقيقة.", "Maximum 90 minutes.", "Maximum 90 minutes."),
    "blockFor" to listOf("مدة الإيقاف", "Pause length", "Durée de pause"),
    "blockForD" to listOf("المدة التي تتوقف فيها التطبيقات بعد بلوغ الحد.", "How long apps stay off after the limit.", "Durée de pause après la limite."),
    "night" to listOf("الوضع الليلي", "Night mode", "Mode nuit"),
    "nightD" to listOf("يمنع كل التطبيقات بعد 00:00 عدا تطبيقات النظام.", "Blocks all apps after 00:00 except system apps.", "Bloque toutes les apps après 00:00 sauf celles du système."),
    "nightEnd" to listOf("ينتهي عند الساعة", "Ends at", "Se termine à"),
    "locked" to listOf("لا يمكن تغييره أثناء الليل.", "Locked during the night.", "Verrouillé pendant la nuit."),
    "lang" to listOf("اللغة", "Language", "Langue"), "auto" to listOf("تلقائي", "Auto", "Auto"),
    "suggested" to listOf("مقترح", "Suggested", "Suggéré"),
    "loading" to listOf("جارٍ التحميل…", "Loading…", "Chargement…"),
    "empty" to listOf("لا توجد تطبيقات لعرضها.", "No apps to show.", "Aucune app à afficher."),
    "appsD" to listOf("اختر التطبيقات التي تريد تحديد وقتها.", "Choose the apps to limit.", "Choisissez les apps à limiter."),
    "setup" to listOf("خطوات الإعداد", "Setup", "Configuration"),
    "setupD" to listOf("امنح الأذونات لبدء المراقبة.", "Grant these permissions to start.", "Accordez ces autorisations pour démarrer."),
    "pUsage" to listOf("الوصول إلى الاستخدام", "Usage access", "Accès à l’usage"),
    "pUsageD" to listOf("لمعرفة التطبيق المفتوح ومدة استخدامه.", "To see which app is open and for how long.", "Pour savoir quelle app est ouverte."),
    "pOver" to listOf("العرض فوق التطبيقات", "Display over apps", "Affichage par-dessus"),
    "pOverD" to listOf("لإظهار شاشة الحظر.", "To show the block screen.", "Pour afficher l’écran de blocage."),
    "pBat" to listOf("استثناء البطارية (اختياري)", "Battery exemption (optional)", "Exemption batterie (facultatif)"),
    "pBatD" to listOf("حتى لا يوقف النظام المراقبة.", "So the system keeps monitoring.", "Pour que le suivi continue."),
    "grant" to listOf("منح", "Grant", "Autoriser"), "done" to listOf("تم", "Done", "Fait"),
    "bTitleL" to listOf("انتهى وقتك المسموح", "Time limit reached", "Limite atteinte"),
    "bDescL" to listOf("خذ استراحة. تعود التطبيقات بعد", "Take a break. Apps return in", "Faites une pause. Retour dans"),
    "bTitleN" to listOf("وقت النوم", "Night mode", "Mode nuit"),
    "bDescN" to listOf("التطبيقات متوقفة حتى الساعة", "Apps are off until", "Apps désactivées jusqu’à"),
    "adv" to listOf("متقدم", "Advanced", "Avancé"),
    "protOn" to listOf("الحماية مفعّلة.", "Protection is on.", "Protection active."),
    "protOff" to listOf("الحماية متوقفة.", "Protection is off.", "Protection désactivée."),
    "reqOff" to listOf("طلب الإيقاف", "Request turn off", "Demander l’arrêt"),
    "wait" to listOf("انتظر", "Wait", "Attendez"), "cancel" to listOf("إلغاء", "Cancel", "Annuler"),
    "confirmOff" to listOf("تأكيد الإيقاف", "Confirm turn off", "Confirmer l’arrêt"),
    "turnOn" to listOf("تفعيل", "Turn on", "Activer"),
    "homeBtn" to listOf("العودة إلى الشاشة الرئيسية", "Go to home screen", "Retour à l’accueil")
)
@Composable fun t(k: String): String = T[k]!![when (LL.current) { "ar" -> 0; "fr" -> 2; else -> 1 }]

// ---- Theme wrapper (dark mode, RTL, language, background) ----
@Composable fun Themed(pref: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme(); val c = if (dark) DarkP else LightP
    val sys = Locale.getDefault().language
    val lang = if (pref == "auto") (if (sys in listOf("ar", "fr")) sys else "en") else pref
    CompositionLocalProvider(LC provides c, LL provides lang,
        LocalLayoutDirection provides (if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Pr) else lightColorScheme(primary = Pr)) {
            Box(Modifier.fillMaxSize().background(c.bg)
                .background(Brush.radialGradient(listOf(Pr.copy(.12f), Color.Transparent), Offset(0f, 0f), 900f))
                .background(Brush.radialGradient(listOf(Pr2.copy(.10f), Color.Transparent), Offset(1200f, 0f), 800f)),
                contentAlignment = Alignment.TopCenter) { content() }
        }
    }
}

// ---- Components ----
@Composable fun Panel(mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LC.current
    Column(mod.fillMaxWidth().shadow(2.dp, R20).background(c.card, R20).border(1.dp, c.bd, R20).padding(16.dp), content = content)
}
@Composable fun Title(s: String) = Text(s, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = LC.current.tx)
@Composable fun Sub(s: String) = Text(s, fontSize = 13.sp, color = LC.current.mu, lineHeight = 20.sp)
@Composable fun Tile(ch: String) = Box(Modifier.size(46.dp).background(Pr.copy(.12f), R13), Alignment.Center) {
    Text(ch, color = Pr, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
}
@Composable fun PrimaryBtn(label: String, onClick: () -> Unit) =
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(R14).background(Grad).clickable(onClick = onClick), Alignment.Center) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
@Composable fun Banner(color: Color, icon: ImageVector, title: String, desc: String) =
    Row(Modifier.fillMaxWidth().background(color.copy(.12f), R14).border(1.dp, color.copy(.4f), R14).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Column { Title(title); if (desc.isNotEmpty()) Sub(desc) }
    }
