package forts.modifiers

import forts.Modifier
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Lifetime
import mindurka.api.Priority
import mindurka.api.on
import mindustry.world.Tile

class Buildup: Modifier() {
    override fun chance() = 0.05f

    override fun onBuild(event: BuildEvent) {
        event.replacementHealth = 1f
    }
}
