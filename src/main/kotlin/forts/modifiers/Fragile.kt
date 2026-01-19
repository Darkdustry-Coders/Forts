package forts.modifiers

import forts.Modifier
import mindustry.world.Tile
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.Vars
import arc.struct.IntIntMap
import arc.math.Mathf
import mindurka.api.Cancel

class Fragile: Modifier() {
    override fun chance() = 0.2f
    override fun start() {
        Vars.state.rules.unitHealthMultiplier /= 2f
        Vars.state.rules.blockHealthMultiplier /= 2f

        lifetime.bind(Cancel {
            Vars.state.rules.unitHealthMultiplier *= 2f
            Vars.state.rules.blockHealthMultiplier *= 2f
        })
    }
}
