package forts.modifiers

import arc.Core
import forts.Modifier
import mindurka.api.on
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.storage.CoreBlock

class Overkill: Modifier() {
    override fun chance() = 0.1f

    override fun start() {
        on<EventType.BlockDestroyEvent>(lifetime = lifetime) {
            if (it.tile.build == null) return@on
            if (it.tile.block().privileged) return@on
            if (it.tile.build is ConstructBlock.ConstructBuild) return@on
            if (it.tile.build is CoreBlock.CoreBuild) return@on
            if (it.tile.build.team == Team.derelict) return@on

            val block = it.tile.build.block
            val rot = it.tile.build.rotation

            val hpdiff = Vars.state.rules.teams[Team.derelict].blockHealthMultiplier / Vars.state.rules.teams[it.tile.team()].blockHealthMultiplier

            Core.app.post {
                it.tile.setNet(block, Team.derelict, rot)
                it.tile.build.maxHealth *= hpdiff
                it.tile.build.health *= hpdiff
            }
        }
    }
}
