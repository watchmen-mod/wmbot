package com.watchmenbot.modules.stash;

import com.watchmenbot.util.ShulkerBoxContentClassifier;
import net.minecraft.item.ItemStack;

final class StashShulkerContentClassifier {
    static final String ECHEST_ALIAS = "echest";

    private StashShulkerContentClassifier() {
    }

    static ShulkerContent classify(ItemStack stack) {
        ShulkerBoxContentClassifier.Content content = ShulkerBoxContentClassifier.classify(stack);
        return new ShulkerContent(
            content.pureEnderChestContents(),
            content.enderChestCount(),
            content.nonEmptyStacks(),
            content.mixedContents()
        );
    }

    static boolean isPureEchestShulker(ItemStack stack) {
        return classify(stack).pureEchest();
    }

    static boolean isEchestAlias(String kitAlias) {
        return StashKitNameNormalizer.canonicalAlias(kitAlias).equals(ECHEST_ALIAS);
    }

    record ShulkerContent(boolean pureEchest, int enderChestCount, int nonEmptyStacks, boolean mixedContents) {
        static ShulkerContent notEchest() {
            return new ShulkerContent(false, 0, 0, false);
        }
    }
}
