package forts.modifiers

import arc.struct.Seq
import forts.Modifier
import mindurka.api.BuildEventPost
import mindurka.api.Cancel
import mindurka.api.on
import mindurka.util.K
import mindustry.Vars
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.net.Administration
import java.util.WeakHashMap

class March: Modifier() {
    override fun chance(): Float = 0.05f
    override val canBeEnabled = false

    override fun start() {
        val skip = WeakHashMap<Building, K>()

        lifetime.alsoCancel(run {
            val filter: Administration.ActionFilter = filter@{
                if (it.type != Administration.ActionType.breakBlock) return@filter true
                val build = it.tile.build?.takeIf { skip.containsKey(it) } ?: return@filter true
                Call.label(it.player.con, "[scarlet]\uE815", 0.5f, build.x, build.y)
                false
            }
            Vars.netServer.admins.addActionFilter(filter)
            Cancel {
                Vars.netServer.admins.actionFilters.remove(filter)
            }
        })

        on(lifetime = lifetime) { event: BuildEventPost ->
            val build = event.tile.build ?: return@on
            skip[build] = K
        }

        val patchGen = StringBuilder()
        patchGen.append("name:Internal Patch\n")
        patchGen.append("unit:{\n")
        for (unit in Vars.content.units()) {
            // Idk how to tell if a unit can carry payloads.
            patchGen.append(unit.name).append(".payloadCapacity: 1\n")
        }
        patchGen.append("}")

        val patches = Seq<String>(Vars.state.patcher.patches.size + 1)

        for (x in Vars.state.patcher.patches) patches.add(x.patch)
        patches.add(patchGen.toString())
        Vars.state.patcher.apply(patches)
    }
}