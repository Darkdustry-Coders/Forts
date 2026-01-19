package forts.modifiers

import forts.Modifier
import mindurka.api.on
import mindustry.game.EventType
import mindustry.world.blocks.storage.CoreBlock

class Paper: Modifier() {
    override fun chance() = 0.05f

    override fun start() {
        on<EventType.BuildDamageEvent>(lifetime = lifetime) {
            if (it.build is CoreBlock.CoreBuild) {
                it.build.kill()
            }
        }
    }
}
