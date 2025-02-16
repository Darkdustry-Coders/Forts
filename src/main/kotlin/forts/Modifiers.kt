package forts

import mindustry.game.EventType
import mindustry.world.Tile
import mindustry.world.Block
import arc.Events
import arc.func.Cons
import arc.struct.Seq
import arc.math.Mathf
import arc.util.Log
import arc.util.Timer

abstract class Modifier {
    protected val eventsToRemove = HashMap<Class<*>, Seq<Cons<*>>>()
    protected val timersToRemove = Seq<Timer.Task>()

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

private val modifiers = Seq<Modifier>()

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
    Events.on(EventType.PlayEvent::class.java) {
        modifiers.each { it.destroy() }
        modifiers.each { it.finalDestroy() }
        modifiers.clear()

        modifiers.add(forts.modifiers.Crazy())
        modifiers.add(forts.modifiers.Fragile())
        modifiers.add(forts.modifiers.ResourceFlood())
        modifiers.add(forts.modifiers.Paper())
        modifiers.add(forts.modifiers.Buildup())
        modifiers.add(forts.modifiers.Unstable())
        modifiers.add(forts.modifiers.Overkill())
        modifiers.add(forts.modifiers.Glass())
        modifiers.add(forts.modifiers.Airforce())
        modifiers.add(forts.modifiers.Decay())
        modifiers.add(forts.modifiers.Luxury())

        if (modifiers.contains { it is forts.modifiers.Decay } && modifiers.contains { it is forts.modifiers.Paper }) {
            if (Mathf.randomBoolean()) modifiers.remove { it is forts.modifiers.Decay }
            else modifiers.remove { it is forts.modifiers.Paper }
        }

        modifiers.removeAll { it.chance() < Mathf.random() }

        modifiers.each { it.start() }
        modifiers.each { Log.info("yoo ${it.javaClass.simpleName.lowercase()}") }
    }
}

fun gameOver() {
    if (modifiers.isEmpty) return
}