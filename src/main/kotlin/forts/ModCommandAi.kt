package forts

import mindurka.util.Ref
import mindustry.ai.UnitCommand
import mindustry.ai.types.CommandAI

class ModCommandAi(val cell: Ref<Boolean>): CommandAI() {
    override fun defaultBehavior() {
        if (!cell.r && (command === UnitCommand.loadBlocksCommand || command === UnitCommand.loadUnitsCommand || command === UnitCommand.unloadPayloadCommand))
            command = UnitCommand.moveCommand
        super.defaultBehavior()
    }

    override fun command(command: UnitCommand) {
        if (!cell.r && (command === UnitCommand.loadBlocksCommand || command === UnitCommand.loadUnitsCommand || command === UnitCommand.unloadPayloadCommand)) return
        super.command(command)
    }
}