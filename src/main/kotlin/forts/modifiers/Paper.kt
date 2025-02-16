package forts.modifiers

import forts.Modifier
import mindustry.game.EventType
import mindustry.world.blocks.storage.CoreBlock

class Paper: Modifier() {
    override fun chance() = 0.05f

    private fun coreDamageEvent(event: EventType.BuildDamageEvent) {
        if (event.build is CoreBlock.CoreBuild) {
            event.build.health(0f)
        }
    }

    override fun start() {
        registerEvent(this::coreDamageEvent)
    }
}