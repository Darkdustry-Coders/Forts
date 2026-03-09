package forts.modifiers

import arc.util.Time
import forts.Modifier
import mindurka.api.interval
import mindurka.api.on
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import kotlin.math.min

class ResourceFlood: Modifier() {

    override fun chance() = 0.2f

    override fun start() {
        var lastTime = Time.millis()
        var untilFast = 200f
        var inc = 0f

        on<EventType.Trigger>(lifetime = lifetime) {
            if (it != EventType.Trigger.update) return@on
            if (Vars.state.isPaused) return@on

            val delta = (Time.millis() - lastTime).toFloat() / 1000f
            lastTime = Time.millis()
            inc += delta * 100 / (if (untilFast <= 0f) 2f else 6f)
            val intInc = inc.toInt()
            inc %= 1

            Team.all.forEach {
                val cap = if (untilFast <= 0) 2000 else 1000
                val core = it.core() ?: return@forEach
                val items = core.items
                Vars.content.items().each { item ->
                    val count = items.get(item)
                    if (count in 1..cap)
                        items.set(item, min(cap, items.get(item) + intInc))
                }
            }

            untilFast -= delta
        }
    }
}
