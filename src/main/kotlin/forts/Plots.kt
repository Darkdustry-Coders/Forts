package forts

import arc.struct.IntSeq
import mindustry.game.Team
import mindustry.world.Block

interface Plots {
    fun considerExpansionBlock(): Boolean = true
    fun placeExpansionBlock(x: Int, y: Int, team: Team, delete: Runnable): Boolean
    fun handleBlockBreak(x: Int, y: Int, checkTeams: IntSeq)
    fun handleBlockBreak(x: Int, y: Int, checkTeam: Team)
    fun handleTeamDeath(team: Team)
    fun canPlaceBlock(team: Team, block: Block, x: Int, y: Int): Boolean
}