package forts

import mindustry.game.Team

enum class PlotStateTag {
    Disabled,
    Enabled,
    Placed,
    Locked,
    Static,
    Ghost,

    ; /* Thanks, Kotlin, for keeping Java's dumbest features. */

    fun startPlaceSchematic(): Boolean = this == Placed || this == Locked || this == Static
    fun placed(): Boolean = this != Enabled && this != Disabled
    fun visible(): Boolean = when (this) { Disabled, Enabled, Ghost -> false; else -> true }
    fun placeable(): Boolean = this == Enabled
    fun breakable(team: Team): Boolean = when (this) {
        Placed -> true
        Locked -> team.cores().isEmpty
        else -> false
    }
}