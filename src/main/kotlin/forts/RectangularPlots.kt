package forts

import arc.graphics.Color
import arc.math.Mathf
import arc.struct.IntMap
import arc.struct.IntSeq
import arc.util.Strings
import arc.util.Log
import mindurka.api.RulesContext
import mindurka.util.FormatException
import mindurka.util.Schematic
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Block
import kotlin.run

class RectangularPlots(rc: RulesContext, shape: Shape): Plots {
    companion object {
        val PREFIX = FortsRules.PLOT_PREFIX
        val SIZE = "$PREFIX.size"
        val WIDTH = "$PREFIX.width"
        val HEIGHT = "$PREFIX.height"
        val WALLS_SIZE = "$PREFIX.walls_size"
        val SHIFT_X = "$PREFIX.shift_x"
        val SHIFT_Y = "$PREFIX.shift_y"
        val STATES = "$PREFIX.states"

        const val MAX_SIZE = 16

        val SCHEME_OPTIONS = Schematic.Options().skipAir().skipEmpty()
        val SCHEME_CREATE_OPTIONS = Schematic.Options().skipBuildings()

        private val SCHEMATIC_HEAD = "$PREFIX.schematic."
        private val SCHEMATIC_HEAD_END = SCHEMATIC_HEAD.length
        fun keyIsSchematic(key: String): Boolean {
            if (!key.startsWith(SCHEMATIC_HEAD)) return false
            // TODO: Port to MindurkaCompat. MindurkaCompat implementation sucks.
            return when (key.length - SCHEMATIC_HEAD.length) {
                1 -> ('1'..'9').contains(key[SCHEMATIC_HEAD_END])
                2 -> ('1'..'9').contains(key[SCHEMATIC_HEAD_END]) && ('0'..'9').contains(key[SCHEMATIC_HEAD_END + 1])
                3 -> ('1'..'2').contains(key[SCHEMATIC_HEAD_END]) && (
                    ('0'..'4').contains(key[SCHEMATIC_HEAD_END + 1]) && ('0'..'9').contains(key[SCHEMATIC_HEAD_END + 2]) ||
                    key[SCHEMATIC_HEAD_END + 1] == '5' && ('0'..'4').contains(key[SCHEMATIC_HEAD_END + 2])
                )
                else -> false
            }
        }
        fun keySchematicTeam(key: String): Team? {
            if (!keyIsSchematic(key)) return null
            return Team.all[Strings.parseInt(key, 10, 0, SCHEMATIC_HEAD_END, key.length)]
        }
    }

    enum class Shape {
        Square,
        Rect,

        ;

        fun width(): String = if (this == Square) SIZE else WIDTH
        fun height(): String = if (this == Square) SIZE else HEIGHT
    }

    val width = rc.r(shape.width(), 6).let { if (it !in 1..MAX_SIZE) 6 else it }
    val height = rc.r(shape.height(), 6).let { if (it !in 1..MAX_SIZE) 6 else it }
    val wallsSize = rc.r(WALLS_SIZE, 1).let { if (it !in 1..MAX_SIZE) 1 else it }
    private val jX = width + wallsSize
    private val jY = height + wallsSize
    val startX = rc.r(SHIFT_X, 0).let { (((it % jX) + jX) % jX).let { if (it !in 0..MAX_SIZE) 0 else it } }
    val startY = rc.r(SHIFT_Y, 0).let { (((it % jY) + jY) % jY).let { if (it !in 0..MAX_SIZE) 0 else it } }
    val plotsX = (rc.mapWidth - startX + wallsSize) / jX
    val plotsY = (rc.mapHeight - startY + wallsSize) / jY
    val teams: ByteArray = ByteArray(plotsX * plotsY)
    fun teams(plotX: Int, plotY: Int): Team = Team.all[teams[plotX + plotY * plotsX].toInt()]
    fun teams(plotX: Int, plotY: Int, team: Team) { teams[plotX + plotY * plotsX] = team.id.toByte() }
    val states: ByteArray = ByteArray(plotsX * plotsY)
    fun states(plotX: Int, plotY: Int, state: PlotStateTag) { states[plotX + plotY * plotsX] = state.ordinal.toByte() }
    fun states(plotX: Int, plotY: Int): PlotStateTag = PlotStateTag.entries[states[plotX + plotY * plotsX].toInt()]
    val plotSchematics = IntMap<Schematic>()

    private val centerParts = IntMap<Schematic>()
    private val horizontalWalls = IntMap<Schematic>()
    private val verticalWalls = IntMap<Schematic>()
    private val intersectionParts = IntMap<Schematic>()

    // Kotlin moment
    init { run {
        for (key in rc.rules.tags.keys()) {
            val team = keySchematicTeam(key) ?: continue
            Log.info("Found team key: $key")
            try {
                val scheme = Schematic.of(rc.rules.tags.get(key))
                if (scheme.width != width + wallsSize * 2 || scheme.height != height + wallsSize * 2) {
                    Log.info("Scheme's FUCKED (${scheme.width} vs ${width + wallsSize * 2}, ${scheme.height} vs ${height + wallsSize * 2})")
                    continue
                }
                plotSchematics.put(team.id, scheme)
            } catch (ignored: FormatException) {
                plotSchematics.clear()
                return@run
            }
        }

        val statesS = rc.r(STATES, "")
        if (statesS.length != states.size * 3) {
            Log.err("Invalid states length! (${statesS.length} vs ${states.size * 3})")
            return@run
        }

        var chars = statesS.chars().iterator()
        for (ignored /* IDEA shut up */ in 0..<plotsX * plotsY) {
            var ch = chars.nextInt()
            if (ch < 'a'.code) {
                Log.err("Invalid plot kind! ($ch)")
                return@run
            }
            if (ch - 'a'.code >= PlotStateTag.entries.size) {
                Log.err("Invalid plot kind! ($ch)")
                return@run
            }

            ch = chars.nextInt()
            if (ch !in '0'.code..'9'.code && ch !in 'a'.code..'f'.code) {
                Log.err("Invalid team! ([0] = $ch)")
                return@run
            }
            ch = chars.nextInt()
            if (ch !in '0'.code..'9'.code && ch !in 'a'.code..'f'.code) {
                Log.err("Invalid team! ([1] = $ch)")
                return@run
            }
        }
        chars = statesS.chars().iterator()
        for (cursor in 0..<plotsX * plotsY) {
            states[cursor] = (chars.nextInt() - 'a'.code).toByte()

            val a = chars.nextInt()
            val b = chars.nextInt()

            teams[cursor] = ((if (a >= 'a'.code) a + 10 - 'a'.code else a - '0'.code) * 16
                + (if (b >= 'a'.code) b + 10 - 'a'.code else b - '0'.code)).toByte()
        }

        for (x in 0..<plotsX) for (y in 0..<plotsY) {
            if (!states(x, y).placed()) continue
            val team = teams(x, y)
            val schematic = plotSchematics[team.id] ?: continue
            actualPlacePlot(x, y, team, schematic)
        }
    } }

    fun actualPlacePlot(plotX: Int, plotY: Int, team: Team, schematic: Schematic) {
        run {
            val x = startX + plotX * jX
            val y = startY + plotY * jY
            val i = plotX + plotY * plotsX
            if (!centerParts.containsKey(i)) {
                centerParts.put(i, Schematic.of(Vars.world.tiles, x, y, width, height, SCHEME_CREATE_OPTIONS))
                schematic.paste(wallsSize, wallsSize, width, height, Vars.world.tiles, x, y, SCHEME_OPTIONS)
            }
        }

        for (dx in 0..1) for (dy in 0..1) {
            val x = startX + (plotX + dx) * jX - wallsSize
            val y = startY + (plotY + dy) * jY - wallsSize
            val i = (plotX + dx) + (plotY + dy) * (plotsX + 1)
            if (!intersectionParts.containsKey(i)) {
                intersectionParts.put(i, Schematic.of(Vars.world.tiles, x, y, wallsSize, wallsSize, SCHEME_CREATE_OPTIONS))
                schematic.paste(dx * jX, dy * jY, wallsSize, wallsSize, Vars.world.tiles, x, y, SCHEME_OPTIONS)
            }
        }

        for (d in 0..1) {
            run {
                val x = startX + (plotX + d) * jX - wallsSize
                val y = startY + plotY * jY
                val i = (plotX + d) + plotY * (plotsX + 1)

                if (!horizontalWalls.containsKey(i)) {
                    horizontalWalls.put(i, Schematic.of(Vars.world.tiles, x, y, wallsSize, height))
                    schematic.paste(jX * d, wallsSize, wallsSize, height, Vars.world.tiles, x, y)
                }
            }
            run {
                val x = startX + plotX * jX
                val y = startY + (plotY + d) * jY - wallsSize
                val i = plotX + (plotY + d) * plotsX

                if (!verticalWalls.containsKey(i)) {
                    verticalWalls.put(i, Schematic.of(Vars.world.tiles, x, y, width, wallsSize))
                    schematic.paste(wallsSize, jY * d, width, wallsSize, Vars.world.tiles, x, y)
                }
            }
        }

        for (other in Team.all) {
            Main.filterTeamPlans(team)
        }
    }

    private fun _placeExpansionBlock(x: Int, y: Int, team: Team, delete: Runnable): Boolean {
        val scheme = plotSchematics[team.id] ?: return false

        val x = if (x < startX) startX else if (x > startX + jX * plotsX) startX + jX * plotsX else x
        val y = if (y < startY) startY else if (y > startY + jY * plotsY) startY + jY * plotsY else y

        for (dx in -1..1) for (dy in -1..1) {
            val plotX = (x - startX) / jX + dx
            val plotY = (y - startY) / jY + dy

            if (dx == 0 && dy == 0) {
                if (!states(plotX, plotY).placeable()) return false
            } else if (states(plotX, plotY).placed() && teams(plotX, plotY) != team) return false
        }

        delete.run()
        actualPlacePlot((x - startX) / jX, (y - startY) / jY, team, scheme)
        val plotX = (x - startX) / jX
        val plotY = (y - startY) / jY
        teams(plotX, plotY, team)
        states(plotX, plotY, PlotStateTag.Placed)

        for (dx in 0..<jX + wallsSize) for (dy in 0..<jY + wallsSize) {
            val x = plotX * jX + dx + startX - wallsSize
            val y = plotY * jY + dy + startY - wallsSize
            val tile = Vars.world.tile(x, y) ?: continue

            if (tile.block() != Blocks.air) continue
            if (!tile.floor().placeableOn) continue
            if (tile.floor().isLiquid && !tile.floor().shallow) continue
            tile.setNet(Blocks.scrapWall, team, 0)
        }

        for (team in Team.all) {
            if (team.core() == null) continue
            team.data().plans.removeAll { plan ->
                when (plan.block.size) {
                    1 -> shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt(), team)
                    2 -> shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt(), team) ||
                        shouldClearPlanCoord(plan.x.toInt() + 1, plan.y.toInt(), team) ||
                        shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt() + 1, team) ||
                        shouldClearPlanCoord(plan.x.toInt() + 1, plan.y.toInt() + 1, team)
                    else -> false
                }
            }
        }

        return true
    }
    override fun placeExpansionBlock(x: Int, y: Int, team: Team, delete: Runnable): Boolean {
        if (_placeExpansionBlock(x, y, team, delete)) return true

        val shift = FortsRules.now.expansionBlock.size / 2
        val _x = if (x < startX) startX else if (x > startX + jX * plotsX) startX + jX * plotsX else x
        val _y = if (y < startY) startY else if (y > startY + jY * plotsY) startY + jY * plotsY else y
        val plotX = (_x - startX) / jX
        val plotY = (_y - startY) / jY
        val inPlotX = (_x - (startX + jX * plotX)).toFloat() / (jX - shift * 2)
        val inPlotY = (_y - (startY + jY * plotY)).toFloat() / (jY - shift * 2)
        val dx = if (inPlotX <= 0.33f) -1 else if (inPlotX <= 0.66f) 0 else 1
        val dy = if (inPlotY <= 0.33f) -1 else if (inPlotY <= 0.66f) 0 else 1

        return _placeExpansionBlock(x + dx * jX, y + dy * jY, team, delete)
    }

    private fun _handleBlockBreak(plotX: Int, plotY: Int) {
        if (plotX < 0 || plotY < 0 || plotX >= plotsX || plotY >= plotsY) return
        if (!states(plotX, plotY).breakable(teams(plotX, plotY))) return

        var score = 0f

        for (dx in 0..<jX + wallsSize) for (dy in 0..<jY + wallsSize) {
            val x = plotX * jX + startX - wallsSize + dx
            val y = plotY * jY + startY - wallsSize + dy
            val tile = Vars.world.tile(x, y) ?: continue
            score += tile.build?.health ?: 0f
            if (score >= 450) return
        }

        val smokeColor = Color(); smokeColor.rgba8888(0x0c0c0c7a)
        val drillColor = Color(); drillColor.rgba8888(0xffac00ffL.toUInt().toInt())
        for (dx in -1..jX + wallsSize) for (dy in -1..jY + wallsSize) {
            val x = plotX * jX + startX - wallsSize + dx.toFloat()
            val y = plotY * jY + startY - wallsSize + dy.toFloat()

            if (Mathf.random() > 0.65f) Call.effect(Fx.smokeCloud, x * Vars.tilesize, y * Vars.tilesize, 0f, smokeColor)
            if (Mathf.random() > 0.45f) Call.effect(Fx.drillSteam, x * Vars.tilesize, y * Vars.tilesize, 0f, drillColor)
        }

        states(plotX, plotY, PlotStateTag.Enabled)

        run {
            val i = plotX + plotY * plotsX
            if (centerParts.containsKey(i)) {
                centerParts.remove(i).paste(Vars.world.tiles, plotX * jX + startX, plotY * jY + startY)
            }
        }

        for (dx in 0..1) a@for (dy in 0..1) {
            val i = (plotX + dx) + (plotY + dy) * (plotsX + 1)
            for (ddx in -1..0) for (ddy in -1..0) {
                if (plotX + dx + ddx >= plotsX) continue
                if (plotY + dy + ddy >= plotsY) continue
                if (plotX + dx + ddx < 0) continue
                if (plotY + dy + ddy < 0) continue
                if (states(plotX + dx + ddx, plotY + dy + ddy).visible()) continue@a
            }
            if (intersectionParts.containsKey(i)) {
                intersectionParts.remove(i).paste(Vars.world.tiles, (plotX + dx) * jX + startX - wallsSize, (plotY + dy) * jY + startY - wallsSize)
            }
        }

        for (d in 0..1) {
            run {
                for (dd in -1..0) {
                    if (plotX + d + dd >= plotsX) continue
                    if (plotX + d + dd < 0) continue
                    if (states(plotX + d + dd, plotY).visible()) return@run
                }
                val i = (plotX + d) + plotY * (plotsX + 1)
                if (horizontalWalls.containsKey(i)) {
                    horizontalWalls.remove(i).paste(Vars.world.tiles, (plotX + d) * jX + startX - wallsSize, plotY * jY + startY)
                }
            }
            run {
                for (dd in -1..0) {
                    if (plotY + d + dd >= plotsY) continue
                    if (plotY + d + dd < 0) continue
                    if (states(plotX, plotY + d + dd).visible()) return@run
                }
                val i = plotX + (plotY + d) * plotsX
                if (verticalWalls.containsKey(i)) {
                    verticalWalls.remove(i).paste(Vars.world.tiles, plotX * jX + startX, (plotY + d) * jY + startY - wallsSize)
                }
            }
        }
    }
    private inline fun shouldClearPlan(plotX: Int, plotY: Int, team: Team): Boolean =
        if (plotX < 0 || plotY < 0 || plotX >= plotsX || plotY >= plotsY) false
        else states(plotX, plotY).placed() && teams(plotX, plotY) != team
    private inline fun shouldClearPlanCoord(x: Int, y: Int, team: Team): Boolean {
        if (x < startX - wallsSize || y < startY - wallsSize) return false
        if (x >= startX + plotsX * jX || y >= startY + plotsY * jY) return false

        // Shifted by `wallsSize` for easier handling.
        val plotX = (x - startX + wallsSize) / jX
        val plotY = (y - startY + wallsSize) / jY
        val inPlotX = x - (plotX * jX + startX - wallsSize)
        val inPlotY = y - (plotY * jY + startY - wallsSize)

        if (shouldClearPlan(plotX, plotY, team)) return true
        if (inPlotX == 0 && plotX != 0 && shouldClearPlan(plotX - 1, plotY, team)) return true
        if (inPlotY == 0 && plotY != 0 && shouldClearPlan(plotX, plotY - 1, team)) return true
        if (inPlotX == 0 && inPlotY == 0 &&
            plotX != 0 && plotY != 0 &&
            shouldClearPlan(plotX - 1, plotY - 1, team)) return true

        return false
    }
    private inline fun handleBlockBreakImpl(x: Int, y: Int, teams: ((Team) -> kotlin.Unit) -> kotlin.Unit) {
        teams { team ->
            team.data().plans.removeAll { plan ->
                when (plan.block.size) {
                    1 -> shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt(), team)
                    2 -> shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt(), team) ||
                        shouldClearPlanCoord(plan.x.toInt() + 1, plan.y.toInt(), team) ||
                        shouldClearPlanCoord(plan.x.toInt(), plan.y.toInt() + 1, team) ||
                        shouldClearPlanCoord(plan.x.toInt() + 1, plan.y.toInt() + 1, team)
                    else -> false
                }
            }
        }

        val plotX = (x - startX) / jX
        val plotY = (y - startY) / jY
        val inPlotX = x - (plotX * jX + startX)
        val inPlotY = y - (plotY * jY + startY)

        _handleBlockBreak(plotX, plotY)
        if (inPlotX >= width) _handleBlockBreak(plotX + 1, plotY)
        if (inPlotY >= height) _handleBlockBreak(plotX, plotY + 1)
        if (inPlotX >= width && inPlotY >= height) _handleBlockBreak(plotX + 1, plotY + 1)
    }
    override fun handleBlockBreak(x: Int, y: Int, checkTeams: IntSeq) {
        handleBlockBreakImpl(x, y) { cb ->
            for (i in 0..<checkTeams.size) {
                cb(Team.all[checkTeams.items[i]])
            }
        }
    }
    override fun handleBlockBreak(x: Int, y: Int, checkTeam: Team) {
        handleBlockBreakImpl(x, y) { cb -> cb(checkTeam) }
    }

    override fun handleTeamDeath(team: Team) {
        for (x in 0..<plotsX) for (y in 0..<plotsY) {
            if (!states(x, y).placed()) continue
            if (teams(x, y).cores() != team) continue
            _handleBlockBreak(x, y)
        }
    }

    private inline fun _canPlaceBlock(team: Team, block: Block, plotX: Int, plotY: Int): Boolean =
        if (plotX >= plotsX || plotY >= plotsY) true
        else !states(plotX, plotY).placed() || teams(plotX, plotY) == team
    override fun canPlaceBlock(team: Team, block: Block, x: Int, y: Int): Boolean {
        if (block.size > 2) return true

        if (x < startX - wallsSize || y < startY - wallsSize) return true
        if (x >= startX + plotsX * jX || y >= startY + plotsY * jY) return true

        // Shifted by `wallsSize` for easier handling.
        val plotX = (x - startX + wallsSize) / jX
        val plotY = (y - startY + wallsSize) / jY
        val inPlotX = x - (plotX * jX + startX - wallsSize)
        val inPlotY = y - (plotY * jY + startY - wallsSize)

        if (!_canPlaceBlock(team, block, plotX, plotY)) return false
        if (inPlotX == 0 && plotX != 0 && !_canPlaceBlock(team, block, plotX - 1, plotY)) return false
        if (inPlotY == 0 && plotY != 0 && !_canPlaceBlock(team, block, plotX, plotY - 1)) return false
        if (inPlotX == 0 && inPlotY == 0 &&
            plotX != 0 && plotY != 0 &&
            !_canPlaceBlock(team, block, plotX - 1, plotY - 1)) return false

        // for (x in x - (block.size - 1) / 2..<x - (block.size - 1) / 2 + block.size) for (y in y - (block.size - 1) / 2..<y - (block.size - 1) / 2 + block.size) {
        //     if (x < startX - 1 || y < startY - 1) continue
        //     if (x >= startX + jX * plotsX) continue
        //     if (y >= startY + jY * plotsY) continue

        //     val px = (x - startX) % jX
        //     val py = (y - startY) % jY

        //     for (x in (x - startX) / jX..(x - startX) / jX + if (px == 0) 1 else 0) for (y in (y - startY) / jY..(y - startY) / jY + if (py == 0) 1 else 0) {
        //         if (x < 0 || y < 0 || x >= plotsX || y >= plotsY) continue
        //         if (states(x, y).placed() && teams(x, y) != team) return false
        //     }
        // }

        return true
    }
}