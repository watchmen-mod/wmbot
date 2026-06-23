package com.watchmenbot.modules.stash;

record KitRequestIntent(Type type) {
    static KitRequestIntent listKits() {
        return new KitRequestIntent(Type.LIST_KITS);
    }

    static KitRequestIntent delivery() {
        return new KitRequestIntent(Type.DELIVERY);
    }

    boolean isListKits() {
        return type == Type.LIST_KITS;
    }

    enum Type {
        LIST_KITS,
        DELIVERY
    }
}
