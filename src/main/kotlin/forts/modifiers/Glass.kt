package forts.modifiers

import forts.Modifier
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.gen.PayloadUnit

class Glass: Modifier() {
    override fun chance() = 0.03f

    override fun start() {
        Vars.state.rules.unitDamageMultiplier *= 4f;
        Vars.state.rules.unitCrashDamageMultiplier *= 4f;

        runEvery(0.2f) {
            Groups.unit.each {
                if (it.isPlayer && !(it is PayloadUnit && !it.payloads.isEmpty)) return@each
                if (it.health() > 10f) it.health(10f)
            }
        }

        registerEvent<EventType.UnitSpawnEvent> {
            if (it.unit.isPlayer && !(it.unit is PayloadUnit && !(it.unit as PayloadUnit).payloads.isEmpty)) return@registerEvent
            it.unit.health(10f)
        }
    }
}