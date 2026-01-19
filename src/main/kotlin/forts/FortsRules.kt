package forts

import mindurka.api.RulesContext
import mindurka.api.SpecialSettings
import mindustry.Vars
import mindustry.content.Blocks

class FortsRules(rc: RulesContext) {
    companion object {
        val PREFIX = "${SpecialSettings.PREFIX}.forts"
        val ENABLE_1VA = "$PREFIX.enable_1va"
        val ENABLE_VNW = "$PREFIX.enable_vnw"
        val EXPANSION_BLOCK = "$PREFIX.expansion_block"

        val THOR_PREFIX = "$PREFIX.thor"
        val THOR_ENABLED = "$THOR_PREFIX.enabled"
        val THOR_DELAY = "$THOR_PREFIX.delay"
        val THOR_COOLDOWN = "$THOR_PREFIX.cooldown"
        val THOR_DAMAGE_MULTIPLIER = "$THOR_PREFIX.damage_multiplier"
        val THOR_RADIUS_MULTIPLIER = "$THOR_PREFIX.radius_multiplier"
        val THOR_BLOCK = "$THOR_PREFIX.block"

        val IMPACT_PREFIX = "$PREFIX.impact"
        val IMPACT_ENABLED = "$IMPACT_PREFIX.enabled"
        val IMPACT_DELAY = "$IMPACT_PREFIX.delay"
        val IMPACT_COOLDOWN = "$IMPACT_PREFIX.cooldown"
        val IMPACT_DURATION = "$IMPACT_PREFIX.duration"
        val IMPACT_EXPLOSION_DAMAGE = "$IMPACT_PREFIX.explosion_damage"
        val IMPACT_EXPLOSION_RADIUS = "$IMPACT_PREFIX.explosion_radius"
        val IMPACT_INSTAKILL = "$IMPACT_PREFIX.instakill"
        val IMPACT_BLOCK = "$IMPACT_PREFIX.block"

        val NEOPLASIA_PREFIX = "$PREFIX.neoplasia"
        val NEOPLASIA_ENABLED = "$NEOPLASIA_PREFIX.enabled"
        val NEOPLASIA_DELAY = "$NEOPLASIA_PREFIX.delay"
        val NEOPLASIA_COOLDOWN = "$NEOPLASIA_PREFIX.cooldown"
        val NEOPLASIA_LENGTH = "$NEOPLASIA_PREFIX.length"
        val NEOPLASIA_PROGRESS_SPEED = "$NEOPLASIA_PREFIX.progress_speed"
        val NEOPLASIA_DAMAGE = "$NEOPLASIA_PREFIX.damage"
        val NEOPLASIA_BLOCK = "$NEOPLASIA_PREFIX.block"

        val PLOT_PREFIX = "$PREFIX.plot"

        lateinit var now: FortsRules
    }

    var enable1va = rc.r(ENABLE_1VA, true)
    var enableVnw = rc.r(ENABLE_VNW, false)
    var expansionBlock = rc.r(EXPANSION_BLOCK, Blocks.impulsePump)

    var thorEnabled = rc.r(THOR_ENABLED, true)
    var thorDelay = rc.r(THOR_DELAY, 0.25f)
    var thorCooldown = rc.r(THOR_COOLDOWN, 0.75f)
    var thorDamageMultiplier = rc.r(THOR_DAMAGE_MULTIPLIER, 1f)
    var thorRadiusMultiplier = rc.r(THOR_RADIUS_MULTIPLIER, 1f)
    var thorBlock = rc.r(THOR_BLOCK, Blocks.thoriumReactor)

    var impactEnabled = rc.r(IMPACT_ENABLED, true)
    var impactDelay = rc.r(IMPACT_DELAY, 0f)
    var impactCooldown = rc.r(IMPACT_COOLDOWN, 5f)
    var impactDuration = rc.r(IMPACT_DURATION, 0.25f)
    var impactExplosionDamage = rc.r(IMPACT_EXPLOSION_DAMAGE, 2000f)
    var impactExplosionRadius = rc.r(IMPACT_EXPLOSION_RADIUS, 4f) * Vars.tilesize
    var impactInstakill = rc.r(IMPACT_INSTAKILL, false)
    var impactBlock = rc.r(IMPACT_BLOCK, Blocks.impactReactor).let { block -> if (block.size == 4) block else Blocks.impactReactor }

    var neoplasiaEnabled = rc.r(NEOPLASIA_ENABLED, true)
    var neoplasiaDelay = rc.r(NEOPLASIA_DELAY, 1.25f)
    var neoplasiaCooldown = rc.r(NEOPLASIA_COOLDOWN, 0.25f)
    var neoplasiaLength = rc.r(NEOPLASIA_LENGTH, 40)
    var neoplasiaProgressSpeed = rc.r(NEOPLASIA_PROGRESS_SPEED, 80f)
    var neoplasiaDamage = rc.r(NEOPLASIA_DAMAGE, 750f)
    var neoplasiaBlock = rc.r(NEOPLASIA_BLOCK, Blocks.neoplasiaReactor).let { block -> if (block.rotate) block else Blocks.neoplasiaReactor }

    val plots: Plots = when (rc.r(PLOT_PREFIX, "square")) {
        "rect" -> RectangularPlots(rc, RectangularPlots.Shape.Rect)
        else /* square */ -> RectangularPlots(rc, RectangularPlots.Shape.Square)
    }
}
