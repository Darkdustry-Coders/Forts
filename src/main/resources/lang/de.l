[forts]
notice.modifiers = [cyan]Aktive Modifikationen{if {modifiers.len} != 1 then s}:
                 = {each {modifier} in {modifiers}
                 = ([red]- [yellow]{forts.modifiers.{modifier}.name}
                 = {forts.modifiers.{modifier}.desc})
                 = split (,) join (\n)}

notice.help = [scarlet]Zerstöre die Feindliche Festung!
            =
            = [accent]Baue[][white] {icon}, um deine Basis zu erweitern.
            = Wenn zu wenige Gebäude auf
            = lder Platform über sind,
            = [cyan]kollabiert[] sie.

[forts.modifiers]
airforce.name = Luftwaffe
airforce.desc = Alle gebauten Geschütze werden von Einheiten getragen.

buildup.name = Aufbau
buildup.desc = Alle gebauten Gebäude fangen schwer beschädigt an.

crazy.name = Durcheinander
crazy.desc = Alle Gebäude sind vermischt.

decay.name = Zerfall
decay.desc = Alle Strukturen nehmen regelmäßig schaden.

fragile.name = Fragil
fragile.desc = Einheiten und Gebäude haben weniger leben.

glass.name = Glas
glass.desc = Einheiten haben weniger Leben, verursachen aber mehr Schaden.

luxury.name = Luxus
luxury.desc = Gebäude kosten doppelt so viel.

overkill.name = Overkill
overkill.desc = Tote Gebäude verden durch Derelict Gebäude ersetzt.

paper.name = Papier
paper.desc = Cores sterben sobald sie Schaden nehmen.

resourceflood.name = Versorgung
resourceflood.desc = Resourcen werden periodisch aufgefüllt.

unstable.name = Instabil
unstable.desc = Blöcke und Einheiten explodieren, wenn sie sterben.

copper.name = Kupfer
copper.desc = Alle Resourcen werden durch Kupfer ersetzt.

march.name = Marsch
march.desc = Von Spielern platzierte Blöcke können nicht abgebaut werden.