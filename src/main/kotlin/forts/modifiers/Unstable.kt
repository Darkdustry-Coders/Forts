package forts.modifiers

import arc.math.Mathf
import forts.Modifier
import mindurka.api.on
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call

class Unstable: Modifier() {
    override fun chance() = 0.02f

    override fun start() {
        val multiplierUnit = 0.25f
        val multiplierBlock = 0.25f

        on<EventType.UnitDestroyEvent>(lifetime = lifetime) {
            Call.logicExplosion(
                Team.derelict,
                it.unit.tileX().toFloat() * 8f, it.unit.tileY().toFloat() * 8f,
                Mathf.log2(it.unit.type.health) * 4f + 16f,
                it.unit.type.health *
                    Vars.state.rules.unitHealthMultiplier *
                    Vars.state.rules.teams.get(it.unit.team()).unitHealthMultiplier * multiplierUnit,
                true, true, false, true
            )
        }
        on<EventType.BlockDestroyEvent>(lifetime = lifetime) {
            Call.logicExplosion(
                Team.derelict,
                it.tile.x.toFloat() * 8f, it.tile.y.toFloat() * 8f,
                Mathf.log2(it.tile.block().health).toFloat() * 4f + 16f,
                it.tile.block().health.toFloat() *
                        Vars.state.rules.blockHealthMultiplier *
                        Vars.state.rules.teams.get(it.tile.team()).blockHealthMultiplier * multiplierBlock,
                true, true, false, true
            )
        }
    }
}
