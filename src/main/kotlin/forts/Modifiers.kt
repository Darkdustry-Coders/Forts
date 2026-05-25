package forts

import mindustry.game.EventType
import arc.func.Boolf
import arc.func.Prov
import arc.struct.Seq
import arc.math.Mathf
import arc.util.Log
import buj.tl.Tl
import buj.tl.Tlu
import mindurka.api.BuildEvent
import mindurka.api.Lifetime
import mindurka.api.RoundEndEvent
import mindurka.api.on
import mindurka.util.Ref
import mindustry.Vars
import mindustry.gen.Call
import kotlin.reflect.KClass

abstract class Modifier {
    val lifetime = object : Lifetime() {
        override fun uponStart() {
            Round.alsoCancel(this)
        }
        override fun uponEnd() {
            cancelled = false
        }
    }

    abstract fun chance(): Float
    open fun disableAssist(): Boolean = false
    open fun start() {}
    open fun onBuild(event: BuildEvent) {}

    open val disableUnitPayloads: Boolean = false
    open val canBeEnabled: Boolean = true
}

fun Modifier.getName() = this.javaClass.simpleName.lowercase()

private val modifiers = Seq<Modifier>()

class AvailableModifier<T: Modifier>(val source: KClass<out T>, val create: Prov<T>) {
    @Suppress("UNCHECKED_CAST")
    fun upcast(): AvailableModifier<Modifier> {
        return this as AvailableModifier<Modifier>
    }
}

val enableUnitPayloads = Ref(true)

private val modifierOrder: Array<KClass<out Modifier>> = arrayOf(
    forts.modifiers.Crazy::class,
    forts.modifiers.Fragile::class,
    forts.modifiers.ResourceFlood::class,
    forts.modifiers.Overkill::class,
    forts.modifiers.Paper::class,
    forts.modifiers.Buildup::class,
    forts.modifiers.Unstable::class,
    forts.modifiers.Glass::class,
    forts.modifiers.Decay::class,
    forts.modifiers.Luxury::class,
    forts.modifiers.Airforce::class,
    forts.modifiers.Copper::class,
    forts.modifiers.March::class,
)
val availableModifiers = Array(modifierOrder.size) {
    val klass = modifierOrder[it]
    val modifier = klass.constructors.first().call()
    AvailableModifier(klass) { modifier }.upcast()
}
fun activeModifiers(): Seq<Modifier> = modifiers
private fun reorderModifiers() {
    var i = 0
    for (modifierTy in modifierOrder) {
        val removed = Ref<Modifier?>(null)
        if (modifiers.remove {
            val c = it.javaClass == modifierTy.java
            if (c) removed.r = it
            c
        }) {
            val modifier = removed.r as Modifier
            modifiers.insert(i, modifier)
            i++
        }
    }
}
fun addModifier(modifier: Modifier) {
    if (!modifier.canBeEnabled) {
        Log.err("Modifier ${modifier.getName()} cannot be enabled")
        return
    }
    modifiers.add(modifier)
    modifier.start()
    Log.info("Enabled modifier ${modifier.getName()}")
    Call.setRules(Vars.state.rules)
    reorderModifiers()
    refresh()
}
fun removeModifiers(cb: Boolf<Modifier>) {
    val prevLen = modifiers.size
    if (modifiers.removeAll {
        if (cb.get(it)) {
            if (!it.canBeEnabled) {
                Log.err("Modifier ${it.getName()} cannot be disabled")
                return@removeAll false
            }

            it.lifetime.cancel()
            Log.info("Disabled modifier ${it.getName()}")
            return@removeAll true
        }
        return@removeAll false
    }.size != prevLen) {
        Call.setRules(Vars.state.rules)
        reorderModifiers()
        refresh()
    }
}

fun onBuild(buildEvent: BuildEvent) {
    modifiers.each { it.onBuild(buildEvent) }
}

private fun refresh() {
    enableUnitPayloads.r = modifiers.all { !it.disableUnitPayloads }
}

fun initModifiers() {
    on<RoundEndEvent> {
        modifiers.each { it.lifetime.cancel() }
        modifiers.clear()
    }

    on<EventType.WorldLoadEvent> {
        if (Mathf.random() < 0.7) return@on
        // if (SpecialSettings.formatVersion == FormatVersion.Zero && SpecialSettings.flagOrError("nomodifiers") != null ||
        //     SpecialSettings.flagOrError("mdrk.modifiers.disabled")?.value?.isEmpty() == true)
        //     return@on

        availableModifiers.forEach { modifiers.add(it.create.get()) }

        //  if (SpecialSettings.formatVersion == FormatVersion.Zero) {
        //      Vars.state.rules.objectives.each {
        //          if (it !is MapObjectives.FlagObjective) return@each
        //          if (it.flag != "disablemodifiers") return@each

        //          it.details?.let {
        //              for (line in it.lines()) {
        //                  modifiers.removeAll { it.getName() == line.trim() }
        //              }
        //          }
        //          it.text?.let {
        //              for (line in it.lines()) {
        //                  modifiers.removeAll { it.getName() == line.trim() }
        //              }
        //          }
        //      }
        //  } else {
        //      val rule = SpecialSettings.flagOrNull("mdrk.modifiers.disabled")
        //      if (rule != null) {
        //          for (modifier in rule.value) {
        //              val lenBefore = modifiers.size
        //              modifiers.remove { it.getName() == modifier }
        //              if (lenBefore == modifiers.size)
        //                  SpecialSettings.error("mdrk.modifiers.disabled: no modifier named '$modifier'")
        //          }
        //      }
        //  }

        modifiers.removeAll { it.chance() < Mathf.random() }

        if (modifiers.contains { it is forts.modifiers.Decay } && modifiers.contains { it is forts.modifiers.Paper }) {
            if (Mathf.randomBoolean()) modifiers.remove { it is forts.modifiers.Decay }
            else modifiers.remove { it is forts.modifiers.Paper }
        }

        modifiers.each { Log.info("yoo ${it.getName()}") }
    }

    on<EventType.PlayEvent> {
        modifiers.each { it.start() }
        refresh()
    }
}

fun gameOver() {
    if (modifiers.isEmpty) return

    Tl.broadcast()
        .apply { Tlu.list(this, modifiers.map { it.javaClass.simpleName.lowercase() }, "modifiers") }
        .done("{forts.notice.modifiers}")
}