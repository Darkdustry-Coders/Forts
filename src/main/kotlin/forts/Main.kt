package forts

import arc.Events
import arc.graphics.Color
import arc.struct.Seq
import arc.util.Timer
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.EventType.BlockBuildEndEvent
import mindustry.game.EventType.PlayEvent
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.mod.Plugin

class Main: Plugin() {
    override fun init() {
        var game = 0

        Blocks.deconstructor

        Events.on(PlayEvent::class.java) {
            game++

            Vars.state.rules.revealedBlocks.addAll(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge)
            Vars.state.rules.bannedBlocks.removeAll(Seq.with(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge))
            Vars.state.rules.tags.put("mindurkaGamemode", "forts")
        }

        Events.on(BlockBuildEndEvent::class.java) {
            if (it.breaking) return@on
            if (it.tile.block() != Blocks.impactReactor) return@on

            val currentGame = game

            Timer.schedule({
                if (currentGame != game) return@schedule
                if (it.tile.block() != Blocks.impactReactor) return@schedule

                it.tile.setNet(Blocks.largeShieldProjector, it.tile.team(), 0)
                Call.effect(
                    Fx.spawn,
                    it.tile.getX(), it.tile.getY(), 0f,
                    Color.yellow
                )

                val currentGame = game

                Timer.schedule({
                    if (currentGame != game) return@schedule
                    if (it.tile.block() != Blocks.largeShieldProjector) return@schedule
                    it.tile.setNet(Blocks.air)

                    Call.logicExplosion(
                        Team.derelict,
                        it.tile.getX(), it.tile.getY(),
                        40.0f, 500.0f,
                        true, true, true
                    )
                    Call.effect(
                        Fx.spawn,
                        it.tile.getX(), it.tile.getY(), 0f,
                        Color.yellow
                    )
                }, 1.7f)
            }, 0.1f)
        }

        CustomDestructor.load()
    }
}