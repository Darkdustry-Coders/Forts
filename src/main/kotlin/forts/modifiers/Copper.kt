package forts.modifiers

import arc.struct.Seq
import forts.Modifier
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.game.Team
import mindustry.type.Item
import mindustry.world.blocks.environment.OreBlock

class Copper: Modifier() {
    override fun chance(): Float = 0.1f

    private fun itemInc(item: Item): Float = when (item) {
        Items.silicon -> 1.2f
        Items.graphite -> 1.2f
        Items.phaseFabric -> 1.7f
        Items.surgeAlloy -> 1.4f
        Items.titanium -> 1.5f
        Items.thorium -> 2f
        Items.carbide -> 3f
        else -> 1f
    }

    override fun start() {
        Vars.world.tiles.eachTile {
            if (it.overlay() is OreBlock) {
                it.setOverlay(Blocks.oreCopper)
            }
        }
        for (team in Team.all) {
            val core = team.core() ?: continue
            var copper = 0f
            core.items.each { item, amount -> copper += amount * itemInc(item) }
            val newCopper = if (copper < Int.MIN_VALUE) Int.MIN_VALUE
                            else if (copper > Int.MAX_VALUE) Int.MAX_VALUE
                            else copper.toInt()
            core.items.clear()
            core.items.set(Items.copper, newCopper)
        }

        val patchGen = StringBuilder()
        patchGen.append("name:Internal Patch\n")
        patchGen.append("block:{\n")
        for (block in Vars.content.blocks()) {
            if (block.requirements.size == 0) continue
            var copper = 0f
            for (stack in block.requirements) copper += stack.amount * itemInc(stack.item)
            val newCopper = if (copper < Int.MIN_VALUE) Int.MIN_VALUE
                            else if (copper > Int.MAX_VALUE) Int.MAX_VALUE
                            else copper.toInt()
            patchGen.append(block.name).append(".requirements:[copper/").append(newCopper).append("]\n")
        }
        patchGen.append("}")

        val patches = Seq<String>(Vars.state.patcher.patches.size + 1)

        for (x in Vars.state.patcher.patches) patches.add(x.patch)
        patches.add(patchGen.toString())
        Vars.state.patcher.apply(patches)
    }

    override val canBeEnabled = false
}