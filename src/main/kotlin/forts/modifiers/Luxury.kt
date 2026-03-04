package forts.modifiers

import arc.Core
import forts.Modifier
import mindurka.api.Cancel
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
                val core = it.core() ?: return@forEach
                core.items.each { item, count ->
                    core.items.add(item, count)
                }
            }
        }

        lifetime.alsoCancel(Cancel {
            Vars.state.rules.buildCostMultiplier /= 2f
            Vars.state.rules.buildSpeedMultiplier /= 2f
        })
    }
}
