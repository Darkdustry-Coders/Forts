package forts.modifiers

import arc.Core
import forts.Modifier
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.world.blocks.ConstructBlock

class Overkill: Modifier() {
    override fun chance() = 0.1f

    override fun start() {
        registerEvent<EventType.BlockDestroyEvent> {
            if (it.tile.build == null) return@registerEvent
            if (it.tile.build is ConstructBlock.ConstructBuild) return@registerEvent
            if (it.tile.build.team == Team.derelict) return@registerEvent

            val block = it.tile.build.block
            val rot = it.tile.build.rotation

            Core.app.post { it.tile.setNet(block, Team.derelict, rot) }
        }
    }
}