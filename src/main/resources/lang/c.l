[forts]
notice.modifiers = [cyan]Enabled modifier{if {modifiers.len} != 1 then s}:
                 = {each {modifier} in {modifiers}
                 = ([red]- [yellow]{forts.modifiers.{modifier}.name}
                 = {forts.modifiers.{modifier}.desc})
                 = split (,) join (\n)}

notice.help = [scarlet]Destroy the enemy fort!
            =
            = [accent]Build[][white] {icon} to expand your base.
            = If there are too few buildings
            = left on a platform, it will
            = [cyan]collapse[].

[forts.modifiers]
airforce.name = Airforce
airforce.desc = All built turrets are held by units.

buildup.name = Buildup
buildup.desc = All buildings are built heavily damaged.

crazy.name = Crazy
crazy.desc = All blocks are randomized

decay.name = Decay
decay.desc = Structures slowly fall apart.

fragile.name = Fragile
fragile.desc = Units' and buildings' health is decreased.

glass.name = Glass
glass.desc = Units are glass-strong.

luxury.name = Luxury
luxury.desc = Buildings cost double the resources.

overkill.name = Overkill
overkill.desc = Dead blocks respawn as derelict.

paper.name = Paper
paper.desc = Cores get destroyed instantly.

resourceflood.name = Resource Flood
resourceflood.desc = Resources are gained over time.

unstable.name = Unstable
unstable.desc = Blocks and units explode on destruction.

copper.name = Copper
copper.desc = All items are replaced with copper.

march.name = March
march.desc = Blocks placed by players cannot be deconstructed.
