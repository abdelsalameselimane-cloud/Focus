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
import androidx.compose.ui.text.style.TextAlign
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
    "limitD" to listOf("الحد الإجمالي لكل التطبيقات المراقبة (حتى 90 دقيقة).", "Combined limit for all monitored apps (up to 90 min).", "Limite cumulée des apps suivies (90 min max)."),
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
    "homeBtn" to listOf("العودة إلى الشاشة الرئيسية", "Go to home screen", "Retour à l’accueil"),
    "stats" to listOf("الإحصائيات", "Stats", "Stats"),
    "scheduleNow" to listOf("جدول حظر نشط", "Block schedule active", "Plage de blocage active"),
    "focusNow" to listOf("جلسة التركيز جارية", "Focus session running", "Session de focus en cours"),
    "streak" to listOf("أيام متتالية دون تجاوز الحد", "Day streak within limits", "Jours d’affilée sans dépassement"),
    "days" to listOf("يوم", "days", "jours"), "left" to listOf("المتبقي", "Left", "Restant"),
    "focus" to listOf("وضع التركيز", "Focus mode", "Mode focus"),
    "focusD" to listOf("يحظر التطبيقات المراقبة طوال المدة، ولا يمكن إلغاؤه.", "Blocks monitored apps for the whole session. It can’t be cancelled.", "Bloque les apps suivies pendant toute la session. Impossible à annuler."),
    "start" to listOf("ابدأ", "Start", "Démarrer"),
    "warnT" to listOf("اقترب الحد", "Limit almost reached", "Limite bientôt atteinte"),
    "warnB" to listOf("بقي نحو 5 دقائق.", "About 5 minutes left.", "Il reste environ 5 minutes."),
    "focusEndT" to listOf("انتهت جلسة التركيز", "Focus session finished", "Session de focus terminée"),
    "focusEndB" to listOf("أحسنت! خذ استراحة.", "Well done! Take a break.", "Bravo ! Faites une pause."),
    "reportT" to listOf("تقريرك الأسبوعي جاهز", "Your weekly report is ready", "Votre rapport hebdo est prêt"),
    "reportB" to listOf("شاركه مع شخص تثق به.", "Share it with someone you trust.", "Partagez-le avec une personne de confiance."),
    "bTitleA" to listOf("وصلت حد هذا التطبيق", "App limit reached", "Limite de l’app atteinte"),
    "bTitleS" to listOf("وقت حظر مجدول", "Scheduled block time", "Plage de blocage"),
    "bTitleF" to listOf("جلسة التركيز", "Focus session", "Session de focus"),
    "bUntil" to listOf("تعود التطبيقات عند", "Apps return at", "Retour des apps à"),
    "bTip" to listOf("جرّب هذا", "Try this", "Essayez ceci"),
    "q1" to listOf("كل دقيقة تستعيدها هي هدية لنفسك.", "Every minute you win back is a gift to yourself.", "Chaque minute reprise est un cadeau à vous-même."),
    "q2" to listOf("تركيز اليوم يصنع إنجاز الغد.", "Today’s focus builds tomorrow’s success.", "La concentration d’aujourd’hui fait la réussite de demain."),
    "q3" to listOf("لا تدع الشاشة تقرر كيف تقضي يومك.", "Don’t let the screen decide how you spend your day.", "Ne laissez pas l’écran décider de votre journée."),
    "a1" to listOf("اشرب كوب ماء وتمدّد دقيقة.", "Drink a glass of water and stretch for a minute.", "Buvez un verre d’eau et étirez-vous une minute."),
    "a2" to listOf("اخرج للمشي 10 دقائق.", "Take a 10-minute walk.", "Faites une marche de 10 minutes."),
    "a3" to listOf("اقرأ بضع صفحات من كتاب.", "Read a few pages of a book.", "Lisez quelques pages d’un livre."),
    "a4" to listOf("اتصل بأحد أفراد عائلتك.", "Call someone in your family.", "Appelez un proche."),
    "a5" to listOf("صلِّ أو تأمّل بضع دقائق.", "Pray or meditate for a few minutes.", "Priez ou méditez quelques minutes."),
    "statsT" to listOf("آخر 7 أيام", "Last 7 days", "7 derniers jours"),
    "total" to listOf("المجموع", "Total", "Total"), "avg" to listOf("المتوسط اليومي", "Daily average", "Moyenne par jour"),
    "topApps" to listOf("الأكثر استخدامًا", "Most used", "Plus utilisées"),
    "share" to listOf("مشاركة التقرير الأسبوعي", "Share weekly report", "Partager le rapport hebdo"),
    "noData" to listOf("لا توجد بيانات بعد.", "No data yet.", "Pas encore de données."),
    "report" to listOf("تقرير Focus Guard الأسبوعي", "Focus Guard weekly report", "Rapport hebdo Focus Guard"),
    "remind" to listOf("تذكير أسبوعي بالمشاركة", "Weekly sharing reminder", "Rappel hebdo de partage"),
    "remindD" to listOf("إشعار أسبوعي لمشاركة تقريرك مع شريك المساءلة.", "A weekly notification to share your report with your accountability partner.", "Une notification hebdo pour partager votre rapport avec votre partenaire."),
    "appLimit" to listOf("حد يومي", "Daily limit", "Limite quotidienne"),
    "noLimit" to listOf("بلا حد", "No limit", "Sans limite"),
    "sched" to listOf("جداول الحظر", "Block schedules", "Plages de blocage"),
    "schedD" to listOf("تُحظر التطبيقات المراقبة في هذه الأوقات.", "Monitored apps are blocked at these times.", "Les apps suivies sont bloquées à ces heures."),
    "from" to listOf("من", "From", "De"), "to" to listOf("إلى", "To", "À"),
    "addSched" to listOf("إضافة جدول", "Add schedule", "Ajouter une plage"),
    "noSched" to listOf("لا توجد جداول.", "No schedules.", "Aucune plage."),
    "everyDay" to listOf("كل يوم", "Every day", "Tous les jours"),
    "pending" to listOf("تغييرات بانتظار التأكيد", "Pending changes", "Modifications en attente"),
    "pendD" to listOf("تخفيف القيود يتطلب انتظار 30 دقيقة.", "Loosening a restriction requires a 30 minute wait.", "Assouplir une restriction demande 30 minutes d’attente."),
    "pendShort" to listOf("بانتظار 30 دقيقة", "Waiting 30 min", "Attente 30 min"),
    "applyNow" to listOf("تطبيق", "Apply", "Appliquer"),
    "wait" to listOf("انتظر", "Wait", "Attendez"), "cancel" to listOf("إلغاء", "Cancel", "Annuler"),
    "off" to listOf("إيقاف", "Off", "Désactivé"),
    "pin" to listOf("قفل برمز PIN", "PIN lock", "Verrou PIN"),
    "pinD" to listOf("يحمي التطبيق من التعديل.", "Protects the app from changes.", "Protège l’app contre les modifications."),
    "pinSet" to listOf("تعيين الرمز", "Set PIN", "Définir le code"),
    "pinRemove" to listOf("إزالة الرمز", "Remove PIN", "Retirer le code"),
    "pinEnter" to listOf("أدخل الرمز", "Enter PIN", "Entrez le code"),
    "pinHint" to listOf("4 أرقام على الأقل", "At least 4 digits", "Au moins 4 chiffres"),
    "pinWrong" to listOf("رمز غير صحيح", "Wrong PIN", "Code incorrect"),
    "unlock" to listOf("فتح", "Unlock", "Déverrouiller"),
    "admin" to listOf("الحماية من إلغاء التثبيت", "Uninstall protection", "Protection contre la désinstallation"),
    "adminD" to listOf("يمنع حذف التطبيق حتى يُلغى تفعيل مسؤول الجهاز.", "Prevents uninstalling until device admin is deactivated.", "Empêche la désinstallation tant que l’administrateur est actif."),
    "adminOn" to listOf("الحماية مفعّلة", "Protection active", "Protection active"),
    "enable" to listOf("تفعيل", "Enable", "Activer"),
    "wLeft" to listOf("متبقٍ اليوم", "left today", "restant")
)
fun tl(lang: String, k: String): String = T[k]?.get(when (lang) { "ar" -> 0; "fr" -> 2; else -> 1 }) ?: k
fun resolveLang(pref: String): String {
    val sys = Locale.getDefault().language
    return if (pref == "auto") (if (sys in listOf("ar", "fr")) sys else "en") else pref
}
@Composable fun t(k: String): String = tl(LL.current, k)

/** Global change counters: reading them in a composable subscribes it to updates. */
object Ver { var n by mutableIntStateOf(0) }
object Sess { var unlocked by mutableStateOf(false) }

// ---- Theme wrapper (dark mode, RTL, language, background) ----
@Composable fun Themed(pref: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme(); val c = if (dark) DarkP else LightP
    val lang = resolveLang(pref)
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

@Composable fun SmallBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).clip(R14).background(Pr.copy(if (enabled) .14f else .05f)).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Text(label, color = if (enabled) Pr else LC.current.mu, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
@Composable fun Stepper(text: String, enabled: Boolean = true, onMinus: () -> Unit, onPlus: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallBtn("−", enabled, onMinus)
        Text(text, color = LC.current.tx, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        SmallBtn("+", enabled, onPlus)
    }
@Composable fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, size: Int = 13, onClick: () -> Unit) =
    Box(modifier.heightIn(min = 40.dp).background(if (on) Pr.copy(.12f) else Color.Transparent, R14)
        .border(1.dp, if (on) Pr else LC.current.bd, R14).clip(R14).clickable(onClick = onClick).padding(horizontal = 6.dp), Alignment.Center) {
        Text(label, fontSize = size.sp, maxLines = 1, color = if (on) Pr else LC.current.tx)
    }
