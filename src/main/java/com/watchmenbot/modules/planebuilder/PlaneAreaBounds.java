package com.watchmenbot.modules.planebuilder;

record PlaneAreaBounds(int minX, int maxX, int minZ, int maxZ) {
    boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    static PlaneAreaBounds buildArea() {
        return PlaneRuntimeConfig.DEFAULT.buildArea();
    }

    static PlaneAreaBounds scanWindow(int centerX, int centerZ, int radius) {
        return scanWindow(buildArea(), centerX, centerZ, radius);
    }

    static PlaneAreaBounds scanWindow(PlaneAreaBounds buildArea, int centerX, int centerZ, int radius) {
        return new PlaneAreaBounds(
            Math.max(buildArea.minX(), centerX - radius),
            Math.min(buildArea.maxX(), centerX + radius),
            Math.max(buildArea.minZ(), centerZ - radius),
            Math.min(buildArea.maxZ(), centerZ + radius)
        );
    }
}
