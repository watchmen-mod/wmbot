package com.watchmenbot.modules.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class InventorySortPlanner {
    private InventorySortPlanner() {
    }

    static List<PlannedStack> plan(List<StackInfo> stacks, int slotCount) {
        List<Group> groups = new ArrayList<>();
        for (StackInfo stack : stacks) {
            if (stack == null || stack.empty()) continue;

            Group group = findGroup(groups, stack);
            if (group == null) {
                group = new Group(stack.displayName(), stack.itemId(), stack.componentKey(), stack.maxCount());
                groups.add(group);
            }

            group.count += stack.count();
        }

        groups.sort(Comparator
            .comparing(Group::sortName)
            .thenComparing(Group::itemId)
            .thenComparing(Group::componentKey)
        );

        List<PlannedStack> planned = new ArrayList<>(slotCount);
        for (Group group : groups) {
            int remaining = group.count;
            int maxCount = Math.max(1, group.maxCount);
            while (remaining > 0 && planned.size() < slotCount) {
                int count = Math.min(maxCount, remaining);
                planned.add(new PlannedStack(group.displayName, group.itemId, group.componentKey, count, maxCount));
                remaining -= count;
            }
        }

        while (planned.size() < slotCount) {
            planned.add(PlannedStack.empty());
        }

        return planned;
    }

    private static Group findGroup(List<Group> groups, StackInfo stack) {
        for (Group group : groups) {
            if (group.matches(stack)) return group;
        }

        return null;
    }

    record StackInfo(String displayName, String itemId, String componentKey, int count, int maxCount) {
        boolean empty() {
            return count <= 0 || itemId == null || itemId.isBlank();
        }

        boolean sameIdentity(PlannedStack other) {
            return other != null
                && !other.isEmpty()
                && itemId.equals(other.itemId())
                && componentKey.equals(other.componentKey());
        }
    }

    record PlannedStack(String displayName, String itemId, String componentKey, int count, int maxCount) {
        static PlannedStack empty() {
            return new PlannedStack("", "", "", 0, 0);
        }

        boolean isEmpty() {
            return count <= 0 || itemId == null || itemId.isBlank();
        }
    }

    private static final class Group {
        private final String displayName;
        private final String itemId;
        private final String componentKey;
        private final int maxCount;
        private int count;

        private Group(String displayName, String itemId, String componentKey, int maxCount) {
            this.displayName = displayName == null ? "" : displayName;
            this.itemId = itemId == null ? "" : itemId;
            this.componentKey = componentKey == null ? "" : componentKey;
            this.maxCount = maxCount;
        }

        private boolean matches(StackInfo stack) {
            return itemId.equals(stack.itemId()) && componentKey.equals(stack.componentKey());
        }

        private String sortName() {
            return displayName.toLowerCase();
        }

        private String itemId() {
            return itemId;
        }

        private String componentKey() {
            return componentKey;
        }
    }
}
