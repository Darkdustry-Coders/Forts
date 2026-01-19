package forts.modifiers

import forts.Modifier
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Lifetime
import mindurka.api.Priority
import mindurka.api.SpecialSettings
import mindurka.api.on
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.gen.PayloadUnit
import mindustry.type.Category
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.payloads.BuildPayload

class Airforce: Modifier() {
    override fun chance() = 0.1f

    init {
        priority = 50
    }

    val extraTurrets = arrayOf(Blocks.buildTower, Blocks.unitRepairTower, Blocks.shockwaveTower, Blocks.forceProjector,
        Blocks.regenProjector)

    override fun start() {
        Vars.state.rules.unitPayloadUpdate = true
    }

    override fun onBuild(event: BuildEvent) {
        val tile = event.tile

        if (tile.block().category != Category.turret && tile.block() !in extraTurrets) return
        val dyn = Vars.state.rules.unitCapVariable
        val limit = Vars.state.rules.unitCap
        Vars.state.rules.unitCapVariable = false
        Vars.state.rules.unitCap = Int.MAX_VALUE
        val uty: UnitType = when (tile.block().size) {
            1 -> UnitTypes.mega
            2 -> UnitTypes.mega
            3 -> UnitTypes.quad
            4 -> UnitTypes.quell
            else -> UnitTypes.disrupt
        }
        val unit = uty.spawn(tile.build.team(), tile.drawx(), tile.drawy())
        unit.maxHealth = tile.block().health.toFloat() *
            Vars.state.rules.blockHealthMultiplier *
            Vars.state.rules.teams.get(tile.team()).blockHealthMultiplier
        unit.health = event.health()
        if (unit.health < 2f) unit.health = 2f
        Vars.state.rules.unitCap = limit
        Vars.state.rules.unitCapVariable = dyn
        if (unit == null) {
            event.replaceAir()
            return
        }
        assert(unit is PayloadUnit)
        unit.apply(StatusEffects.disarmed, 999999f)
        unit.apply(StatusEffects.invincible, 5f)
        (unit as PayloadUnit).addPayload(BuildPayload(tile.build))
        event.replaceAir()
    }
}
