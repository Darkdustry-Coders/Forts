package forts;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadDeconstructor;

public class CustomDestructor {
    public static void load() {
        Vars.content.blocks().each(x -> x instanceof PayloadDeconstructor, x -> {
            x.buildType = () -> ((PayloadDeconstructor) x).new PayloadDeconstructorBuild() {
                @Override
                public boolean acceptPayload(Building source, Payload payload) {
                    return super.acceptPayload(source, payload) &&
                            (!(payload instanceof BuildPayload) ||
                                    ((BuildPayload) payload).build.block != Blocks.carbideWallLarge);
                }
            };
        });
    }
}
