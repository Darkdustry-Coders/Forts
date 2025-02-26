package forts

import mindustry.game.EventType
import mindustry.world.Tile
import mindustry.world.Block
import arc.Events
import arc.func.Boolf
import arc.func.Cons
import arc.func.Prov
import arc.struct.Seq
import arc.math.Mathf
import arc.util.Log
import arc.util.Timer
import buj.tl.Tl
import mindustry.Vars
import mindustry.game.MapObjectives
import mindustry.gen.Call
import mindustry.gen.Groups
import kotlin.reflect.KClass

abstract class Modifier {
    protected val eventsToRemove = HashMap<Class<*>, Seq<Cons<*>>>()
    protected val timersToRemove = Seq<Timer.Task>()

    var priority = 100

    protected inline fun <reified T> registerEvent(handler: Cons<T>) {
        Events.on<T>(T::class.java, handler)
        if (!eventsToRemove.containsKey(T::class.java))
            eventsToRemove.put(T::class.java, Seq())
        eventsToRemove[T::class.java]!!.add(handler)
    }
    protected fun runEvery(time: Float, cb: Runnable): Timer.Task {
        val task = Timer.schedule(cb, time, time)
        timersToRemove.add(task)
        return task
    }
    protected fun runAfter(time: Float, cb: Runnable): Timer.Task {
        val task = Timer.schedule(cb, time)
        timersToRemove.add(task)
        return task
    }
    protected fun cancel(task: Timer.Task) {
        timersToRemove.remove(task)
        task.cancel()
    }

    abstract fun chance(): Float
    open fun disableAssist(): Boolean = false
    open fun start() {}
    open fun mapBlock(tile: Tile, block: Block): Block? = block
    open fun blockPlaced(tile: Tile) {}
    open fun destroy() {}
    @Suppress("UNCHECKED_CAST")
    fun finalDestroy() {
        for (x in eventsToRemove.entries) {
            for (y in x.value) {
                Events.remove<Any>(x.key as Class<Any>, y as Cons<Any>);
            }
        }
        timersToRemove.each { it.cancel() }
    }
}

fun Modifier.getName() = this.javaClass.simpleName.lowercase()

private val modifiers = Seq<Modifier>()

class AvailableModifier<T: Modifier>(val source: KClass<T>, val create: Prov<T>) {
    @Suppress("UNCHECKED_CAST")
    fun upcast(): AvailableModifier<Modifier> {
        return this as AvailableModifier<Modifier>
    }
}

fun availableModifiers(): Seq<AvailableModifier<Modifier>> {
    val mods = Seq<AvailableModifier<Modifier>>()
    mods.add(AvailableModifier(forts.modifiers.Crazy::class) { forts.modifiers.Crazy() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Fragile::class) { forts.modifiers.Fragile() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.ResourceFlood::class) { forts.modifiers.ResourceFlood() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Overkill::class) { forts.modifiers.Overkill() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Paper::class) { forts.modifiers.Paper() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Buildup::class) { forts.modifiers.Buildup() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Unstable::class) { forts.modifiers.Unstable() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Glass::class) { forts.modifiers.Glass() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Decay::class) { forts.modifiers.Decay() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Luxury::class) { forts.modifiers.Luxury() }.upcast())
    mods.add(AvailableModifier(forts.modifiers.Airforce::class) { forts.modifiers.Airforce() }.upcast())
    return mods
}
fun activeModifiers(): Seq<Modifier> = modifiers
fun addModifier(modifier: Modifier) {
    modifiers.add(modifier)
    modifier.start()
    Log.info("Enabled modifier ${modifier.getName()}")
    Call.setRules(Vars.state.rules)
    modifiers.sort { a, b -> b.priority - a.priority }
}
fun removeModifiers(cb: Boolf<Modifier>) {
    val prevLen = modifiers.size
    if (modifiers.removeAll {
        if (cb.get(it)) {
            it.destroy()
            it.finalDestroy()
            Log.info("Disabled modifier ${it.getName()}")
            return@removeAll true
        }
        return@removeAll false
    }.size != prevLen) {
        Call.setRules(Vars.state.rules)
        modifiers.sort { a, b -> b.priority - a.priority }
    }
}

fun mapBlockModifiers(tile: Tile, block: Block): Block? {
    var b: Block? = block
    for (mo in modifiers) {
        b = mo.mapBlock(tile, b!!)
        if (b == null) return null
    }
    return b
}

fun blockPlacedModifiers(tile: Tile) {
    for (mo in modifiers) {
        mo.blockPlaced(tile)
    }
}

fun initModifiers() {
    Events.on(EventType.WorldLoadBeginEvent::class.java) {
        modifiers.each { it.destroy() }
        modifiers.each { it.finalDestroy() }
        modifiers.clear()
    }

    Events.on(EventType.PlayEvent::class.java) {
        if (Mathf.random() < 0.7) return@on
        if (Vars.state.rules.objectives.any { it is MapObjectives.FlagObjective && it.flag == "nomodifiers" }) return@on

        availableModifiers().each { modifiers.add(it.create.get()) }

        Vars.state.rules.objectives.each {
            if (it !is MapObjectives.FlagObjective) return@each
            if (it.flag != "disablemodifiers") return@each

            it.details?.let {
                for (line in it.lines()) {
                    modifiers.removeAll { it.getName() == line.trim() }
                }
            }
            it.text?.let {
                for (line in it.lines()) {
                    modifiers.removeAll { it.getName() == line.trim() }
                }
            }
        }

        if (modifiers.contains { it is forts.modifiers.Decay } && modifiers.contains { it is forts.modifiers.Paper }) {
            if (Mathf.randomBoolean()) modifiers.remove { it is forts.modifiers.Decay }
            else modifiers.remove { it is forts.modifiers.Paper }
        }

        modifiers.removeAll { it.chance() < Mathf.random() }
        modifiers.sort { a, b -> b.priority - a.priority }

        modifiers.each { it.start() }
        modifiers.each { Log.info("yoo ${it.getName()}") }
    }
}

fun gameOver() {
    if (modifiers.isEmpty) return

    for (player in Groups.player) {
        Tl.send(player).done("{forts.notice.modifiers}")
        for (modifier in modifiers) {
            val m = modifier.javaClass.simpleName.lowercase()
            Tl.send(player).done("[red]- [yellow]{forts.modifiers.${m}.name}\n{forts.modifiers.${m}.desc}")
        }
    }
}