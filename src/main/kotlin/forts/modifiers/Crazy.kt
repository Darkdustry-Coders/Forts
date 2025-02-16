package forts.modifiers

import forts.Modifier
import mindustry.world.Tile
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.Vars
import arc.struct.IntIntMap
import arc.math.Mathf

class Crazy: Modifier() {
    val morphMap = IntIntMap()

    override fun disableAssist(): Boolean = true
    override fun chance() = 0.1f

    override fun start() {
        Vars.state.rules.deconstructRefundMultiplier = 1f
        val free = Vars.content.blocks()
            .select { it !is ConstructBlock }
        Vars.content.blocks()
            .each<Block>({ it !is ConstructBlock }) {
                while (true) {
                    val idx = Mathf.random(0, free.size - 1)
                    val obj = free[idx]
                    if (it.size != obj.size) continue
                    morphMap.put(it.id.toInt(), obj.id.toInt())
                    free.remove(idx);
                    break
                }
            }
    }
    override fun mapBlock(tile: Tile, block: Block): Block? {
        return Vars.content.block(morphMap.get(block.id.toInt()))
    }

    override fun blockPlaced(tile: Tile) {
        val block = tile.block();
        if (block == null) return;

        if (block.isFloor) {
            tile.setFloor(block.asFloor())
        }
        else if (block.isOverlay) {
            tile.setOverlay(block)
        }
    }
}
