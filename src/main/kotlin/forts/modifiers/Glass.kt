package forts.modifiers

import forts.Modifier
import mindurka.api.Cancel
import mindurka.api.interval
import mindurka.api.on
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.gen.PayloadUnit

class Glass: Modifier() {
    override fun chance() = 0.03f

    override fun start() {
        Vars.state.rules.unitDamageMultiplier *= 4f;
        Vars.state.rules.unitCrashDamageMultiplier *= 4f;

        interval(0.2f, lifetime = lifetime) {
            Groups.unit.each {
                if (it.isPlayer && !(it is PayloadUnit && !it.payloads.isEmpty)) return@each
                if (it.health() > 10f) it.health(10f)
            }
        }

        on<EventType.UnitSpawnEvent>(lifetime = lifetime) {
            if (it.unit.isPlayer && !(it.unit is PayloadUnit && !(it.unit as PayloadUnit).payloads.isEmpty)) return@on
            it.unit.health(10f)
        }

        lifetime.bind(Cancel {
            Vars.state.rules.unitDamageMultiplier /= 4f;
            Vars.state.rules.unitCrashDamageMultiplier /= 4f;
        })
    }
}
