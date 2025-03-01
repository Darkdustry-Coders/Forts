package forts.modifiers

import arc.Core
import forts.Modifier
import mindustry.Vars
import mindustry.game.Team

class Luxury: Modifier() {
    override fun chance() = 0.1f

    override fun start() {
        Vars.state.rules.buildCostMultiplier *= 2f
        Vars.state.rules.buildSpeedMultiplier *= 2f
        Core.app.post {
            Team.all.forEach {
                if (!it.active()) return@forEach
                val core = it.core()
                if (core == null) return@forEach
                core.items().each { item, count ->
                    core.items().add(item, count)
                }
            }
        }
    }

    override fun destroy() {
        Vars.state.rules.buildCostMultiplier /= 2f
        Vars.state.rules.buildSpeedMultiplier /= 2f
    }
}
