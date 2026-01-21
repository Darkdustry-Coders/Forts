package forts;

import arc.math.Mathf;
import arc.util.Log;
import mindurka.api.SpecialSettings;
import mindurka.util.ModifyWorld;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadDeconstructor;
import mindustry.world.consumers.Consume;

public class ClassPatches {
    public static void load() {
        Vars.content.blocks().each(x -> x instanceof PayloadDeconstructor, x -> {
            x.buildType = () -> ((PayloadDeconstructor) x).new PayloadDeconstructorBuild() {
                @Override
                public boolean acceptPayload(Building source, Payload payload) {
                    if (payload instanceof BuildPayload buildPayload && buildPayload.build.block == Blocks.carbideWallLarge) {
                        Main.carbideWallsCatapult(this, source);
                        return false;
                    }
                    return super.acceptPayload(source, payload);
                }
            };
        });
    }
}
