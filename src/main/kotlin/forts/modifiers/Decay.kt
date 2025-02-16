package forts.modifiers

import arc.math.Mathf
import forts.Modifier
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.world.blocks.storage.CoreBlock

class Decay: Modifier() {
    var amount = 1f

    override fun chance() = 0.03f

    override fun start() {
        runEvery(1f) {
            amount += 0.02f
            repeat(amount.toInt()) {
                val len = Groups.build.size()
                if (len == 0) return@repeat
                val build = Groups.build.index(Mathf.random(0, len - 1))
                if (!build.block.targetable) return@repeat
                val modifier = if (build is CoreBlock.CoreBuild) 8f else 4f
                build.damage(build.block.health *
                        Vars.state.rules.blockHealthMultiplier *
                        Vars.state.rules.teams.get(build.team()).blockHealthMultiplier / modifier)
            }
        }
    }
}