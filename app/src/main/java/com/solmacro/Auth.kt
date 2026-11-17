package com.solmacro

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// v2.3 sign-in with Discord or Roblox: the normal OAuth 2.0 "authorization code + PKCE" flow in the phone's browser.
// No password is typed in this app, no secret is stored in it, and the access token is thrown away right after the
// profile (name, picture) was read. See SIGNIN_SETUP.md for creating the two apps.
object Auth {
    const val DISCORD = "discord"
    const val ROBLOX = "roblox"
    private val main = Handler(Looper.getMainLooper())

    fun label(p: String) = if (p == ROBLOX) "Roblox" else "Discord"

    fun clientId(c: Context, p: String): String {
        val v = Cfg.gets(c, "auth_${p}_id").trim()
        if (v.isNotEmpty()) return v
        return try { c.getString(if (p == ROBLOX) R.string.roblox_client_id else R.string.discord_client_id).trim() } catch (_: Exception) { "" }
    }

    fun redirect(c: Context, p: String): String {
        val v = Cfg.gets(c, "auth_${p}_redirect").trim()
        return if (v.isEmpty()) "luminfish://auth" else v
    }

    fun isDone(c: Context): Boolean = Cfg.gets(c, "auth_state").let { it == "ok" || it == "guest" }
    fun signedIn(c: Context): Boolean = Cfg.gets(c, "auth_state") == "ok"
    fun name(c: Context): String = Cfg.gets(c, "auth_name").ifEmpty { "Guest" }
    fun provider(c: Context): String = Cfg.gets(c, "auth_provider")
    fun avatarFile(c: Context) = File(c.filesDir, "avatar.png")

    // ---- accounts kept on this phone (sign up creates one, log in uses one) ----
    class Account(val p: String, val id: String, val name: String)

    private class NoAccount(val provider: String) : Exception()

    fun accounts(c: Context): List<Account> {
        val out = ArrayList<Account>()
        try {
            val a = JSONArray(Cfg.gets(c, "auth_accounts").ifEmpty { "[]" })
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Account(o.getString("p"), o.getString("id"), o.optString("name")))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun saveAccounts(c: Context, list: List<Account>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("p", x.p).put("id", x.id).put("name", x.name))
        Cfg.puts(c, "auth_accounts", a.toString())
    }

    fun avatarOf(c: Context, acc: Account) = File(c.filesDir, "av_" + acc.p + "_" + acc.id.filter { it.isLetterOrDigit() } + ".png")

    private fun addAccount(c: Context, acc: Account) {
        val list = accounts(c).filterNot { it.p == acc.p && it.id == acc.id }.toMutableList()
        list.add(0, acc)
        saveAccounts(c, list)
    }

    // makes a saved account the current one (no browser needed)
    fun setCurrent(c: Context, acc: Account) {
        Cfg.puts(c, "auth_state", "ok")
        Cfg.puts(c, "auth_provider", acc.p)
        Cfg.puts(c, "auth_name", acc.name)
        Cfg.puts(c, "auth_id", acc.id)
        val src = avatarOf(c, acc)
        try { if (src.exists()) src.copyTo(avatarFile(c), true) else avatarFile(c).delete() } catch (_: Exception) {}
        addAccount(c, acc)
    }

    fun removeAccount(c: Context, acc: Account) {
        saveAccounts(c, accounts(c).filterNot { it.p == acc.p && it.id == acc.id })
        avatarOf(c, acc).delete()
        if (Cfg.gets(c, "auth_id") == acc.id && provider(c) == acc.p) signOut(c)
    }

    fun current(c: Context): Account? = if (signedIn(c)) Account(provider(c), Cfg.gets(c, "auth_id"), name(c)) else null

    fun guest(c: Context) {
        Cfg.puts(c, "auth_state", "guest")
        Cfg.puts(c, "auth_provider", "")
        Cfg.puts(c, "auth_name", "Guest")
        avatarFile(c).delete()
    }

    fun signOut(c: Context) {
        // the account itself stays saved on this phone, so Log in can use it again
        for (k in listOf("auth_state", "auth_provider", "auth_name", "auth_id", "auth_pend_v", "auth_pend_s", "auth_pend_p", "auth_pend_m")) Cfg.puts(c, k, "")
        avatarFile(c).delete()
    }

    private fun rnd(n: Int): String {
        val b = ByteArray(n)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // Opens the provider's sign-in page in the browser. Returns an error text, or null when the browser was opened.
    fun begin(a: Activity, p: String, mode: String = "signup"): String? {
        val id = clientId(a, p)
        if (id.isEmpty()) return "${label(p)} sign-in is not set up yet. Open \"Sign-in setup\" below and paste the client ID."
        val verifier = rnd(48)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val state = p + "." + rnd(12)
        Cfg.puts(a, "auth_pend_v", verifier)
        Cfg.puts(a, "auth_pend_s", state)
        Cfg.puts(a, "auth_pend_p", p)
        Cfg.puts(a, "auth_pend_m", mode)
        val base = if (p == ROBLOX) "https://apis.roblox.com/oauth/v1/authorize" else "https://discord.com/oauth2/authorize"
        val scope = if (p == ROBLOX) "openid profile" else "identify"
        val url = base + "?client_id=" + enc(id) + "&response_type=code&redirect_uri=" + enc(redirect(a, p)) +
            "&scope=" + enc(scope) + "&state=" + enc(state) + "&code_challenge=" + enc(challenge) + "&code_challenge_method=S256"
        return try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            null
        } catch (e: ActivityNotFoundException) {
            "No browser found on this phone."
        }
    }

    private fun http(url: String, form: String?, bearer: String?): Pair<Int, String> {
        val cn = URL(url).openConnection() as HttpURLConnection
        try {
            cn.connectTimeout = 12000
            cn.readTimeout = 15000
            cn.setRequestProperty("User-Agent", "LuminFish/2.3")
            cn.setRequestProperty("Accept", "application/json")
            if (bearer != null) cn.setRequestProperty("Authorization", "Bearer $bearer")
            if (form != null) {
                cn.requestMethod = "POST"
                cn.doOutput = true
                cn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                cn.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = cn.responseCode
            val stream = if (code in 200..299) cn.inputStream else cn.errorStream
            return Pair(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            cn.disconnect()
        }
    }

    // Called with the address the browser came back with (luminfish://auth?code=...&state=...).
    fun finish(c: Context, uri: Uri, cb: (Boolean, String) -> Unit) {
        val err = uri.getQueryParameter("error")
        if (err != null) { cb(false, if (err == "access_denied") "Sign-in was cancelled." else "Sign-in failed: $err"); return }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val p = Cfg.gets(c, "auth_pend_p")
        val verifier = Cfg.gets(c, "auth_pend_v")
        if (code == null || state == null || p.isEmpty() || verifier.isEmpty() || state != Cfg.gets(c, "auth_pend_s")) {
            cb(false, "This sign-in link is not valid any more. Please try again.")
            return
        }
        Cfg.puts(c, "auth_pend_v", "")
        Cfg.puts(c, "auth_pend_s", "")
        Thread {
            var ok = false
            var msg = ""
            try {
                val tokenUrl = if (p == ROBLOX) "https://apis.roblox.com/oauth/v1/token" else "https://discord.com/api/oauth2/token"
                val form = "client_id=" + enc(clientId(c, p)) + "&grant_type=authorization_code&code=" + enc(code) +
                    "&redirect_uri=" + enc(redirect(c, p)) + "&code_verifier=" + enc(verifier)
                val (tc, tb) = http(tokenUrl, form, null)
                if (tc !in 200..299) throw Exception("token HTTP $tc " + tb.take(120))
                val token = JSONObject(tb).getString("access_token")
                val infoUrl = if (p == ROBLOX) "https://apis.roblox.com/oauth/v1/userinfo" else "https://discord.com/api/users/@me"
                val (ic, ib) = http(infoUrl, null, token)
                if (ic !in 200..299) throw Exception("profile HTTP $ic " + ib.take(120))
                val j = JSONObject(ib)
                var name: String
                var id: String
                var pic: String? = null
                if (p == ROBLOX) {
                    id = j.optString("sub")
                    name = j.optString("nickname").ifEmpty { j.optString("name").ifEmpty { j.optString("preferred_username") } }
                    pic = j.optString("picture").ifEmpty { null }
                } else {
                    id = j.optString("id")
                    name = j.optString("global_name").let { if (it.isEmpty() || it == "null") j.optString("username") else it }
                    val h = j.optString("avatar")
                    if (h.isNotEmpty() && h != "null") pic = "https://cdn.discordapp.com/avatars/$id/$h.png?size=128"
                }
                if (name.isEmpty()) name = label(p) + " user"
                val mode = Cfg.gets(c, "auth_pend_m")
                val known = accounts(c).any { it.p == p && it.id == id }
                if (mode == "login" && !known) throw NoAccount(p)
                val acc = Account(p, id, name)
                avatarOf(c, acc).delete()
                if (pic != null) {
                    try {
                        val cn = URL(pic).openConnection() as HttpURLConnection
                        cn.connectTimeout = 8000
                        cn.readTimeout = 8000
                        cn.inputStream.use { s -> BitmapFactory.decodeStream(s) }?.let { b ->
                            avatarOf(c, acc).outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 95, o) }
                        }
                    } catch (_: Exception) {}
                }
                setCurrent(c, acc)
                ok = true
                msg = (if (known) "back:" else "new:") + name
            } catch (e: NoAccount) {
                msg = "noaccount:" + label(e.provider)
            } catch (e: Exception) {
                msg = "Could not finish sign-in (" + (e.message ?: "error") + ")"
            }
            val m = msg
            val o = ok
            main.post { cb(o, m) }
        }.start()
    }

    // round picture with the first letter, used when there is no avatar
    fun initialBitmap(c: Context, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = when (provider(c)) { DISCORD -> 0xFF5865F2.toInt(); ROBLOX -> 0xFF3A3F4B.toInt(); else -> 0xFF2A3A82.toInt() }
        cv.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = sizePx * 0.46f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        val ch = name(c).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        cv.drawText(ch, sizePx / 2f, sizePx / 2f - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }
}
