package forts

import arc.Core
import arc.graphics.Color
import arc.struct.IntIntMap
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Time
import kotlinx.coroutines.future.await
import mindurka.api.BuildEvent
import mindurka.api.Cancel
import mindurka.api.Lifetime
import mindurka.api.Priority
import mindurka.api.SpecialSettingsLoad
import mindurka.api.interval
import mindustry.Vars
import mindustry.gen.Building
import mindustry.mod.Plugin
import mindustry.world.Block
import mindustry.world.Tile
import kotlin.math.nextUp
import kotlin.math.roundToInt
import mindustry.game.EventType
import mindurka.api.on
import mindurka.api.sleep
import mindurka.coreplugin.CorePlugin
import mindurka.util.Async
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.StatusEffects
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.PayloadUnit
import mindustry.gen.Unit
import mindustry.type.Category
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.defense.BaseShield
import mindustry.world.blocks.environment.Prop
import mindustry.world.blocks.payloads.BuildPayload
import java.lang.ref.WeakReference
import kotlin.math.max

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
    private var epoch = 0L
    private var thorLastAt = IntIntMap()  // We are NOT playing for long enough for it to break. Just no.
    private var impactLastAt = IntIntMap()
    private var neoplasiaLastAt = IntIntMap()

    private var loading = true

    private fun epochMillis(): Int {
        if (epoch == 0L) epoch = Time.millis()
        return (Time.millis() - epoch).toInt()
    }

    override fun init() {
        CorePlugin.init(javaClass)

        initModifiers()

        on<SpecialSettingsLoad> { if (it.currentMap) FortsRules.now = FortsRules(it.rc) else FortsRules(it.rc) }
        on<EventType.GameOverEvent> { gameOver() }
        on<EventType.WorldLoadBeginEvent>(priority = Priority.Lowest) {
            loading = true
        }
        on<EventType.PlayEvent> {
            loading = false
            thorLastAt.clear()
            epoch = 0L
        }
        on<BuildEvent>(priority = Priority.Low, listener = ::onBuild)
        on<BuildEvent>(priority = Priority.High) {
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

                    tile.setNet(Blocks.largeShieldProjector)
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
                    Call.logicExplosion(Team.derelict, ex, ey, FortsRules.now.impactExplosionRadius * Vars.tilesize, FortsRules.now.impactExplosionDamage, true, true, false, true)
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
                    val size = build.block.size

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

                            for (player in Groups.player) {
                                Call.effect(player.con, Fx.explosion, x * Vars.tilesize.toFloat(), y * Vars.tilesize.toFloat(), 0f, Color.yellow)
                            }
                        }
                    })

                    tile.setNet(Blocks.air)
                }
            }
        }

        on<EventType.BlockDestroyEvent> { event ->
            if (loading) return@on

            FortsRules.now.plots.handleBlockBreak(event.tile.x.toInt(), event.tile.y.toInt())
        }
        on<EventType.BlockBuildBeginEvent> { event ->
            if (loading) return@on

            FortsRules.now.plots.handleBlockBreak(event.tile.x.toInt(), event.tile.y.toInt())
        }
        on<EventType.BlockBuildEndEvent> { event ->
            if (loading) return@on

            FortsRules.now.plots.handleBlockBreak(event.tile.x.toInt(), event.tile.y.toInt())
        }

        fun reevalUnitEffects(unit: Unit) {
            if (unit.spawnedByCore) {
                if (unit !is PayloadUnit) return
                if (unit.payloads.size == 0) return
                unit.apply(StatusEffects.slow, 2f)
                if (unit.health > 350) unit.health = 340f
                if (!unit.payloads.any { it is BuildPayload && it.block().category === Category.turret }) return
                unit.apply(StatusEffects.burning, 2f)
                unit.apply(StatusEffects.melting, 2f)
                unit.apply(StatusEffects.corroded, 2f)
                unit.apply(StatusEffects.sapped, 2f)
                unit.apply(StatusEffects.slow, 2f)
                unit.apply(StatusEffects.overclock, 2f)
                if (unit.health > 200) unit.health = 190f
            } else {
                if (unit.type.naval) unit.apply(StatusEffects.boss, 9999f)
                unit.apply(StatusEffects.shielded, 9999f)
                unit.apply(StatusEffects.burning, 9999f)
                if (unit.health > 100) unit.apply(StatusEffects.melting, 9999f)
                if (unit.health > 2000) unit.apply(StatusEffects.corroded, 9999f)
            }
        }
        interval(0.25f) {
            Groups.unit.each(::reevalUnitEffects)
        }
        on<EventType.PickupEvent> { reevalUnitEffects(it.carrier) }

        CustomDestructor.load()

        // UnitTypes.poly.health = 90f
        // UnitTypes.flare.health = 150f

        // (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.metaglass).splashDamage = 65f
        // (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.blastCompound).splashDamage = 100f
        // (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.plastanium).splashDamage = 95f
        // (Blocks.cyclone as ItemTurret).ammoTypes.get(Items.surgeAlloy).splashDamage = 125f

        // val titanAmmo = (Blocks.titan as ItemTurret).ammoTypes.get(Items.thorium)
        // titanAmmo.damage = 200f
        // titanAmmo.splashDamage = 800f
        // titanAmmo.splashDamagePierce = true
        // titanAmmo.splashDamageRadius = 80f
        // titanAmmo.buildingDamageMultiplier = 0.02f
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
