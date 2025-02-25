package forts.modifiers

import forts.Modifier
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

    override fun start() {
        Vars.state.rules.unitPayloadUpdate = true
    }

    override fun blockPlaced(tile: Tile) {
        if (tile.block().category != Category.turret) return
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
        unit.health = tile.build.health()
        if (unit.health < 2f) unit.health = 2f
        Vars.state.rules.unitCap = limit
        Vars.state.rules.unitCapVariable = dyn
        if (unit == null) {
            tile.setNet(Blocks.air)
            return
        }
        assert(unit is PayloadUnit)
        unit.apply(StatusEffects.disarmed, 999999f)
        unit.apply(StatusEffects.invincible, 5f)
        (unit as PayloadUnit).addPayload(BuildPayload(tile.build))
        tile.setNet(Blocks.air)
    }
}