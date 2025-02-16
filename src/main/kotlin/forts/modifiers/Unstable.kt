package forts.modifiers

import arc.math.Mathf
import forts.Modifier
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call

class Unstable: Modifier() {
    override fun chance() = 0.02f

    override fun start() {
        registerEvent<EventType.BlockDestroyEvent> {
            Call.logicExplosion(
                Team.derelict,
                it.tile.x.toFloat() * 8f, it.tile.y.toFloat() * 8f,
                Mathf.log2(it.tile.block().health).toFloat() * 4f + 16f,
                it.tile.block().health.toFloat() *
                        Vars.state.rules.blockHealthMultiplier *
                        Vars.state.rules.teams.get(it.tile.team()).blockHealthMultiplier / 2f,
                true, true, false
            )
        }
        registerEvent<EventType.UnitDestroyEvent> {
            Call.logicExplosion(
                Team.derelict,
                it.unit.tileX().toFloat() * 8f, it.unit.tileY().toFloat() * 8f,
                Mathf.log2(it.unit.type.health).toFloat() * 4f + 16f,
                it.unit.type.health.toFloat() *
                        Vars.state.rules.blockHealthMultiplier *
                        Vars.state.rules.teams.get(it.unit.team()).blockHealthMultiplier / 2f,
                true, true, false
            )
        }
    }
}