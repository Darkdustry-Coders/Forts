package forts

import arc.Events
import arc.graphics.Color
import arc.math.Mathf
import arc.struct.IntIntMap
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Timer
import buj.tl.Tl
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.game.EventType.BlockBuildBeginEvent
import mindustry.game.EventType.BlockBuildEndEvent
import mindustry.game.EventType.PlayEvent
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.graphics.CacheLayer
import mindustry.mod.Plugin
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.production.Drill.DrillBuild
import mindustry.world.blocks.ConstructBlock
import kotlin.math.nextUp
import kotlin.math.roundToInt
import forts.mapBlockModifiers
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.type.Item
import mindustry.world.blocks.defense.turrets.ItemTurret

fun surroundingTiles(tile: Tile, block: Block, collect: Seq<Tile>): Seq<Tile> {
    collect.clear()

    val x = (tile.x - block.size.toFloat().plus(.5f).div(2f).nextUp()).roundToInt()
    val y = (tile.y - block.size.toFloat().plus(.5f).div(2f).nextUp()).roundToInt()

    (1..block.size).forEach { xx ->
        var tile = Vars.world.tile(xx + x, y)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(xx + x, y + block.size + 1)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(x, xx + y)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(x + block.size + 1, xx + y)
        if (tile != null) collect.add(tile)
    }

    return collect
}

fun surroundingTiles(build: Building, collect: Seq<Tile>): Seq<Tile> {
    collect.clear()

    val x = (build.x - build.block.size.toFloat() / 2f).roundToInt() - 1
    val y = (build.y - build.block.size.toFloat() / 2f).roundToInt() - 1

    (1..build.block.size).forEach { xx ->
        var tile = Vars.world.tile(xx + x, y + 1)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(xx + x, y + build.block.size + 1)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(x + 1, xx + y)
        if (tile != null) collect.add(tile)
        tile = Vars.world.tile(x + build.block.size + 1, xx + y)
        if (tile != null) collect.add(tile)
    }

    return collect
}

fun surroundingTiles(tile: Tile, collect: Seq<Tile>): Seq<Tile> =
    if (tile.build == null) collect.clear()
    else surroundingTiles(tile.build, collect)

fun occupiedTiles(tile: Tile, block: Block, collect: Seq<Tile>): Seq<Tile> {
    collect.clear()

    val x = (tile.x - block.size.toFloat().plus(.5f).div(2f).nextUp()).roundToInt()
    val y = (tile.y - block.size.toFloat().plus(.5f).div(2f).nextUp()).roundToInt()

    (1..block.size).forEach { xx ->
        (1..block.size).forEach { yy ->
            val tile = Vars.world.tile(xx + x, yy + y)
            if (tile != null) collect.add(tile)
        }
    }

    return collect
}

data class GameStage(
    var thorium: Boolean,
    var titanium: Boolean,
) {
    companion object {
        fun default() = GameStage(thorium = false, titanium = false)
    }
}

class Main: Plugin() {
    override fun init() {
        var game = 0

        initModifiers()
        Tl.init(javaClass.classLoader)

        Events.on(PlayEvent::class.java) {
            game++

            Vars.state.rules.revealedBlocks.addAll(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge, Blocks.basicAssemblerModule)
            Vars.state.rules.bannedBlocks.removeAll(Seq.with(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge, Blocks.basicAssemblerModule))
            Vars.state.rules.tags.put("mindurkaGamemode", "forts")
        }

        Events.on(BlockBuildEndEvent::class.java) {
            if (it.breaking) return@on

            val rot = if (it.tile.block().hasBuilding()) { it.tile.build.rotation } else { 0 }
            var block: Block? = it.tile.block()

            block = mapBlockModifiers(it.tile, block!!)

            if (block != Blocks.impactReactor) {
                if (it.tile.block() != block) {
                    Timer.schedule({
                        it.tile.setNet(block ?: Blocks.air, it.tile.team(), rot)
                        blockPlacedModifiers(it.tile)
                    }, 0.1f)
                }
                else {
                    Timer.schedule({
                        blockPlacedModifiers(it.tile)
                    }, 0.1f)
                }
                return@on
            }

            val currentGame = game

            Timer.schedule({
                if (currentGame != game) return@schedule
                if (it.tile.block() != Blocks.impactReactor) return@schedule

                it.tile.setNet(Blocks.largeShieldProjector, it.tile.team(), rot)
                blockPlacedModifiers(it.tile)
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

        Events.on(EventType.GameOverEvent::class.java) { gameOver() }

        CustomDestructor.load()
        UnitTypes.poly.health = 90f
        UnitTypes.flare.health = 150f
        (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.metaglass).splashDamage = 65f
        (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.blastCompound).splashDamage = 100f
        (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.plastanium).splashDamage = 95f
        (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.surgeAlloy).splashDamage = 125f
        (Blocks.titan as ItemTurret).ammoTypes.get(Items.thorium).buildingDamageMultiplier = 0.4f
        (Blocks.titan as ItemTurret).ammoTypes.get(Items.thorium).splashDamage = 180f
        (Blocks.titan as ItemTurret).ammoTypes.get(Items.thorium).splashDamagePierce = true
    }

    override fun registerServerCommands(handler: CommandHandler) {
        handler.register("modifiers", "Show currently active modifiers") {
            Log.info("Active modifiers:")
            if (activeModifiers().isEmpty) Log.info("<none>")
            activeModifiers().each { Log.info("- ${it.getName()}") }
        }

        handler.register("modifier-add", "<modifiers...>", "Add modifiers") {
            val target = it[0]!!.split(' ')
            val available = availableModifiers()
                .select { !activeModifiers().any { other -> other.javaClass == it.source.java } }
                .select { target.any { name -> name == it.source.java.simpleName.lowercase()} }
            available.each { addModifier(it.create.get()) }
        }

        handler.register("modifier-rem", "<modifiers...>", "Remove modifiers") {
            val target = it[0]!!.split(' ')
            removeModifiers { mod -> target.any { it == mod.getName() } }
        }
    }
}
