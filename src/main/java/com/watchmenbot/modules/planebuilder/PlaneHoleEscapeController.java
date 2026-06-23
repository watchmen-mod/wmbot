package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlaneHoleEscapeController {
    private final Config config;
    private final PlaneHoleEscapePlanner planner;
    private final Navigator navigator;

    PlaneHoleEscapeController(PlaneBuilderSettings.HoleEscape settings, PlaneRuntimeConfig runtimeConfig, PlaneWorldAccess world) {
        this(
            () -> settings.enabled().get(),
            new PlaneHoleEscapePlanner(
                runtimeConfig,
                new PlaneHoleEscapePlanner.BlockView() {
                    @Override
                    public boolean passable(BlockPos pos) {
                        return world.isReplaceable(pos);
                    }

                    @Override
                    public boolean solid(BlockPos pos) {
                        return world.isSolidBlock(pos);
                    }
                }
            ),
            new PlaneHoleEscapeNavigator()
        );
    }

    PlaneHoleEscapeController(Config config, PlaneHoleEscapePlanner planner, Navigator navigator) {
        this.config = config;
        this.planner = planner;
        this.navigator = navigator;
    }

    Phase tick(BlockPos playerPos) {
        if (!config.enabled()) {
            reset();
            return Phase.IDLE;
        }

        BlockPos target = planner.escapeTarget(playerPos);
        if (target == null) {
            reset();
            return Phase.IDLE;
        }

        navigator.pathTo(target);
        return Phase.HOLE_ESCAPE;
    }

    void reset() {
        navigator.stop();
    }

    interface Config {
        boolean enabled();
    }

    interface Navigator {
        void pathTo(BlockPos target);

        void stop();
    }
}
