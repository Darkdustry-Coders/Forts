package buj.mitools

import arc.func.Cons
import arc.struct.ObjectMap
import arc.struct.Seq
import forts.Main
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player

interface Ti {
    fun key(key: String, value: Any?)
}

fun sanitizeLocale(locale: String) = locale.replace(Regex("[^a-zA-Z]"), "").lowercase()

private val locales = ObjectMap<String, LocaleFile?>()

private val nullFile = LocaleFile(null, Seq.with())
class LocaleFile(val parent: LocaleFile?, val strings: Seq<String>) {
    fun ti(name: String, cb: Cons<Ti>) {
        val keys = ObjectMap<String, String>()
        cb.get(object : Ti {
            override fun key(key: String, value: Any?) {
                keys.put(key, value?.toString() ?: "null")
            }
        })
    }
}

private fun localeFile(locale: String): LocaleFile? {
    if (locales.containsKey(locale)) return locales.get(locale)
    val raw = Vars.mods.getMod(Main::class.java).root.child("lang")
        .child("${sanitizeLocale(locale)}.lang")
    if (!raw.exists()) return null
    raw.readString().lines().forEach {
        if (it.contains('=') || it.trim().startsWith('#')) return@forEach
        it.split(Regex("="), 1)
    }
    return LocaleFile(if (locale == "c") null else localeFile("c"), Seq.with())
}

/**
 * Load a translation string
 */
fun ti(locale: Player, name: String): String = ti(locale, name) {}
/**
 * Load a translation string
 */
fun ti(locale: Player, name: String, closure: Cons<Ti>): String = ti(locale.locale, name, closure)
/**
 * Load a translation string
 */
fun ti(locale: String, name: String): String = ti(locale, name) {}
/**
 * Load a translation string
 */
fun ti(locale: String, name: String, closure: Cons<Ti>): String {
    val file = localeFile(locale) ?: localeFile("c") ?: nullFile

    return name
}

fun tisend(key: String) = tisend(key) {}
fun tisend(key: String, closure: Cons<Ti>) = Groups.player.each { tisend(it, key, closure) }
fun tisend(to: Player, key: String) = tisend(to, key) {}
fun tisend(to: Player, key: String, closure: Cons<Ti>) = to.sendMessage(ti(to, key, closure))
