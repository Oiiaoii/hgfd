package com.solmacro

import android.animation.Animator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

private const val FILL = ViewGroup.LayoutParams.MATCH_PARENT
private const val HUG = ViewGroup.LayoutParams.WRAP_CONTENT

// slow drifting colour glow behind the sign-in screen
class AuroraView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var anim: ValueAnimator? = null
    private var g1: RadialGradient? = null
    private var g2: RadialGradient? = null
    private var g3: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = maxOf(w, h) * 0.55f
        g1 = RadialGradient(0f, 0f, r, 0x555865F2, 0x005865F2, Shader.TileMode.CLAMP)
        g2 = RadialGradient(0f, 0f, r * 0.9f, 0x4438A0FF, 0x0038A0FF, Shader.TileMode.CLAMP)
        g3 = RadialGradient(0f, 0f, r * 0.8f, 0x33A06BFF, 0x00A06BFF, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Fx.on(context)) {
            anim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 26000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { t = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(0xFF03050E.toInt())
        fun blob(g: RadialGradient?, x: Float, y: Float) {
            if (g == null) return
            c.save()
            c.translate(x, y)
            paint.shader = g
            c.drawCircle(0f, 0f, maxOf(w, h), paint)
            c.restore()
        }
        blob(g1, w * (0.2f + 0.12f * Math.sin(t.toDouble()).toFloat()), h * (0.12f + 0.06f * Math.cos(t.toDouble()).toFloat()))
        blob(g2, w * (0.9f + 0.1f * Math.cos(t * 1.0 + 1.0).toFloat()), h * (0.45f + 0.08f * Math.sin(t * 1.0 + 2.0).toFloat()))
        blob(g3, w * (0.3f + 0.1f * Math.sin(t * 1.0 + 4.0).toFloat()), h * (0.95f + 0.04f * Math.cos(t * 1.0 + 3.0).toFloat()))
    }
}

// v2.3 sign-in screen: Sign up or Log in, with Discord or Roblox (or a guest). Opened before the main screen until a choice is made.
class LoginActivity : Activity() {
    private val cBg = 0xFF03050E.toInt()
    private val cCard = 0xE60A1028.toInt()
    private val cBd = 0xFF22306B.toInt()
    private val cTx = 0xFFE6ECFF.toInt()
    private val cMut = 0xFF8E9BD0.toInt()
    private val cBad = 0xFFFF6B6B.toInt()
    private val cOk = 0xFF38D39F.toInt()
    private val cAcc = 0xFF8FB4FF.toInt()

    private lateinit var status: TextView
    private lateinit var busy: ProgressBar
    private lateinit var btnD: LinearLayout
    private lateinit var btnR: LinearLayout
    private lateinit var lblD: TextView
    private lateinit var lblR: TextView
    private lateinit var titleTv: TextView
    private lateinit var subTv: TextView
    private lateinit var card: LinearLayout
    private lateinit var savedBox: LinearLayout
    private lateinit var savedList: LinearLayout
    private lateinit var tabSignup: TextView
    private lateinit var tabLogin: TextView
    private lateinit var indicator: View
    private lateinit var tabBar: FrameLayout
    private lateinit var setup: LinearLayout
    private lateinit var idD: EditText
    private lateinit var idR: EditText
    private lateinit var reD: EditText
    private lateinit var reR: EditText
    private var mode = "signup"
    private val loops = ArrayList<Animator>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun attachBaseContext(base: Context) {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        if (cfg.fontScale > 1.0f) cfg.fontScale = 1.0f
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    private fun shape(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(stroke), strokeColor)
    }

    private fun tv(t: String, size: Float, color: Int = cTx, bold: Boolean = false, center: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }

    private fun tile(letter: String, bg: Int): TextView = tv(letter, 16f, Color.WHITE, true, true).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
    }

    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    // big provider button; the label view is returned too so the text can change between Sign up and Log in
    private fun provider(name: String, letter: String, tileColor: Int, fill: Int, stroke: Int, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val lbl = tv("", 16f, Color.WHITE, true)
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(18), 0)
            background = shape(fill, 14, 1, stroke)
            isClickable = true
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
        pressFx(b)
        b.addView(tile(letter, tileColor), LinearLayout.LayoutParams(dp(34), dp(34)))
        b.addView(lbl, LinearLayout.LayoutParams(0, HUG, 1f).apply { leftMargin = dp(14) })
        b.addView(tv("›", 22f, 0xB0FFFFFF.toInt(), false))
        return Pair(b, lbl)
    }

    private fun field(hint: String, value: String, uri: Boolean = false) = EditText(this).apply {
        setHint(hint)
        setText(value)
        setHintTextColor(0xFF5E6A9C.toInt())
        setTextColor(cTx)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setSingleLine(true)
        inputType = if (uri) InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = shape(0xFF070C20.toInt(), 10, 1, cBd)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        mode = if (Auth.accounts(this).isNotEmpty()) "login" else "signup"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(44), dp(22), dp(28))
        }

        // logo with a glow ring that keeps spreading
        val logoBox = FrameLayout(this)
        val ring = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(2), cAcc); setColor(Color.TRANSPARENT) } }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.brry_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF000000.toInt()); setStroke(dp(2), cAcc) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            elevation = dp(6).toFloat()
        }
        logoBox.addView(ring, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        logoBox.addView(logo, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        root.addView(logoBox, LinearLayout.LayoutParams(dp(150), dp(120)))
        titleTv = tv("", 28f, cTx, true, true)
        subTv = tv("", 14f, cMut, false, true)
        root.addView(titleTv, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(6) })
        root.addView(subTv, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(4) })

        // Sign up | Log in
        tabBar = FrameLayout(this).apply { background = shape(0xCC0A1028.toInt(), 14, 1, cBd) }
        indicator = View(this).apply { background = shape(0xFF2A3A82.toInt(), 11) }
        tabBar.addView(indicator, FrameLayout.LayoutParams(0, dp(38), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dp(4) })
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabSignup = tv("Sign up", 14f, cTx, true, true).apply { setOnClickListener { switchTab("signup") } }
        tabLogin = tv("Log in", 14f, cMut, true, true).apply { setOnClickListener { switchTab("login") } }
        tabRow.addView(tabSignup, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabRow.addView(tabLogin, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabBar.addView(tabRow, FrameLayout.LayoutParams(FILL, dp(46)))
        root.addView(tabBar, LinearLayout.LayoutParams(FILL, dp(46)).apply { topMargin = dp(22) })

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(cCard, 20, 1, cBd)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING); setDuration(260) }
        }
        savedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        savedBox.addView(tv("Your accounts on this phone", 12f, cMut, true))
        savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        savedBox.addView(savedList, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(6) })
        savedBox.addView(tv("or use the browser", 11.5f, 0xFF5E6A9C.toInt(), false, true), LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(8); bottomMargin = dp(8) })
        card.addView(savedBox, LinearLayout.LayoutParams(FILL, HUG))
        val pd = provider("Discord", "D", 0xFF5865F2.toInt(), 0xFF5865F2.toInt(), 0xFF7A85F5.toInt()) { start(Auth.DISCORD) }
        val pr = provider("Roblox", "R", 0xFF2B2F38.toInt(), 0xFF1A1D25.toInt(), 0xFF4A505E.toInt()) { start(Auth.ROBLOX) }
        btnD = pd.first; lblD = pd.second; btnR = pr.first; lblR = pr.second
        card.addView(btnD, LinearLayout.LayoutParams(FILL, dp(56)))
        card.addView(btnR, LinearLayout.LayoutParams(FILL, dp(56)).apply { topMargin = dp(12) })
        busy = ProgressBar(this).apply { isIndeterminate = true; visibility = View.GONE }
        card.addView(busy, LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL })
        status = tv("", 12.5f, cMut, false, true).apply { visibility = View.GONE }
        card.addView(status, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(12) })
        root.addView(card, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(16) })

        val guest = tv("Continue as guest", 14f, cAcc, true, true).apply {
            setPadding(dp(12), dp(14), dp(12), dp(14))
            isClickable = true
            setOnClickListener { Auth.guest(this@LoginActivity); goMain() }
        }
        pressFx(guest)
        root.addView(guest, LinearLayout.LayoutParams(HUG, HUG).apply { topMargin = dp(8) })

        val toggle = tv("Sign-in setup", 12f, cMut, false, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            setOnClickListener { setup.visibility = if (setup.visibility == View.VISIBLE) View.GONE else View.VISIBLE; if (setup.visibility == View.VISIBLE) Fx.rise(setup, 0, 14, 300) }
        }
        root.addView(toggle, LinearLayout.LayoutParams(HUG, HUG))
        setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = shape(cCard, 16, 1, cBd)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        setup.addView(tv("Paste the public client IDs of your Discord and Roblox apps (see SIGNIN_SETUP.md). Redirect URLs can stay empty.", 11.5f, cMut))
        idD = field("Discord client ID", Cfg.gets(this, "auth_discord_id"))
        idR = field("Roblox client ID", Cfg.gets(this, "auth_roblox_id"))
        reD = field("Discord redirect URL (default luminfish://auth)", Cfg.gets(this, "auth_discord_redirect"), true)
        reR = field("Roblox redirect URL (default luminfish://auth)", Cfg.gets(this, "auth_roblox_redirect"), true)
        for (f in listOf(idD, reD, idR, reR)) setup.addView(f, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(8) })
        val save = tv("Save", 14f, Color.WHITE, true, true).apply {
            background = shape(0xFF2A3A82.toInt(), 12)
            setPadding(0, dp(11), 0, dp(11))
            isClickable = true
            setOnClickListener {
                Cfg.puts(this@LoginActivity, "auth_discord_id", idD.text.toString().trim())
                Cfg.puts(this@LoginActivity, "auth_roblox_id", idR.text.toString().trim())
                Cfg.puts(this@LoginActivity, "auth_discord_redirect", reD.text.toString().trim())
                Cfg.puts(this@LoginActivity, "auth_roblox_redirect", reR.text.toString().trim())
                show("Saved.", cOk)
            }
        }
        pressFx(save)
        setup.addView(save, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(12) })
        root.addView(setup, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(6) })

        val foot = tv("LuminFish only reads your public profile name and picture. No password is typed in this app and nothing is stored on a server. Accounts are kept on this phone only. Not affiliated with Discord or Roblox.", 11f, 0xFF5E6A9C.toInt(), false, true)
        root.addView(foot, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(18) })

        val frame = FrameLayout(this)
        frame.addView(AuroraView(this), FrameLayout.LayoutParams(FILL, FILL))
        frame.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(root) }, FrameLayout.LayoutParams(FILL, FILL))
        setContentView(frame)

        // start state without animation, then the entrance
        tabBar.post {
            val half = (tabBar.width - dp(8)) / 2
            indicator.layoutParams = (indicator.layoutParams as FrameLayout.LayoutParams).apply { width = half }
            applyMode(false)
            indicator.translationX = if (mode == "login") half.toFloat() else 0f
            entrance(logoBox, ring, logo, foot, guest, toggle)
        }
        handle(intent)
    }

    private fun entrance(logoBox: View, ring: View, logo: View, foot: View, guest: View, toggle: View) {
        Fx.pop(logo, 0)
        Fx.ripple(ring)?.let { loops.add(it) }
        Fx.float(logo, 3, 2800)?.let { loops.add(it) }
        Fx.rise(titleTv, 120)
        Fx.rise(subTv, 190)
        Fx.rise(tabBar, 260)
        Fx.rise(card, 330)
        Fx.rise(btnD, 430, 22, 420)
        Fx.rise(btnR, 510, 22, 420)
        Fx.rise(guest, 600, 14, 380)
        Fx.rise(toggle, 660, 10, 320)
        Fx.rise(foot, 720, 10, 380)
    }

    override fun onDestroy() {
        for (a in loops) a.cancel()
        loops.clear()
        super.onDestroy()
    }

    // ---- Sign up / Log in ----
    private fun switchTab(m: String) {
        if (m == mode) return
        mode = m
        applyMode(true)
    }

    private fun applyMode(animate: Boolean) {
        val half = (tabBar.width - dp(8)) / 2
        val x = if (mode == "login") half.toFloat() else 0f
        if (animate && Fx.on(this)) indicator.animate().translationX(x).setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start() else indicator.translationX = x
        tabSignup.setTextColor(if (mode == "signup") cTx else cMut)
        tabLogin.setTextColor(if (mode == "login") cTx else cMut)
        val t = if (mode == "login") "Welcome back" else "Create your account"
        val s = if (mode == "login") "Log in to the account you already made" else "Sign up with Discord or Roblox, it takes a few seconds"
        val verb = if (mode == "login") "Log in" else "Sign up"
        if (animate) {
            Fx.swapText(titleTv, t); Fx.swapText(subTv, s); Fx.swapText(lblD, "$verb with Discord"); Fx.swapText(lblR, "$verb with Roblox")
        } else {
            titleTv.text = t; subTv.text = s; lblD.text = "$verb with Discord"; lblR.text = "$verb with Roblox"
        }
        rebuildSaved(animate)
        status.visibility = View.GONE
    }

    private fun rebuildSaved(animate: Boolean) {
        savedList.removeAllViews()
        val list = Auth.accounts(this)
        if (mode != "login" || list.isEmpty()) { savedBox.visibility = View.GONE; return }
        var i = 0
        for (acc in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shape(0xFF0E1638.toInt(), 14, 1, cBd)
                setPadding(dp(10), dp(8), dp(6), dp(8))
                isClickable = true
                setOnClickListener { loginSaved(acc, this) }
            }
            pressFx(row)
            val f = Auth.avatarOf(this, acc)
            val av = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF000000.toInt()) }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                val bm = if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                setImageBitmap(bm ?: initial(acc))
            }
            row.addView(av, LinearLayout.LayoutParams(dp(40), dp(40)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv(acc.name.ifEmpty { Auth.label(acc.p) + " user" }, 15f, cTx, true))
            col.addView(tv(Auth.label(acc.p), 11.5f, cMut))
            row.addView(col, LinearLayout.LayoutParams(0, HUG, 1f).apply { leftMargin = dp(12) })
            row.addView(tv("Log in ›", 13f, cAcc, true))
            val x = tv("✕", 15f, 0xFF7E89BD.toInt(), false, true).apply {
                setPadding(dp(12), dp(8), dp(10), dp(8))
                isClickable = true
                setOnClickListener { confirmRemove(acc) }
            }
            row.addView(x)
            savedList.addView(row, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = if (i == 0) 0 else dp(8) })
            if (animate) Fx.rise(row, 60L * i, 12, 300)
            i++
        }
        savedBox.visibility = View.VISIBLE
    }

    private fun initial(acc: Auth.Account): android.graphics.Bitmap {
        val sz = dp(80)
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = if (acc.p == Auth.DISCORD) 0xFF5865F2.toInt() else 0xFF3A3F4B.toInt()
        cv.drawCircle(sz / 2f, sz / 2f, sz / 2f, p)
        p.color = Color.WHITE
        p.textSize = sz * 0.46f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        cv.drawText((acc.name.firstOrNull() ?: '?').uppercaseChar().toString(), sz / 2f, sz / 2f - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }

    private fun confirmRemove(acc: Auth.Account) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Remove account?")
            .setMessage(acc.name + " (" + Auth.label(acc.p) + ") will be removed from this phone. You can sign up again any time.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> Auth.removeAccount(this, acc); rebuildSaved(true); if (Auth.accounts(this).isEmpty()) { mode = "signup"; applyMode(true) } }
            .show()
    }

    private fun loginSaved(acc: Auth.Account, row: View) {
        Auth.setCurrent(this, acc)
        welcome("Welcome back, " + acc.name.ifEmpty { Auth.label(acc.p) + " user" })
    }

    // ---- feedback ----
    private fun show(t: String, color: Int) {
        status.setTextColor(color)
        if (status.visibility != View.VISIBLE) { status.text = t; status.visibility = View.VISIBLE; Fx.rise(status, 0, 8, 240) } else Fx.swapText(status, t)
    }

    private fun fail(t: String) {
        show(t, cBad)
        Fx.shake(card)
    }

    private fun working(on: Boolean) {
        busy.visibility = if (on) View.VISIBLE else View.GONE
        for (v in listOf(btnD, btnR)) {
            v.isEnabled = !on
            v.animate().alpha(if (on) 0.5f else 1f).setDuration(200).start()
        }
    }

    private fun welcome(msg: String) {
        show("✓  $msg", cOk)
        val c = card
        c.postDelayed({ Fx.out(c, 320) { goMain() } }, if (Fx.on(this)) 650L else 0L)
    }

    private fun start(p: String) {
        val err = Auth.begin(this, p, mode)
        if (err != null) {
            fail(err)
            if (err.contains("not set up")) { setup.visibility = View.VISIBLE; Fx.rise(setup, 0, 14, 300) }
        } else {
            show("Finish " + (if (mode == "login") "logging in" else "signing up") + " with " + Auth.label(p) + " in the browser, then you come back here.", cMut)
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        handle(i)
    }

    private fun handle(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "luminfish") return
        i.data = null
        working(true)
        show("One moment…", cMut)
        Auth.finish(this, d) { ok, msg ->
            working(false)
            when {
                ok -> welcome(if (msg.startsWith("back:")) "Welcome back, " + msg.substring(5) else "Account created. Welcome, " + msg.substring(4))
                msg.startsWith("noaccount:") -> {
                    fail("No LuminFish account for this " + msg.substring(10) + " yet. Create one with Sign up.")
                    card.postDelayed({ switchTab("signup") }, 900L)
                }
                else -> fail(msg)
            }
        }
    }
}
