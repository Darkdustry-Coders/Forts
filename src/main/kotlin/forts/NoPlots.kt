package forts

import arc.struct.IntSeq
import mindustry.game.Team
import mindustry.world.Block

object NoPlots: Plots {
    override fun placeExpansionBlock(
        x: Int,
        y: Int,
        team: Team,
        delete: Runnable
    ): Boolean = false
    override fun handleBlockBreak(x: Int, y: Int, checkTeams: IntSeq) {}
    override fun handleBlockBreak(x: Int, y: Int, checkTeam: Team) {}
    override fun handleTeamDeath(team: Team) {}
    override fun canPlaceBlock(
        team: Team,
        block: Block,
        x: Int,
        y: Int
    ): Boolean = true
}