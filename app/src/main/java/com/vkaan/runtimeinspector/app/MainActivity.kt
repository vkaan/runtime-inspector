package com.vkaan.runtimeinspector.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.vkaan.runtimeinspector.RuntimeInspector
import com.vkaan.runtimeinspector.rules.Risk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The inspector's own screen: what the rules have found, and the detail of whichever finding a
 * notification was tapped on. Also the reason this Activity exists at all — an app that has never
 * been launched is in Android's stopped state, where it gets no BOOT_COMPLETED and cannot be bound.
 */
class MainActivity : ComponentActivity() {

    // Token's palette, all from uicomponents_v2.aar. Brand is the opulent-blue ramp.
    private companion object {
        const val BG = 0xFFEFF1F5.toInt() // gray_100
        const val SURFACE = 0xFFFFFFFF.toInt() // white
        const val BRAND = 0xFF114CEE.toInt() // core_opulent_blue_500
        const val BRAND_STRONG = 0xFF114CEE.toInt() // core_opulent_blue_500
        const val TEXT = 0xFF202328.toInt() // gray_1500
        const val MUTED = 0xFF667080.toInt() // gray_1000
        const val HAIRLINE = 0xFFC0C4D3.toInt() // gray_400
        const val ERROR = 0xFFC83524.toInt() // red_1000
        const val WARNING = 0xFFBF7A2C.toInt() // orange_800
    }

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    // Token's UI font (their design system's tokenui_font_family_body). The code only ever asks
    // for normal or bold, so two weights cover it.
    private val interRegular by lazy { resources.getFont(R.font.inter_regular) }
    private val interBold by lazy { resources.getFont(R.font.inter_bold) }

    private lateinit var column: LinearLayout

    // All screen state and the actions on it live here, so it survives rotation.
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InspectorService.start(this)
        readIntent(intent)

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BG)
                isFillViewport = true
                addView(column, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        )
    }

    // The notification uses SINGLE_TOP, so a tap on a running app lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // Help view is a second screen inside this Activity, so back has to unwind it first.
    override fun onBackPressed() {
        if (viewModel.back()) {
            render()
            return
        }
        super.onBackPressed()
    }

    private fun readIntent(intent: Intent) {
        viewModel.openFromNotification(
            intent.getStringExtra(RuntimeInspector.EXTRA_RULE_ID),
            intent.getStringExtra(RuntimeInspector.EXTRA_SUBJECT),
        )
    }

    private fun render() {
        val s = viewModel.state()
        s.helpFor?.let { renderHelp(it); return }

        column.removeAllViews()
        column.addView(header(s.rest + listOfNotNull(s.opened)))
        column.addView(actions(s.confirmingClear))

        if (s.openRuleId != null && s.opened == null) {
            column.addView(note("${s.openRuleId} artık bellekte değil — servis yeniden başlamış olabilir."))
        }
        s.opened?.let {
            column.addView(sectionTitle("Bildirimden gelen"))
            column.addView(findingCard(it, s.expanded))
        }

        column.addView(sectionTitle(if (s.opened == null) "Bulgular" else "Diğer bulgular"))
        if (s.rest.isEmpty()) {
            column.addView(note("Henüz bulgu yok. İncele'ye basınca son log penceresi kurallardan geçer."))
        } else {
            s.rest.forEach { column.addView(findingCard(it, s.expanded)) }
        }
    }

    /** Why the finding happened and what would have avoided it. */
    private fun renderHelp(risk: Risk) {
        val help = RULE_HELP[risk.ruleId]
        val accent = accentOf(risk)

        column.removeAllViews()
        column.addView(
            outlinedButton("← Geri") { viewModel.closeHelp(); render() },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                .apply { bottomMargin = dp(18) },
        )
        column.addView(pill(risk.severity.name, accent))
        column.addView(
            label(risk.ruleId, 22f, BRAND_STRONG, Typeface.BOLD)
                .apply { setPadding(0, dp(10), 0, 0) }
        )
        risk.label()?.let {
            addSpaced(label(it, 13f, BRAND), dp(2))
        }
        addSpaced(label(risk.message, 14f, TEXT).apply { setLineSpacing(dp(3).toFloat(), 1f) }, dp(10))

        if (help == null) {
            column.addView(sectionTitle("Açıklama"))
            column.addView(note("Bu kural için henüz yazılmış bir açıklama yok."))
            return
        }
        column.addView(sectionTitle("Neden oluyor"))
        column.addView(paragraph(help.cause))
        column.addView(sectionTitle("Nasıl düzeltilir"))
        column.addView(paragraph(help.fix))
    }

    private fun header(risks: List<Risk>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Runtime Inspector", 24f, BRAND_STRONG, Typeface.BOLD))
        addView(divider())
        val errors = risks.count { it.severity == Risk.Severity.ERROR }
        addView(
            label(
                "Servis çalışıyor · ${risks.size} bulgu, $errors hata · " +
                    "detaylar logcat'te RuntimeInspector etiketinde",
                13f,
                MUTED,
            ).apply { setPadding(0, dp(6), 0, dp(20)) }
        )
    }

    private fun actions(confirmingClear: Boolean): View =
        if (confirmingClear) confirmClearActions() else defaultActions()

    private fun defaultActions(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(8))
        addView(
            // Elle basıldı: buffer büyümemiş olsa da eldeki pencereyi kurallardan geçir.
            filledButton("İncele") { viewModel.incele() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(10) },
        )
        addView(
            outlinedButton("Yenile") { render() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(10) },
        )
        addView(
            outlinedButton("Temizle") { viewModel.startClear(); render() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
    }

    private fun confirmClearActions(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(SURFACE)
            setStroke(dp(1), HAIRLINE)
        }
        addView(
            label("Bulguları temizlemek istediğinize emin misiniz?", 15f, TEXT, Typeface.BOLD),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) },
        )
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    secondaryButton("Vazgeç") { viewModel.cancelClear(); render() },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(10) },
                )
                addView(
                    primaryButton("Evet, temizle") { viewModel.confirmClear(); render() },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
            },
        )
    }

    // Confirm dialog's primary action — same look as filledButton, no ripple.
    private fun primaryButton(text: String, onClick: () -> Unit): Button = button(text, onClick).apply {
        setTextColor(SURFACE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(BRAND)
        }
    }

    // Token's dialog_box_info_330 secondary button, token theme: gray_200 surface, gray_1000 (MUTED) text.
    private fun secondaryButton(text: String, onClick: () -> Unit): Button = button(text, onClick).apply {
        setTextColor(MUTED)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(0xFFE3E5ED.toInt()) // gray_200
        }
    }

    /**
     * One finding. Tapping it expands the counts and times, and offers the explanation. Cards
     * arrived at from a notification start expanded.
     */
    private fun findingCard(risk: Risk, expanded: Set<String>): View {
        val cardKey = riskKey(risk.ruleId, risk.subject)
        val open = cardKey in expanded
        val accent = accentOf(risk)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(pill(risk.severity.name, accent))
                    if (risk.occurrences > 1) addView(pill("×${risk.occurrences}", MUTED))
                }
            )
            addView(
                label(risk.ruleId, 16f, BRAND_STRONG, Typeface.BOLD)
                    .apply { setPadding(0, dp(10), 0, 0) }
            )
            risk.label()?.let {
                addView(label(it, 13f, BRAND).apply { setPadding(0, dp(2), 0, 0) })
            }
            addView(
                label(risk.message, 14f, TEXT).apply {
                    setPadding(0, dp(8), 0, 0)
                    setLineSpacing(dp(3).toFloat(), 1f)
                }
            )
            if (open) {
                addView(divider())
                addView(detailRow("İlk görülme", "${clock.format(Date(risk.timestampMillis))}  ·  seq ${risk.seq}"))
                addView(detailRow("Son görülme", "${clock.format(Date(risk.lastTimestampMillis))}  ·  seq ${risk.lastSeq}"))
                addView(detailRow("Tekrar", "${risk.occurrences}"))
                risk.instanceId?.let { addView(detailRow("Instance", "#$it")) }
                addView(
                    outlinedButton("Daha fazla bilgi") { viewModel.openHelp(risk); render() },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        .apply { topMargin = dp(14) },
                )
            } else {
                addView(label("Detay için dokun", 11f, MUTED).apply { setPadding(0, dp(10), 0, 0) })
            }
        }

        // A colour stripe instead of a coloured card: the severity reads at a glance without the
        // card shouting.
        val stripe = View(this).apply { setBackgroundColor(accent) }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(SURFACE)
                setStroke(dp(1), if (open) accent else HAIRLINE)
            }
            clipToOutline = true
            addView(stripe, LinearLayout.LayoutParams(dp(4), MATCH_PARENT))
            addView(body, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { bottomMargin = dp(10) }
            setOnClickListener {
                viewModel.toggle(cardKey)
                render()
            }
        }
    }

    private fun accentOf(risk: Risk): Int = when (risk.severity) {
        Risk.Severity.ERROR -> ERROR
        Risk.Severity.WARNING -> WARNING
        Risk.Severity.INFO -> BRAND
    }

    private fun paragraph(text: String): View = label(text, 14f, TEXT).apply {
        setLineSpacing(dp(4).toFloat(), 1f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(SURFACE)
            setStroke(dp(1), HAIRLINE)
        }
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun addSpaced(view: View, topMargin: Int) {
        column.addView(
            view,
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { this.topMargin = topMargin },
        )
    }

    private fun detailRow(name: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
        addView(label(name, 12f, MUTED), LinearLayout.LayoutParams(dp(104), WRAP_CONTENT))
        addView(label(value, 12f, TEXT))
    }

    private fun sectionTitle(text: String): View =
        label(text.uppercase(Locale.US), 11f, MUTED, Typeface.BOLD).apply {
            letterSpacing = 0.12f
            setPadding(dp(2), dp(18), 0, dp(10))
        }

    private fun note(text: String): View = label(text, 13f, MUTED).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(SURFACE)
            setStroke(dp(1), HAIRLINE)
        }
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun pill(text: String, color: Int): View =
        label(text, 10f, color, Typeface.BOLD).apply {
            letterSpacing = 0.08f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), color)
            }
            setPadding(dp(8), dp(3), dp(8), dp(3))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                .apply { rightMargin = dp(6) }
        }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(HAIRLINE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
            .apply { topMargin = dp(14); bottomMargin = dp(10) }
    }

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int,
        style: Int = Typeface.NORMAL,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = if (style == Typeface.BOLD) interBold else interRegular
    }

    private fun filledButton(text: String, onClick: () -> Unit): Button = button(text, onClick).apply {
        setTextColor(SURFACE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(BRAND)
        }
    }

    private fun outlinedButton(text: String, onClick: () -> Unit): Button = button(text, onClick).apply {
        setTextColor(BRAND)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(SURFACE)
            setStroke(dp(1), HAIRLINE)
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = interBold
        setPadding(dp(16), dp(12), dp(16), dp(12))
        minHeight = dp(48)
        stateListAnimator = null
        elevation = 0f
        // Posted: fired straight from the click, İncele's heavy onClick work (a TSystem bind + file
        // IO on the main thread) steals the bounce-back animation's first frames — Yenile's cheap
        // render() doesn't, so the two looked like they ran at different speeds. A post() lets the
        // animation get a frame in before the click work starts.
        setOnClickListener { v -> v.post { onClick() } }
        // A scale bounce instead of a color ripple — visible the same way on every button,
        // whatever its background, instead of chasing contrast per button.
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
