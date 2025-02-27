// TODO: Proper parser

package buj.tl

import arc.math.Mathf
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

private val COLORS = arrayOf(
    "clear",
    "black",
    "white",
    "lightgray",
    "gray",
    "darkgray",
    "blue",
    "navy",
    "royal",
    "slate",
    "sky",
    "cyan",
    "teal",
    "green",
    "acid",
    "lime",
    "forest",
    "olive",
    "yellow",
    "gold",
    "goldenrod",
    "orange",
    "brown",
    "tan",
    "brick",
    "red",
    "scarlet",
    "coral",
    "salmon",
    "pink",
    "magenta",
    "purple",
    "violet",
    "maroon",
)
private fun isValidColor(color: String): Boolean {
    if (color.startsWith("#")) return color.matches(Regex("^#([0-9]{1,6}|[0-9]{8})$"))
    return COLORS.contains(color)
}

private val loaders = Seq<ClassLoader>()
private val localeCache = ObjectMap<ClassLoader, ObjectMap<String, LocaleFile?>>()
private class LocaleFile {
    companion object {
        fun get(localeBase: String, key: String): Script {
            var i = loaders.size
            while (--i >= 0) {
                val loader = loaders[i];
                for (locale in destructLocale(localeBase)) {
                    val file = get(loader, locale)
                    if (file == null || !file.tls.containsKey(key)) continue
                    return file.tls.get(key)
                }
            }
            return ScrText("[red]$key[]")
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
            var value = ""
            for (line in str.lines()) {
                val trimmed = line.trim()
                if (trimmed.matches(Regex("^\\[[\\w0-9._-]+]$"))) {
                    prefix = trimmed.substring(1, trimmed.length - 1)
                    continue
                }
                val eqIdx = minIdx(line.indexOf('='), line.indexOf(':'))
                if (eqIdx != -1) {
                    val maybeName = line.substring(0, eqIdx).trim()

                    if (maybeName.isEmpty()) {
                        if (line[eqIdx] == '=') {
                            value += "\n" + line.substring(eqIdx + 1).trim()
                        } else if (!line.substring(eqIdx + 1).trim().isEmpty()) {
                            value += " " + line.substring(eqIdx + 1).trim()
                        }
                        continue
                    }

                    if (!name.isEmpty()) file.tls.put(name, Tl.parse(value))
                    name = maybeName
                    if (!prefix.isEmpty()) name = "${prefix}.$name"
                    value = line.substring(eqIdx + 1).trim()
                }
            }
            if (!name.isEmpty()) file.tls.put(name, Tl.parse(value))

            cache.put(locale, file)
            return file
        }
    }

    private val tls = ObjectMap<String, Script>()
}

class LCtx(private val parent: LCtx? = null) {
    private val tls = ObjectMap<String, Script>()

    /**
     * Add a mapping for a translation key.
     */
    fun put(key: String, value: Script) {
        tls.put(key, value)
    }

    /**
     * Set translation key to unformatted text.
     */
    fun put(key: String, text: String) {
        tls.put(key, ScrText(text))
    }

    fun tl(key: String, lang: String): Script {
        if (tls.containsKey(key)) return tls[key]
        return LocaleFile.get(lang, key)
    }
}

interface L<Self> {
    /**
     * Add a mapping for a translation key.
     */
    fun put(key: String, value: Script): Self;
    /**
     * Set translation key to unformatted text.
     */
    fun put(key: String, text: String): Self;
}

class La(val ctx: LCtx = LCtx()): L<La> {
    /**
     * Add a mapping for a translation key.
     */
    override fun put(key: String, value: Script): La {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun put(key: String, text: String): La {
        ctx.put(key, text)
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
    override fun put(key: String, value: Script): Lc {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun put(key: String, text: String): Lc {
        ctx.put(key, text)
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
    override fun put(key: String, value: Script): Ls {
        ctx.put(key, value)
        return this
    }
    /**
     * Set translation key to unformatted text.
     */
    override fun put(key: String, text: String): Ls {
        ctx.put(key, text)
        return this
    }

    fun done(key: String): String {
        val script = Tl.parse(key)
        Log.info(script.debug())
        return script.append(ctx, "", locale)
    }
}

interface Script {
    fun append(ctx: LCtx, source: String, lang: String): String
    fun debug(): String
}

private class ScrCombo(val list: Seq<Script>): Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        var txt = source
        for (scr in list) {
            txt = scr.append(ctx, txt, lang)
        }
        return txt
    }

    override fun debug(): String = "Combo(${list.map { it.debug() }.joinToString(", ")})"
}

private class ScrText(text: String): Script {
    val text: String

    init {
        // TODO: :emojis:

        var idx = text.lastIndex
        while (idx > 0 && text[idx] != '[' && !text[idx].isWhitespace() && text[idx] != ']') idx--
        this.text = if (text[idx] == '[') text.substring(0..idx.coerceAtMost(1) - 1) + '[' + text.substring(idx)
                    else text
    }

    override fun append(ctx: LCtx, source: String, lang: String): String {
        return source + text
    }

    override fun debug(): String = "\"$text\""
}

private class ScrKey(val key: Script): Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        val key = this.key.append(ctx, "", lang)
        return ctx.tl(key, lang).append(ctx, source, lang)
    }

    override fun debug(): String = "Key(${key.debug()})"
}

private fun parseUnicode(script: String, idx: Array<Int>): Char {
    var num = 0
    if (idx[0] >= script.length || script[idx[0]] != '{') throw RuntimeException("invalid unicode escape")
    while (++idx[0] < script.length && script[idx[0]] != '}') {
        val ch = script[idx[0]]
        num *= 16
        num += if (ch >= '0' && ch <= '9') ch.code - '0'.code
               else if (ch >= 'a' && ch <= 'f') ch.code - 'a'.code + 10
               else if (ch >= 'A' && ch <= 'F') ch.code - 'a'.code + 10
               else throw RuntimeException("invalid unicode escape")
    }
    if (++idx[0] >= script.length || script[idx[0]] != '}') throw RuntimeException("invalid unicode escape")
    if (!(Char.MIN_VALUE.code..Char.MAX_VALUE.code).contains(num)) throw RuntimeException("invalid unicode escape")
    idx[0]++
    return num.toChar()
}

private fun parseKey(script: String, idx: Array<Int>): Script {
    // TODO: {if (<cond>) () ..else if (<cond>) () ..else ()}
    // TODO: {each ({name} in {key} split (<sep>) [join (<sep>)]}

    if (idx[0] >= script.length || script[idx[0]++] != '{') throw RuntimeException("invalid key sequence")
    var text = ""
    var depth = 1
    var backslash = false
    do {
        if (backslash) {
            text += script[idx[0]]
            backslash = true
            continue
        }
        when (script[idx[0]]) {
            '{' -> depth++
            '}' -> depth--
            '\\' -> { backslash = true; continue }
        }
        if (depth > 0) text += script[idx[0]]
        else break
    } while (++idx[0] < script.length)
    Log.info("parseKey text=$text")
    return ScrKey(Tl.parse(text))
}

private fun parseRoot(script: String, idx: Array<Int>): Script {
    val combo = Seq<Script>()

    var text = ""
    var backslash = false
    idx[0]--
    while (++idx[0] < script.length) {
        val ch = script[idx[0]]

        if (backslash) {
            backslash = false

            if (ch == 'u') {
                text += parseUnicode(script, idx)
            }

            text += ch
            continue
        }

        when (ch) {
            '\\' -> backslash = true
            '{' -> {
                if (!text.isEmpty()) combo.add(ScrText(text))
                text = ""
                combo.add(parseKey(script, idx))
            }
            '}' -> {
                Log.err("FATAL!")
                Log.err("text=$text")
                Log.err("idx=${idx[0]}")
                Log.err("script=$script")
                throw RuntimeException("unexpected '}'")
            }
            else -> text += ch
        }
    }
    if (!text.isEmpty()) combo.add(ScrText(text))

    if (combo.size == 1) return combo[0]
    return ScrCombo(combo)
}

object Tl {
    fun broadcast(): La = La()
    fun send(player: Player): Lc = Lc(player)
    fun fmtFor(player: Player): Ls = Ls(player.locale)
    fun fmt(locale: String): Ls = Ls(locale)

    fun init(loader: ClassLoader) {
        loaders.add(loader)
    }

    fun parse(script: String): Script {
        val idx = arrayOf(0)
        val script = parseRoot(script, idx)
        Log.info("parse ${script.debug()}")
        return script
    }
}