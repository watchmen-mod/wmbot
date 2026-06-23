package com.watchmenbot.modules.planebuilder;

final class PlacementExecutor {
    private final PlaneActionGuards guards;
    private final PlaneActionExecutor actions;

    PlacementExecutor(PlaneActionGuards guards, PlaneActionExecutor actions) {
        this.guards = guards;
        this.actions = actions;
    }

    boolean execute(PlacementRequest request) {
        if (!guards.readyForUseAction() || !request.valid()) return false;

        actions.rotate(request.hit(), () -> {
            if (!guards.readyForUseAction() || !request.valid()) return;

            if (request.item().isHotbar()) {
                actions.withHotbarSwap(request.item().slot(), () -> {
                    if (request.preparedValid()) actions.interact(request.hitResult(), request.hand());
                });
            }
            else {
                if (request.preparedValid()) actions.interact(request.hitResult(), request.hand());
            }
        });

        return true;
    }
}
