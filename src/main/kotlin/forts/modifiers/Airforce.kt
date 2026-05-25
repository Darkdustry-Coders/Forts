package forts.modifiers

import arc.Core
import arc.func.Boolf
import arc.util.Log
import forts.Modifier
import mindurka.api.BuildEvent
import mindurka.api.BuildEventPost
import mindurka.api.Lifetime
import mindurka.api.Priority
import mindurka.api.SpecialSettings
import mindurka.api.interval
import mindurka.api.on
import mindurka.util.newSeq
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.Team
import mindustry.gen.PayloadUnit
import mindustry.gen.Unit
import mindustry.type.Category
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.payloads.BuildPayload
import kotlin.math.min

private val FILTER = Boolf<Unit> { !it.spawnedByCore && it.type.useUnitCap && (!it.type.vulnerableWithPayloads || it !is PayloadUnit || !it.hasPayload()) }

class Airforce: Modifier() {
    override fun chance() = 0.1f

    val extraTurrets = arrayOf(Blocks.buildTower, Blocks.shockwaveTower, Blocks.forceProjector,
        Blocks.regenProjector)

    override fun start() {
        Vars.state.rules.unitPayloadUpdate = true

        val tmp = newSeq<Unit>(150)
        interval(0.2f, lifetime = lifetime) {
            for (team in Team.all) {
                val count = team.data().units.count(FILTER)
                if (count < team.data().unitCap) continue;
                val damage = (count - team.data().unitCap).toFloat().times(2f)
                tmp.addAll(team.data().units)
                tmp.each { if (FILTER[it]) it.damage(damage) }
                tmp.clear()
            }
        }
    }

    override fun onBuild(event: BuildEvent) {
        val tile = event.tile

        if (tile.block().category != Category.turret && tile.block() !in extraTurrets) return

        Core.app.post {
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
                Vars.state.rules.teams[tile.team()].blockHealthMultiplier
            unit.health = event.health() /
                ((Vars.state.rules.unitHealthMultiplier + 1) / 2) /
                ((Vars.state.rules.teams[tile.team()].unitHealthMultiplier + 1) / 2)
            if (unit.health < 2f) unit.health = min(10f, unit.maxHealth)
            Vars.state.rules.unitCap = limit
            Vars.state.rules.unitCapVariable = dyn
            if (unit == null) {
                tile.setNet(Blocks.air)
                return@post
            }
            assert(unit is PayloadUnit)
            unit.apply(StatusEffects.disarmed, Float.POSITIVE_INFINITY)
            unit.apply(StatusEffects.invincible, 60f)
            (unit as PayloadUnit).addPayload(BuildPayload(tile.build))
            tile.setNet(Blocks.air)
        }
    }

    override val disableUnitPayloads: Boolean get() = true
}
