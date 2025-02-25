package buj.tl

import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import mindustry.gen.Groups
import mindustry.gen.Player
import java.io.IOException

private fun minIdx(vararg vals: Int): Int {
    var min = -1
    for (v in vals) if (v != -1 && v < min || min == -1) min = v
    return min
}

private fun destructLocale(locale: String): Array<String> {
    val san = locale
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .replace(Regex("_+"), "_")
        .replace(Regex("^_|_$"), "")
        .lowercase()
    if (san.indexOf('_') > 0) return arrayOf(san, san.substring(0, san.indexOf('_')), "c")
    if (san != "c") return arrayOf(san, "c")
    return arrayOf("c")
}

private val loaders = Seq<ClassLoader>()
private val localeCache = ObjectMap<ClassLoader, ObjectMap<String, LocaleFile?>>()
private class LocaleFile {
    companion object {
        fun get(localeBase: String, key: String): String {
            var i = loaders.size
            while (--i >= 0) {
                val loader = loaders[i];
                for (locale in destructLocale(localeBase)) {
                    val file = get(loader, locale)
                    if (file == null || !file.tls.containsKey(key)) continue
                    return file.tls.get(key)
                }
            }
            return key
        }

        fun get(loader: ClassLoader, locale: String): LocaleFile? {
            val cache = localeCache.get(loader) { ObjectMap() }
            if (cache.containsKey(locale)) { return cache.get(locale) }

            val str: String
            try {
                val stream = loader.getResourceAsStream("lang/${locale}.l")
                if (stream == null) {
                    Log.warn("Couldn't find resource 'lang/${locale}.l'")
                    cache.put(locale, null)
                    return null
                }
                val bytes = stream.readBytes()
                str = bytes.toString(Charsets.UTF_8)
            } catch (_: IOException) {
                return null
            }

            val file = LocaleFile()
            var prefix = ""
            var name = ""
            for (line in str.lines()) {
                // TODO: Multiline strings
                // TODO: Continuous strings
                val trimmed = line.trim()
                if (trimmed.matches(Regex("^\\[[\\w0-9._-]+]$"))) {
                    prefix = trimmed.substring(1, trimmed.length - 1)
                    continue
                }
                val eqIdx = line.indexOf('=')
                if (eqIdx != -1) {
                    name = line.substring(0, eqIdx).trim()
                    if (!prefix.isEmpty()) name = "${prefix}.$name"
                    val value = line.substring(eqIdx + 1).trim()
                    file.tls.put(name, value)
                }
            }

            cache.put(locale, file)
            return file
        }
    }

    private val tls = ObjectMap<String, String>()
}

class LCtx(private val parent: LCtx? = null) {
    private val tls = ObjectMap<String, String>()
    private val tlsRaw = ObjectMap<String, String>()

    /**
     * Add a mapping for a translation key.
     */
    fun put(key: String, value: String) {
        tls.put(key, value)
    }

    /**
     * Set translation key to unformatted text.
     */
    fun putRaw(key: String, text: String) {
        tlsRaw.put(key, text)
    }

    fun tlKey(key: String, lang: String): String {
        if (tlsRaw.containsKey(key)) return tlsRaw.get(key)!!
        if (tls.containsKey(key)) return tl(tls.get(key)!!, lang)
        return tl(if (parent == null) LocaleFile.get(lang, key) else parent.tlKey(key, lang), lang)
    }

    fun tl(text: String, lang: String): String {
        // TODO: Properly parse colors
        // TODO: :emojis:
        var currentText = text;
        var i = minIdx(
            currentText.indexOf('{'),
            currentText.indexOf('\\'),
        )
        while (i != -1 && i < currentText.length) {
            if (currentText[i] == '{') {
                // TODO: {if <cond> {} ..else if <cond> {} ..else {}}
                val o = currentText.indexOf('}', i)
                if (o == -1) return "tl error: unenclosed interpolation"
                val key = currentText.substring(i + 1..o - 1)
                currentText = currentText.substring(0..i - 1) +
                        // TODO: tl the key
                        tlKey(key, lang) +
                        currentText.substring(o + 1)
            }
            else if (currentText[i] == '\\') {
                i++
                currentText = if (i >= currentText.length) {
                    currentText.substring(i - 2)
                } else {
                    currentText.substring(0..i - 2) + currentText.substring(i)
                }
            }

            i = minIdx(
                currentText.indexOf('{', i + 1),
                currentText.indexOf('\\', i + 1),
            )
        }
        return currentText
    }
}

interface L<Self> {
    /**
     * Add a mapping for a translation key.
     */
    fun put(key: String, value: String): Self;
    /**
     * Set translation key to unformatted text.
     */
    fun putRaw(key: String, text: String): Self;
}

class La(val ctx: LCtx = LCtx()): L<La> {
    /**
     * Add a mapping for a translation key.
     */
    override fun put(key: String, value: String): La {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun putRaw(key: String, text: String): La {
        ctx.putRaw(key, text)
        return this
    }

    fun done(key: String) {
        for (player in Groups.player) {
            Lc(player, ctx).done(key)
        }
    }
}
class Lc(val player: Player, val ctx: LCtx = LCtx()): L<Lc> {
    /**
     * Add a mapping for a translation key.
     */
    override fun put(key: String, value: String): Lc {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun putRaw(key: String, text: String): Lc {
        ctx.putRaw(key, text)
        return this
    }

    fun done(key: String) {
        val l = Ls(player.locale, ctx)
        for (line in l.done(key).lines()) {
            player.sendMessage(line)
        }
    }
}
class Ls(val locale: String, val ctx: LCtx = LCtx()): L<Ls> {
    /**
     * Add a mapping for a translation key.
     */
    override fun put(key: String, value: String): Ls {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun putRaw(key: String, text: String): Ls {
        ctx.putRaw(key, text)
        return this
    }

    fun done(key: String): String {
        val line = LocaleFile.get(locale, key)
        return ctx.tl(line, locale)
    }
}

object Tl {
    fun broadcast(): La = La()
    fun send(player: Player): Lc = Lc(player)
    fun fmtFor(player: Player): Ls = Ls(player.locale)
    fun fmt(locale: String): Ls = Ls(locale)

    fun init(loader: ClassLoader) {
        loaders.add(loader)
    }
}