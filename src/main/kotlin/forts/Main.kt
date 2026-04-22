package forts

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.math.Mathf
import arc.struct.ByteSeq
import arc.struct.IntIntMap
import arc.struct.IntMap
import arc.struct.IntSeq
import arc.struct.IntSet
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Time
import arc.util.io.Streams
import buj.tl.Tl
import kotlinx.coroutines.future.await
import mindurka.api.BuildEvent
import mindurka.api.Cancel
import mindurka.api.Gamemode
import mindurka.api.Lifetime
import mindurka.api.Priority
import mindurka.api.SpecialSettingsLoad
import mindurka.api.interval
import mindurka.api.on
import mindurka.api.sleep
import mindurka.api.timer
import mindurka.coreplugin.CorePlugin
import mindurka.util.Async
import mindurka.util.ModifyWorld
import mindurka.util.Ops
import mindurka.util.canActuallyPlaceOn
import mindurka.util.prefixed
import mindustry.Vars
import mindustry.ai.types.CommandAI
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.content.Liquids
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.game.Teams
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.PayloadUnit
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry.gen.WorldLabel
import mindustry.logic.LExecutor
import mindustry.mod.Plugin
import mindustry.net.Administration
import mindustry.type.Category
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.defense.BaseShield
import mindustry.world.blocks.environment.Prop
import mindustry.world.blocks.payloads.BuildPayload
import mindustry.world.blocks.storage.CoreBlock
import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.nextUp
import kotlin.math.roundToInt
import kotlin.math.sin

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

fun blockTileWorth(block: Block) = when (block) {
    Blocks.air -> 0
    is ConstructBlock -> 0
    is Prop -> 0
    else if (block.isFloor || block.isOverlay) -> 0 /* I have no idea what do the brackets here clarify, but at least IDEA shuts up. */
    else -> 1
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
    private var destroyCoreLock = false

    companion object {
        private val carbideWallsCatapult = ObjectMap<Building, Seq<Building>>();
        @JvmStatic
        fun carbideWallsCatapult(deconstructor: Building, other: Building) {
            var buildings: Seq<Building>? = carbideWallsCatapult[deconstructor]
            if (buildings != null) {
                buildings.addUnique(other)
                return
            }

            buildings = Seq.with(other)
            carbideWallsCatapult.put(deconstructor, buildings)
            val victims: Seq<Building> = buildings

            Core.app.post {
                carbideWallsCatapult.remove(deconstructor)
                if (deconstructor.dead) {
                    return@post
                }

                those@for (victim in victims) {
                    if (victim.dead) {
                        continue
                    }
                    if (victim.payload == null) {
                        continue
                    }
                    if (victim.payload !is BuildPayload) {
                        continue
                    }
                    if ((victim.payload as BuildPayload).block() != Blocks.carbideWall && (victim.payload as BuildPayload).block() != Blocks.carbideWallLarge) {
                        continue
                    }

                    val build = (victim.takePayload() as BuildPayload).build

                    var counter = 0

                    for (dx in -10..9) a@ for (dy in -10..9) {
                        val x: Int = deconstructor.tileX() + dx
                        val y: Int = deconstructor.tileY() + dy

                        if (x < 0 || y < 0 || x + 1 >= Vars.world.width() || y + 1 >= Vars.world.height()) continue

                        for (ddx in 0..1) for (ddy in 0..1) {
                            val tile = Vars.world.tile(x + ddx, y + ddy)
                            if (!build.block.canActuallyPlaceOn(tile, build.team, 0)) {
                                continue@a
                            }
                        }

                        counter++
                    }

                    if (counter == 0) {
                        val tile: Tile = deconstructor.tile
                        tile.setNet(Blocks.air)
                        Call.effect(Fx.titanSmokeLarge, deconstructor.getX(), deconstructor.getY(), 0f, Color.purple);
                        Call.effect(Fx.titanSmokeLarge, deconstructor.getX(), deconstructor.getY(), 0f, Color.purple);
                        Call.effect(Fx.titanSmokeLarge, deconstructor.getX(), deconstructor.getY(), 0f, Color.purple);
                        Call.effect(Fx.scatheExplosion, deconstructor.getX(), deconstructor.getY(), 0f, Color.purple);
                        Call.logicExplosion(
                            Team.derelict, (tile.x * Vars.tilesize).toFloat(), (tile.y * Vars.tilesize).toFloat(), 80f, 4000f,
                            true, true, false, true
                        )
                        return@post
                    }

                    counter = Mathf.random(counter - 1)

                    for (dx in -10..9) a@ for (dy in -10..9) {
                        val x: Int = deconstructor.tileX() + dx
                        val y: Int = deconstructor.tileY() + dy

                        if (x < 0 || y < 0 || x + 1 >= Vars.world.width() || y + 1 >= Vars.world.height()) continue

                        for (ddx in 0..1) for (ddy in 0..1) {
                            val tile = Vars.world.tile(x + ddx, y + ddy)
                            if (!build.block.canActuallyPlaceOn(tile, build.team, 0)) {
                                continue@a
                            }
                        }

                        if (counter-- == 0) {
                            val tile = Vars.world.tile(x, y)
                            tile.setBlock(build.block,
                                Team.derelict,
                                build.rotation
                            ) { build }
                            ModifyWorld.netBlock(tile, build.block, build.team, build.rotation)
                            ModifyWorld.syncBuild(victim)
                            Call.effect(Fx.unitCapKill, build.getX(), build.getY(), 0f, Color.red);
                            continue@those
                        }
                    }

                    throw RuntimeException("Reached unreachable!")
                }
            }

            ModifyWorld.syncBuild(deconstructor)
        }

        fun filterTeamPlans(team: Team) {
            team.data().plans.retainAll { FortsRules.now.plots.canPlaceBlock(team, it.block, it.x.toInt(), it.y.toInt()) }
        }
    }

    private var epoch = 0L
    private var thorLastAt = IntIntMap()  // We are NOT playing for long enough for it to break. Just no.
    private var impactLastAt = IntIntMap()
    private var neoplasiaLastAt = IntIntMap()
    private val mainCores = IntMap<CoreBlock.CoreBuild>()
    private val helpLabels = IntMap<UnsyncLabel>()
    private val helpLabelsPlayerTimers = ObjectMap<Player, Cancel>()

    private var loading = true

    // Otherwise this crashes
    private var builtInContentPatch: String = ""

    private fun epochMillis(): Int {
        if (epoch == 0L) epoch = Time.millis()
        return (Time.millis() - epoch).toInt()
    }

    private fun fillBuild(build: Building) {
        if (build.block == Blocks.siliconCrucible) { if (build.items != null) build.items.set(Items.coal, Blocks.siliconCrucible.itemCapacity) }
        else if (build.block == Blocks.siliconSmelter) { if (build.items != null) build.items.set(Items.coal, Blocks.siliconSmelter.itemCapacity) }
        else if (build.block == Blocks.multiPress) { if (build.items != null) build.items.set(Items.coal, Blocks.multiPress.itemCapacity) }
        else if (build.block == Blocks.plastaniumCompressor) { if (build.liquids != null) build.liquids.set(Liquids.oil, Blocks.plastaniumCompressor.liquidCapacity) }
        else if (build.block == Blocks.shockwaveTower) { if (build.liquids != null) build.liquids.set(Liquids.cyanogen, Blocks.shockwaveTower.liquidCapacity) }
        else if (build.block == Blocks.shipAssembler) { if (build.liquids != null) build.liquids.set(Liquids.cyanogen, Blocks.shipAssembler.liquidCapacity) }
        else if (build.block == Blocks.tankAssembler) { if (build.liquids != null) build.liquids.set(Liquids.cyanogen, Blocks.tankAssembler.liquidCapacity) }
        else if (build.block == Blocks.mechAssembler) { if (build.liquids != null) build.liquids.set(Liquids.cyanogen, Blocks.mechAssembler.liquidCapacity) }
        else if (build.block == Blocks.unitRepairTower) { if (build.liquids != null) build.liquids.set(Liquids.ozone, Blocks.unitRepairTower.liquidCapacity) }
    }

    override fun init() {
        val loader = javaClass.classLoader.prefixed("forts")
        CorePlugin.init(loader)

        builtInContentPatch = Streams.copyString(loader.getResourceAsStream("patch.hjson"))

        initModifiers()

        Gamemode.defaultPatch = { builtInContentPatch }

        Vars.content.units().each {
            val old = it.controller
            it.controller = { unit ->
                val controller = old[unit]
                if (controller is CommandAI && controller !is ModCommandAi) ModCommandAi(enableUnitPayloads)
                else controller
            }
        }

        on<SpecialSettingsLoad> { if (it.currentMap) FortsRules.now = FortsRules(it.rc) else FortsRules(it.rc) }
        on<EventType.GameOverEvent> { gameOver() }
        on<EventType.WorldLoadBeginEvent>(priority = Priority.Lowest) {
            loading = true
        }
        on<EventType.PlayEvent>(priority = Priority.Highest) {
            thorLastAt.clear()
            impactLastAt.clear()
            neoplasiaLastAt.clear()
            epoch = 0L
            Vars.state.rules.waveTeam = Team.derelict

            mainCores.clear()
            helpLabels.clear()
            helpLabelsPlayerTimers.clear()
            for (team in Team.all) {
                if (team == Team.derelict) continue
                val core = team.core() ?: continue
                mainCores.put(team.id, core)
                val label = UnsyncLabel()
                label.textGen = { player ->
                    Tl.fmt(player)
                        .apply { FortsRules.now.expansionBlock.emoji()?.let { put("icon", it) } }
                        .done("{forts.notice.help}")
                }
                label.x = core.x
                label.y = core.y
                label.flags = Ops.bitOr(WorldLabel.flagBackground, WorldLabel.flagOutline)
                label.add()
                helpLabels.put(team.id, label)
            }

            val color = Color()
            var part = 0
            interval(0.07f, lifetime = Lifetime.Round) {
                if (Vars.state.isPaused) return@interval
                part = (part + 1) % 32
                val angle = Mathf.PI2 / 32 * part
                val dx = cos(angle) * Vars.tilesize * 3
                val dy = sin(angle) * Vars.tilesize * 3
                mainCores.values().forEach { core ->
                    color.set(core.team.color)
                    color.set(
                        min(color.r * 1.4f, 1f),
                        min(color.g * 1.4f, 1f),
                        min(color.b * 1.4f, 1f),
                    )
                    Call.effect(Fx.colorTrail, core.x + dx, core.y + dy, 2f, color)
                    Call.effect(Fx.colorTrail, core.x - dx, core.y - dy, 2f, color)
                }
            }

            loading = false
        }
        on<EventType.PlayerConnectionConfirmed> {
            helpLabelsPlayerTimers[it.player]?.cancel()
            helpLabels.values().forEach { label -> label.syncFor(it.player) }
            helpLabelsPlayerTimers.put(it.player, timer(120f) {
                helpLabels.values().forEach { label -> label.removeFor(it.player) }
            })
        }
        on<EventType.PlayerLeave> {
            helpLabelsPlayerTimers[it.player]?.cancel()
        }
        on<BuildEvent>(priority = Priority.Low, listener = ::onBuild)
        on<BuildEvent>(priority = Priority.After) {
            val tile = it.tile
            val build = WeakReference(it.tile.build)
            val team = it.team()
            when (val block = it.block()) {
                FortsRules.now.expansionBlock if FortsRules.now.plots.considerExpansionBlock() -> Core.app.post {
                    FortsRules.now.plots.placeExpansionBlock(tile.x.toInt(), tile.y.toInt(), team) {
                        tile.setNet(Blocks.air, team, 0)
                    }
                }
                FortsRules.now.thorBlock if FortsRules.now.thorEnabled -> Async.run {
                    val now = epochMillis()
                    val lastThorAt = this.thorLastAt.get(team.id, 0)
                    this.thorLastAt.put(team.id, max(now, lastThorAt) + (FortsRules.now.thorCooldown * 1000f).toInt())

                    val col1 = Color(0x26122fU.toInt())
                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.smokeCloud, build.get()!!.getX(), build.get()!!.getY(), 250f, col1)
                    }

                    sleep((max(lastThorAt, now) - now) / 1000f + FortsRules.now.thorDelay, lifetime = Lifetime.Round).await()
                    if (build.get() == null || build.get()!!.dead || build.get()!!.tile !== tile || tile.build !== build.get()) return@run

                    tile.setNet(Blocks.air)

                    val b = build.get()!!

                    val col2 = Color(0xd176ffffU.toInt())
                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.dynamicSpikes, b.getX(), b.getY(), 250f, col2)
                    }

                    val rm = FortsRules.now.thorRadiusMultiplier
                    val dm = FortsRules.now.thorDamageMultiplier
                    Call.logicExplosion(team, b.getX(), b.getY(), 32f * rm * Vars.tilesize, 120f * dm, true, true, true, true)
                    Call.logicExplosion(team, b.getX(), b.getY(), 22f * rm * Vars.tilesize, 120f * dm, true, true, false, true)
                    Call.logicExplosion(team, b.getX(), b.getY(), 16f * rm * Vars.tilesize, 120f * dm, true, true, false, true)
                    Call.logicExplosion(team, b.getX(), b.getY(), 16f * rm * Vars.tilesize, 120f * dm, true, true, false, true)
                }
                FortsRules.now.impactBlock if FortsRules.now.impactEnabled -> Async.run {
                    val now = epochMillis()
                    val lastImpactAt = this.impactLastAt.get(team.id, 0)
                    this.impactLastAt.put(team.id, max(now, lastImpactAt) + (FortsRules.now.impactCooldown * 1000f).toInt())

                    val col1 = Color(0x26122fU.toInt())
                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.smokeCloud, build.get()!!.getX(), build.get()!!.getY(), 250f, col1)
                    }

                    sleep((max(lastImpactAt, now) - now) / 1000f + FortsRules.now.impactDelay, lifetime = Lifetime.Round).await()
                    if (build.get() == null || build.get()!!.dead || build.get()!!.tile !== tile || tile.build !== build.get()) return@run

                    tile.setNet(Blocks.largeShieldProjector, team, 0)
                    if (FortsRules.now.impactInstakill) Groups.unit.each { unit ->
                        if (unit.dst(tile.build) > (Blocks.largeShieldProjector as BaseShield).radius) return@each
                        unit.kill()
                    }

                    val b = build.get()!!
                    val ex = b.getX()
                    val ey = b.getY()

                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.spawn, ex, ey, 0f, Color.yellow)
                    }

                    val shieldBuild = WeakReference(tile.build)

                    sleep(FortsRules.now.impactDuration, lifetime = Lifetime.Round).await()
                    if (shieldBuild.get() == null || shieldBuild.get()!!.dead() || shieldBuild.get()!!.tile !== tile || tile.build !== shieldBuild.get()!!) return@run

                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.spawn, ex, ey, 0f, Color.yellow)
                    }

                    tile.setNet(Blocks.air)
                    Log.info(FortsRules.now.impactExplosionRadius)
                    Call.logicExplosion(Team.derelict, ex, ey, FortsRules.now.impactExplosionRadius, FortsRules.now.impactExplosionDamage, true, true, true, true)
                }
                FortsRules.now.neoplasiaBlock if FortsRules.now.neoplasiaEnabled -> Async.run {
                    val now = epochMillis()
                    val lastNeoplasiaAt = this.neoplasiaLastAt.get(team.id, 0)
                    this.neoplasiaLastAt.put(team.id, max(now, lastNeoplasiaAt) + (FortsRules.now.neoplasiaCooldown * 1000f).toInt())

                    val col1 = Color(0x26122fU.toInt())
                    Groups.player.each { player ->
                        Call.effect(player.con, Fx.smokeCloud, build.get()!!.getX(), build.get()!!.getY(), 250f, col1)
                    }

                    sleep((max(lastNeoplasiaAt, now) - now) / 1000f + FortsRules.now.neoplasiaDelay, lifetime = Lifetime.Round).await()
                    val build = build.get()
                    if (build == null || build.dead || build.tile !== tile || tile.build !== build) return@run

                    val rotation = ((build.rotation % 4) + 4) % 4
                    val dx = when (rotation) { 0 -> 1; 2 -> -1; else -> 0 }
                    val dy = when (rotation) { 1 -> 1; 3 -> -1; else -> 0 }
                    val size = block.size
                    val shift = 1 + (block.size - 1) / 2

                    val cancel = arrayOf<Cancel?>(null)
                    cancel[0] = interval(1 / FortsRules.now.neoplasiaProgressSpeed, lifetime = Lifetime.Round, run = object : Runnable {
                        var x = build.tileX() + size / 2 * dx
                        var y = build.tileY() + size / 2 * dy
                        var remaining = FortsRules.now.neoplasiaLength

                        override fun run() {
                            if (remaining-- <= 0) {
                                cancel[0]!!.cancel()
                                return
                            }

                            x += dx
                            y += dy

                            for (d in 0..<block.size + 2) {
                                val xx = (x + if (rotation % 2 == 1) d - shift else 0) * Vars.tilesize.toFloat()
                                val yy = (y + if (rotation % 2 == 0) d - shift else 0) * Vars.tilesize.toFloat()
                                for (player in Groups.player) {
                                    Call.effect(player.con, Fx.explosion, xx, yy, 0f, Color.yellow)
                                }
                                LExecutor.logicExplosion(team, xx, yy, Vars.tilesize.toFloat() / 2, FortsRules.now.neoplasiaDamage, false, true, true, false)
                            }
                        }
                    })

                    tile.setNet(Blocks.air)
                }
                else -> Core.app.post {
                    if (it.tile.build != null) fillBuild(it.tile.build)
                }
            }
        }

        on<EventType.BlockDestroyEvent> { event ->
            if (loading) return@on

            val x = event.tile.build?.tileX() ?: event.tile.x.toInt()
            val y = event.tile.build?.tileY() ?: event.tile.y.toInt()

            FortsRules.now.plots.handleBlockBreak(x, y, event.tile.team())

            val team = event.tile.team().id
            mainCores[team]?.let { core ->
                if (destroyCoreLock) return@let
                if (event.tile.build !== core) return@let
                destroyCoreLock = true
                val cores = event.tile.team().cores().copy()
                while (!cores.isEmpty) {
                    val target = cores.pop()
                    if (target.dead) continue
                    target.kill()
                    Call.logicExplosion(Team.derelict,
                        target.x, target.y,
                        64f, 10000f,
                        true, true, true, true)
                }
                mainCores.remove(team)
                helpLabels.remove(team)?.remove()
                destroyCoreLock = false
            }
        }
        on<EventType.BlockBuildBeginEvent> { event ->
            if (loading) return@on
            if (!event.breaking) return@on

            FortsRules.now.plots.handleBlockBreak(event.tile.x.toInt(), event.tile.y.toInt(), event.team)
        }
        on<EventType.BlockBuildEndEvent> { event ->
            if (loading) return@on
            if (!event.breaking) return@on

            FortsRules.now.plots.handleBlockBreak(event.tile.x.toInt(), event.tile.y.toInt(), event.team)
        }

        fun reevalUnitEffects(unit: Unit) {
            if (!FortsRules.now.applyDamageEffects(unit.team())) return
            if (unit.spawnedByCore) {
                if (unit !is PayloadUnit) return
                if (unit.payloads.size == 0) return
                unit.apply(StatusEffects.slow, 2f * 60)
                if (unit.health > 350) unit.health = 340f
                if (!unit.payloads.any { it is BuildPayload && it.block().category === Category.turret }) return
                unit.apply(StatusEffects.burning, 2f * 60)
                unit.apply(StatusEffects.melting, 2f * 60)
                unit.apply(StatusEffects.corroded, 2f * 60)
                unit.apply(StatusEffects.sapped, 2f * 60)
                unit.apply(StatusEffects.slow, 2f * 60)
                unit.apply(StatusEffects.overclock, 2f * 60)
                if (unit.health > 200) unit.health = 190f
            } else {
                if (unit.type.vulnerableWithPayloads && (unit !is PayloadUnit || unit.hasPayload())) return

                unit.apply(StatusEffects.shielded, 9999f)
                unit.apply(StatusEffects.burning, 9999f)
                if (unit.health > 100) unit.apply(StatusEffects.melting, 9999f)
                if (unit.health > 2000) unit.apply(StatusEffects.corroded, 9999f)
            }
        }
        interval(0.25f) {
            Groups.unit.each(::reevalUnitEffects)
        }
        interval(5f) {
            val activeTeams = ByteSeq()
            for (team in Team.all) {
                if (team == Team.derelict) continue
                if (team.core() != null) activeTeams.add(team.id.toByte())
            }
            for (item in Vars.content.items()) {
                val max = FortsRules.now.passiveItems(item)
                if (max <= 0) continue
                for (i in 0..<activeTeams.size) {
                    val team = Team.all[activeTeams[i].toUByte().toInt()]
                    team.core()?.let { core ->
                        if (core.items[item] > 50) return@let
                        core.items[item] = min(core.items[item] + 10, max)
                    }
                }
            }
        }
        on<EventType.PickupEvent> { reevalUnitEffects(it.carrier) }

        ClassPatches.load()

        Vars.netServer.admins.addActionFilter { act ->
            if (act.type != Administration.ActionType.placeBlock) return@addActionFilter true

            if (!FortsRules.now.plots.canPlaceBlock(act.player.team(), act.block, act.tile.x.toInt(), act.tile.y.toInt())) {
                val extra = act.block.size.toFloat() * Vars.tilesize / 2
                Call.effect(act.player.con, Fx.breakBlock, act.tile.getX() + extra, act.tile.getY() + extra, act.block.size.toFloat(), Color.red)

                false
            } else true
        }

        interval(1f) {
            Groups.build.each(::fillBuild)
        }
    }

    override fun registerServerCommands(handler: CommandHandler) {
        handler.register("modifiers", "Show currently active modifiers") {
            Log.info("Active modifiers:")
            if (activeModifiers().isEmpty) Log.info("<none>")
            activeModifiers().each { Log.info("- ${it.getName()}") }
        }

        handler.register("modifier-add", "<modifiers...>", "Add modifiers") {
            val target = it[0]!!.split(' ')
            val available = availableModifiers
                .filter { !activeModifiers().any { other -> other.javaClass == it.source.java } }
                .filter { target.any { name -> name == it.source.java.simpleName.lowercase()} }
            available.forEach { addModifier(it.create.get()) }
        }

        handler.register("modifier-rem", "<modifiers...>", "Remove modifiers") {
            val target = it[0]!!.split(' ')
            removeModifiers { mod -> target.any { it == mod.getName() } }
        }
    }
}
