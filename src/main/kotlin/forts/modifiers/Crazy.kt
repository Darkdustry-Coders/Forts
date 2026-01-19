package forts.modifiers

import forts.Modifier
import mindustry.world.Tile
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.Vars
import arc.struct.IntIntMap
import arc.math.Mathf
import arc.util.Log
import mindurka.api.BuildEvent
import mindurka.api.Cancel
import mindurka.api.Consts
import mindurka.api.Priority
import mindurka.api.on
import mindustry.content.Blocks

class Crazy: Modifier() {
    val morphMap = IntIntMap()

    override fun disableAssist(): Boolean = true
    override fun chance() = 0.08f

    override fun start() {
        Vars.state.rules.deconstructRefundMultiplier = 1f
        val free = Vars.content.blocks()
            .select { it !is ConstructBlock }
        Vars.content.blocks()
            .each<Block>({ it !is ConstructBlock && it !in Consts.legacyBlocks }) {
                while (true) {
                    val idx = Mathf.random(0, free.size - 1)
                    val obj = free[idx]
                    if (it.size != obj.size) continue
                    morphMap.put(it.id.toInt(), obj.id.toInt())
                    free.remove(idx);
                    break
                }
            }

        on<BuildEvent>(lifetime = lifetime, priority = Priority.Low) {
        }
    }

    override fun onBuild(event: BuildEvent) {
        val block = Vars.content.block(morphMap.get(event.block().id.toInt())) ?: return;
        if (block.isOverlay) {
            event.replaceAir()
            event.replaceOverlay(block)
            event.tile.setOverlayNet(block)
            event.tile.setNet(Blocks.air)
        } else if (block.isFloor) {
            event.replaceAir()
            event.replaceFloor(block.asFloor())
        } else {
            event.replace(block, event.team(), event.rotation())
        }
    }
}
