package forts.modifiers

import forts.Modifier
import mindustry.world.Tile

class Buildup: Modifier() {
    override fun chance() = 0.05f

    override fun blockPlaced(tile: Tile) {
        tile.build?.health(1f)
        tile.build?.healthChanged()
    }
}