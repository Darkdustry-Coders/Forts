package forts

import mindustry.game.Team

interface Plots {
    fun considerExpansionBlock(): Boolean = true
    fun placeExpansionBlock(x: Int, y: Int, team: Team, delete: Runnable): Boolean
    fun handleBlockBreak(x: Int, y: Int)
    fun handleTeamDeath(team: Team)
}