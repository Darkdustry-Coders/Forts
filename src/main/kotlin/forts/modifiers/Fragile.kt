package forts.modifiers

import forts.Modifier
import mindustry.Vars
import mindurka.api.Cancel

class Fragile: Modifier() {
    override fun chance() = 0.2f
    override fun start() {
        Vars.state.rules.unitHealthMultiplier /= 2f
        Vars.state.rules.blockHealthMultiplier /= 2f

        lifetime.alsoCancel(Cancel {
            Vars.state.rules.unitHealthMultiplier *= 2f
            Vars.state.rules.blockHealthMultiplier *= 2f
        })
    }
}
