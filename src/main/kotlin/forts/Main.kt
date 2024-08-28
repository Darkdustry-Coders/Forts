package forts

import arc.Events
import arc.graphics.Color
import arc.struct.IntIntMap
import arc.struct.IntSeq
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Timer
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.game.EventType.BlockBuildEndEvent
import mindustry.game.EventType.PlayEvent
import mindustry.game.MapObjectives.FlagObjective
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.mod.Plugin
import mindustry.net.Administration
import mindustry.net.Administration.PlayerAction
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.production.Drill
import mindustry.world.blocks.production.Drill.DrillBuild
import kotlin.math.nextUp
import kotlin.math.roundToInt

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
    private val lolNo = "[#ff3066]\uE815"

    private val whitelist = IntSeq()
    private val noAssist = Seq<String>()
    private var allowNoAssist = true
    private var enableAssist = true

    private val gameStage = ObjectMap<Team, GameStage>()

    private fun gameStage(team: Team) = gameStage.get(team) { GameStage.default() }

    override fun init() {
        var game = 0

        Blocks.deconstructor

        Events.on(PlayEvent::class.java) {
            game++

            noAssist.clear()
            whitelist.clear()
            gameStage.clear()

            allowNoAssist = true
            enableAssist = true
            Vars.state.rules.objectives.forEach {
                if (it.typeName() == "flag" && it is FlagObjective && it.flag == "forceassist") {
                    allowNoAssist = false
                }
                if (it.typeName() == "flag" && it is FlagObjective && it.flag == "noassist") {
                    enableAssist = false
                }
            }

            Vars.state.rules.revealedBlocks.addAll(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge, Blocks.basicAssemblerModule)
            Vars.state.rules.bannedBlocks.removeAll(Seq.with(Blocks.impactReactor, Blocks.carbideWall, Blocks.carbideWallLarge, Blocks.basicAssemblerModule))
            Vars.state.rules.tags.put("mindurkaGamemode", "forts")
        }

        Events.on(BlockBuildEndEvent::class.java) {
            if (it.breaking) return@on

            whitelist.removeValue(it.tile.pos())

            val stage = gameStage(it.unit.team)
            it.tile.build?.let {
                if (it !is DrillBuild) return@let

                if (it.dominantItem == Items.thorium) stage.thorium = true
                else if (it.dominantItem == Items.titanium) stage.titanium = true
            }

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

        Vars.netServer.admins.addActionFilter {
            if (it.type != Administration.ActionType.placeBlock) return@addActionFilter true
            if (it.tile.block() != Blocks.air) return@addActionFilter true
            if (noAssist.contains(it.player.uuid())) return@addActionFilter true
            if (whitelist.contains(it.tile.pos())) return@addActionFilter true

            fun cancel() {
                Call.label(it.player.con, lolNo, 1f, it.tile.getX(), it.tile.getY())
            }

            fun hasItem(it: PlayerAction, item: Item, count: Int): Boolean {
                if (it.player.team().core() == null) return false
                return it.player.team().core().items().get(item) >= count
            }

            if (it.block == Blocks.blastDrill) {
                var stage = gameStage(it.player.team())
                if (stage.thorium && stage.titanium) return@addActionFilter true

                val item = occupiedTiles(it.tile, it.block, Seq.with())
                    .map { it.overlay() }
                    .filterNot { it == null || run {
                        val drop = it.itemDrop
                        drop == null || drop.hardness > (Blocks.blastDrill as Drill).tier && drop == (Blocks.blastDrill as Drill).blockedItem
                    } }
                    .fold(IntIntMap()) { map, it ->
                        map.increment(it.itemDrop.id.toInt(), 1, 0)
                        map
                    }
                    .fold(null as IntIntMap.Entry?) { item, entry ->
                        val other = Vars.content.item(entry.key)

                        if (item == null) {
                            return@fold entry
                        }

                        val current = Vars.content.item(item.key)

                        if (current.lowPriority && !other.lowPriority) return@fold entry
                        if (item.value < entry.value) return@fold entry

                        return@fold item
                    }
                    ?.let { Vars.content.item(it.key) }

                if (stage.thorium && item == Items.thorium) {
                    cancel()
                    return@addActionFilter false
                }

                if (stage.titanium && item == Items.titanium) {
                    cancel()
                    return@addActionFilter false
                }

                if (item != Items.thorium && item != Items.titanium) {
                    cancel()
                    return@addActionFilter false
                }

                return@addActionFilter true
            }

            if (it.block == Blocks.router || it.block == Blocks.separator || it.block == Blocks.plastaniumConveyor
                || it.block == Blocks.vault || it.block == Blocks.cultivator) {
                cancel()
                return@addActionFilter false
            }

            if ((it.block == Blocks.conveyor || it.block == Blocks.titaniumConveyor
                        || it.block == Blocks.armoredConveyor) &&
                hasItem(it, Items.beryllium, 2)) {
                cancel()
                return@addActionFilter false
            }

            if ((it.block == Blocks.mechanicalDrill || it.block == Blocks.pneumaticDrill) &&
                it.player.team().core().items().get(Items.thorium) >= 75 &&
                it.player.team().core().items().get(Items.titanium) >= 50) {
                cancel()
                return@addActionFilter false
            }

            val tiles = surroundingTiles(it.tile, it.block, Seq.with())

            if ((it.block == Blocks.phaseWeaver) &&
                tiles.any { b -> b.build is DrillBuild && (b.build as DrillBuild).dominantItem == Items.thorium }) {
                cancel()
                return@addActionFilter false
            }

            if ((it.block == Blocks.plastaniumConveyor) &&
                tiles.any { b -> b.build is DrillBuild && (b.build as DrillBuild).dominantItem == Items.titanium }) {
                cancel()
                return@addActionFilter false
            }

            if ((it.block == Blocks.kiln) &&
                tiles.any { b -> b.build is DrillBuild && (b.build as DrillBuild).dominantItem == Items.lead }) {
                cancel()
                return@addActionFilter false
            }

            if (it.block == Blocks.siliconArcFurnace &&
                tiles.any { b -> b.block() == Blocks.graphitePress || b.block() == Blocks.multiPress }) {
                cancel()
                return@addActionFilter false
            }

            if (it.block == Blocks.surgeSmelter &&
                tiles.any { b -> b.block() == Blocks.siliconArcFurnace ||
                        b.block() == Blocks.siliconCrucible || b.block() == Blocks.siliconSmelter }) {
                cancel()
                return@addActionFilter false
            }

            if ((it.block == Blocks.graphitePress || it.block == Blocks.multiPress) &&
                tiles.any { b -> b.block() == Blocks.siliconArcFurnace }) {
                cancel()
                return@addActionFilter false
            }

            whitelist.add(it.tile.pos())

            true
        }

        Timer.schedule({
            whitelist.clear()
        }, 1f, 1f)
    }

    override fun registerClientCommands(handler: CommandHandler) {
        handler.register<Player>("noassist", "Disable assist mode") { _, player ->
            if (allowNoAssist) noAssist.addUnique(player.uuid())
        }
    }
}