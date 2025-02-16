package forts.modifiers

import forts.Modifier
import mindustry.world.Tile
import mindustry.world.Block
import mindustry.Vars
import mindustry.game.Team
import arc.struct.IntIntMap
import arc.math.Mathf
import arc.util.Timer

class ResourceFlood: Modifier() {
    var untilFast = 100

    override fun chance() = 0.2f

    override fun start() {
        var timer: Array<Timer.Task> = arrayOf(Timer.schedule({}, 0f))
        timer[0] = runEvery(6f) {
            Team.all.forEach {
                val core = it.core() ?: return@forEach
                val items = core.items()
                Vars.content.items().each { item ->
                    val count = items.get(item)
                    if (count > 0 && count < 1000)
                        items.add(item, 100)
                }
            }
            untilFast--
            if (untilFast == 0) {
                cancel(timer[0])
                timer[0] = runEvery(2f) {
                    Team.all.forEach {
                        val core = it.core() ?: return@forEach
                        val items = core.items()
                        Vars.content.items().each { item ->
                            val count = items.get(item)
                            if (count > 0 && count < 1000)
                                items.add(item, 100)
                        }
                    }
                }
            }
        }
    }
}
