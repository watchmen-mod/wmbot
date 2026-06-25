package com.watchmenbot.modules.stash;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.cacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.container;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.echestContentCacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.fancyCacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.fancyPossessiveCacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.kitRequestWithSources;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.phraseCacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.shulker;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.source;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.target;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.toPosition;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.watchmenVariantCacheFile;

final class StashKitbotStockSourceCachePureTest {
    private StashKitbotStockSourceCachePureTest() {
    }

    static void run() {
        plansGatherConfirmation();
        normalizesFancyKitNames();
        selectsEchestKitsByPureContent();
        ignoresNamedEchestKitsWithoutPureContent();
        keepsNonEchestKitMatchingNameBased();
        buildsEchestSourcePlansFromPureContent();
        selectsFancyKitByPlainAlias();
        selectsQuotedKitByPhraseAlias();
        resolvesAmbiguousKitSearches();
        formatsAmbiguousKitChoices();
        listsAvailableKitsByTier();
        formatsAvailableKitListMessages();
        combinesInventoryAndCachedKitCounts();
        combinesFancyAliasVariants();
        selectsInventoryOnlyKit();
        buildsSourcePlans();
        buildsFancyAliasSourcePlans();
        choosesClosestKitbotSourceFromCurrentLocation();
        reranksKitbotSourcesAfterMovement();
        skipsFailedKitbotSourcesForNextClosestSource();
        suppressesFailedKitbotSourceNeighborhoodButAllowsUnrelatedSources();
        reconsidersSuppressedKitbotSourceNeighborhoodAfterMovement();
        updatesCachedContainerAndItemIndex();
        depletesCachedSourceSlotAfterConfirmedTransfer();
        decrementsCachedSourceSlotStackAfterPartialTransfer();
        keepsCacheWhenDepletedSourceSlotDoesNotMatch();
        depletesPureEchestCachedSourceSlot();
    }

    private static void plansGatherConfirmation() {
        assertEquals(
            2,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 2),
            "initial inventory contributes to gathered count"
        );
        assertEquals(
            3,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 3),
            "confirmed transfer increases gathered count"
        );
        assertEquals(
            5,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 20),
            "confirmed gathered count caps at request count"
        );
        assertEquals(
            2,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 1),
            "temporary lower inventory count does not reduce initial fulfillment"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.WAIT,
            StashKitbotGatherPlanner.transferVerifyDecision(2, 5, 2, 2, true, false),
            "transfer verification waits before inventory changes"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTINUE_TAKING,
            StashKitbotGatherPlanner.transferVerifyDecision(3, 5, 2, 3, true, false),
            "verified transfer continues taking when more are needed"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.COMPLETE_REQUEST,
            StashKitbotGatherPlanner.transferVerifyDecision(5, 5, 4, 5, false, false),
            "closed container after successful transfer can complete request"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTAINER_CLOSED,
            StashKitbotGatherPlanner.transferVerifyDecision(3, 5, 2, 3, false, false),
            "closed container before completion skips current source"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTINUE_TAKING,
            StashKitbotGatherPlanner.transferVerifyDecision(2, 5, 2, 2, true, true),
            "transfer verification timeout retries taking from open container"
        );
    }

    private static void selectsEchestKitsByPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(echestContentCacheFile(), "echest");

        assertTrue(selected.isPresent(), "selected kit present");
        assertEquals(new SelectedKit("echest", "echest", 5), selected.get(), "echest content match ignores shulker name");
    }

    private static void ignoresNamedEchestKitsWithoutPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "echest");

        assertTrue(selected.isEmpty(), "named echest shulkers without pure content metadata are ignored");
    }

    private static void keepsNonEchestKitMatchingNameBased() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "blue");

        assertTrue(selected.isPresent(), "name-based kit selected");
        assertEquals(new SelectedKit("blue echest", "blue echest", 8), selected.get(), "non-echest query keeps name matching");
    }

    private static void buildsEchestSourcePlansFromPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(echestContentCacheFile(), "echest");

        assertEquals(2, sources.size(), "content echest source count");
        assertEquals(List.of(0), sources.get(0).slots(), "first content source uses unnamed pure echest slot");
        assertEquals(2, sources.get(0).cachedCount(), "first content source count");
        assertEquals(List.of(2), sources.get(1).slots(), "second content source excludes named mixed echest slot");
        assertEquals(3, sources.get(1).cachedCount(), "second content source count");
    }

    private static void normalizesFancyKitNames() {
        String fancyWatchmen = "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽█▓▒░";
        String fancyKit = "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░";

        assertEquals("the watchmen", StashKitNameNormalizer.canonicalAlias(fancyWatchmen), "fancy watchmen canonical alias");
        assertEquals("the watchmen's kit", StashKitNameNormalizer.canonicalAlias(fancyKit), "fancy watchmen kit canonical alias");
        assertEquals("watchmen", StashKitNameNormalizer.alias(fancyWatchmen), "fancy watchmen loose alias");
        assertEquals("watchmen", StashKitNameNormalizer.alias(fancyKit), "fancy watchmen kit loose alias");
        assertEquals("the watchmen's kit", StashKitNameNormalizer.phraseAlias(fancyKit), "fancy watchmen phrase alias");
        assertTrue(StashKitNameNormalizer.matches(fancyKit, "watchmen"), "plain query matches fancy alias");
        assertTrue(StashKitNameNormalizer.matches(fancyKit, "watchmen kit", true), "quoted query matches depossessed phrase alias");
        assertTrue(StashKitNameNormalizer.matchesAliasKey(fancyKit, "watchmen"), "alias key matches loose alias");
        assertTrue(StashKitNameNormalizer.matchesAliasKey(fancyKit, "the watchmen's kit"), "alias key matches canonical alias");
        assertTrue(StashKitNameNormalizer.matches("Blue EChest", "echest"), "plain kit keeps substring matching");
    }

    private static void selectsFancyKitByPlainAlias() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(fancyCacheFile(), "the watchmen's kit", true, Map.of());

        assertTrue(selected.isPresent(), "fancy selected kit present");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), selected.get(), "quoted query selects fancy kit");
    }

    private static void selectsQuotedKitByPhraseAlias() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(phraseCacheFile(), "watchmen kit", true, Map.of());

        assertTrue(selected.isPresent(), "quoted selected kit present");
        assertEquals(new SelectedKit("the watchmen kit", "the watchmen kit", 3), selected.get(), "quoted query keeps kit word selective");

        List<KitSource> sources = planner.buildSources(phraseCacheFile(), selected.get().alias());
        assertEquals(1, sources.size(), "quoted phrase source count");
        assertEquals(List.of(1), sources.getFirst().slots(), "quoted phrase source slots exclude pvp kit");
    }

    private static void resolvesAmbiguousKitSearches() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        StashKitbotStockPlanner.KitResolution ambiguous = planner.resolveKit(fancyCacheFile(), "watchmen", false, Map.of());

        assertTrue(ambiguous.ambiguous(), "plain watchmen query is ambiguous");
        assertEquals(
            List.of(
                new SelectedKit("the watchmen kit", "the watchmen kit", 3),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 4)
            ),
            ambiguous.choices(),
            "ambiguous choices are canonical and sorted"
        );

        StashKitbotStockPlanner.KitResolution selected = planner.resolveKit(fancyCacheFile(), "the watchmen's kit", true, Map.of());
        assertTrue(selected.hasSelection(), "quoted canonical query resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), selected.selected(), "quoted canonical selected kit");

        StashKitbotRequestParser parser = new StashKitbotRequestParser();
        KitCommand prefixed = parser.parseCommand("kit \"the watchmen's kit\" 2");
        StashKitbotStockPlanner.KitResolution prefixedSelected = planner.resolveKit(fancyCacheFile(), prefixed.name(), prefixed.quotedSearch(), Map.of());
        assertTrue(prefixedSelected.hasSelection(), "prefixed quoted canonical query resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), prefixedSelected.selected(), "prefixed quoted canonical selected kit");

        StashKitbotStockPlanner.KitResolution variantAmbiguous = planner.resolveKit(watchmenVariantCacheFile(), "watchmen", false, Map.of());
        assertTrue(variantAmbiguous.ambiguous(), "unquoted watchmen variant query stays ambiguous");
        assertEquals(
            List.of(
                new SelectedKit("the watchmen", "the watchmen", 1),
                new SelectedKit("the watchmen's echest's", "the watchmen's echest's", 2),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 10)
            ),
            variantAmbiguous.choices(),
            "unquoted watchmen choices include all variants"
        );

        StashKitbotStockPlanner.KitResolution exactVariant = planner.resolveKit(watchmenVariantCacheFile(), "the watchmen's kit", true, Map.of());
        assertTrue(exactVariant.hasSelection(), "quoted exact watchmen variant resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 10), exactVariant.selected(), "quoted exact watchmen kit aggregates duplicate containers");

        KitCommand prefixedVariant = parser.parseCommand("kit \"the watchmen's kit\" 2");
        StashKitbotStockPlanner.KitResolution prefixedExactVariant = planner.resolveKit(watchmenVariantCacheFile(), prefixedVariant.name(), prefixedVariant.quotedSearch(), Map.of());
        assertTrue(prefixedExactVariant.hasSelection(), "prefixed quoted exact watchmen variant resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 10), prefixedExactVariant.selected(), "prefixed quoted exact watchmen kit aggregates duplicate containers");
    }

    private static void formatsAmbiguousKitChoices() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        String message = planner.ambiguousChoicesMessage(
            "watchmen",
            2,
            List.of(
                new SelectedKit("the watchmen", "the watchmen", 1),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 1)
            )
        );

        assertEquals(
            "Multiple kits match 'watchmen'. Type one exactly: \"the watchmen\" 2, \"the watchmen's kit\" 2.",
            message,
            "ambiguous choices message"
        );
    }

    private static void listsAvailableKitsByTier() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        KitbotRequesterAccess tier1 = new KitbotRequesterAccess("Alice", "alice", KitbotTier.TIER_1, 0);
        KitbotRequesterAccess tier2 = new KitbotRequesterAccess("Bob", "bob", KitbotTier.TIER_2, 0);

        assertEquals(
            List.of("blue echest", "green kit", "red echest", "spare kit"),
            planner.availableKitNames(cacheFile(), Map.of("Spare Kit", 2), tier1, "red echest"),
            "tier 1 sees all stocked kits from cache and inventory"
        );
        assertEquals(
            List.of("red echest", "spare kit"),
            planner.availableKitNames(cacheFile(), Map.of("Spare Kit", 2), tier2, "red echest, spare kit"),
            "tier 2 sees only stocked whitelisted kits"
        );
        assertEquals(
            List.of(),
            planner.availableKitNames(null, Map.of(), tier2, "echest"),
            "empty stock returns no available kits"
        );
    }

    private static void formatsAvailableKitListMessages() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();

        assertEquals(
            List.of("No kits are currently available for your tier."),
            planner.kitListMessages(List.of()),
            "empty kit list message"
        );
        assertEquals(
            List.of("Available kits: blue echest, red echest"),
            planner.kitListMessages(List.of("blue echest", "red echest"), 80),
            "single kit-list message"
        );
        assertEquals(
            List.of(
                "Available kits 1/2: alpha kit, bravo kit",
                "Available kits 2/2: charlie kit"
            ),
            planner.kitListMessages(List.of("alpha kit", "bravo kit", "charlie kit"), 45),
            "long kit lists are chunked deterministically"
        );
    }

    private static void combinesInventoryAndCachedKitCounts() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(echestContentCacheFile(), "echest", Map.of("echest", 2));

        assertTrue(selected.isPresent(), "combined selected kit present");
        assertEquals(new SelectedKit("echest", "echest", 7), selected.get(), "inventory counts contribute to content echest selection");
    }

    private static void combinesFancyAliasVariants() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(fancyPossessiveCacheFile(), "watchmen", Map.of("The Watchmen's Kit", 5));

        assertTrue(selected.isPresent(), "combined fancy selected kit present");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 9), selected.get(), "canonical variants combine");
    }

    private static void selectsInventoryOnlyKit() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "spare", Map.of("Spare Kit", 2));

        assertTrue(selected.isPresent(), "inventory-only selected kit present");
        assertEquals(new SelectedKit("spare kit", "spare kit", 2), selected.get(), "inventory-only kit can satisfy selection");
    }

    private static void buildsSourcePlans() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(cacheFile(), "blue echest");

        assertEquals(2, sources.size(), "source count");
        assertEquals(new PositionRecord(10, 64, 10), toPosition(sources.get(0).interactionPos()), "first source pos");
        assertEquals(List.of(0), sources.get(0).slots(), "first source slots");
        assertEquals(3, sources.get(0).cachedCount(), "first source count");
        assertEquals(5, sources.get(1).cachedCount(), "second source count");
    }

    private static void buildsFancyAliasSourcePlans() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(fancyCacheFile(), "watchmen");

        assertEquals(2, sources.size(), "fancy source count");
        assertEquals(List.of(0), sources.get(0).slots(), "fancy first source slots");
        assertEquals(4, sources.get(0).cachedCount(), "fancy first source count");
        assertEquals(List.of(2), sources.get(1).slots(), "plain alias variant source slots");
        assertEquals(3, sources.get(1).cachedCount(), "plain alias variant source count");
    }

    private static void choosesClosestKitbotSourceFromCurrentLocation() {
        KitRequest request = kitRequestWithSources(
            source("far", 40, 64, 0),
            source("near", 3, 64, 0)
        );

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            new net.minecraft.util.math.BlockPos(0, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather chooses closest source from current location");
    }

    private static void reranksKitbotSourcesAfterMovement() {
        KitRequest request = kitRequestWithSources(
            source("west", 0, 64, 0),
            source("east", 80, 64, 0)
        );

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(75, 65, 0),
            new net.minecraft.util.math.BlockPos(75, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather re-ranks sources after movement");
    }

    private static void skipsFailedKitbotSourcesForNextClosestSource() {
        KitRequest request = kitRequestWithSources(
            source("failed", 2, 64, 0),
            source("next", 8, 64, 0)
        );
        request.gather.skippedSourceIndexes.add(0);

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            new net.minecraft.util.math.BlockPos(0, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather skips failed source and selects next closest");
    }

    private static void suppressesFailedKitbotSourceNeighborhoodButAllowsUnrelatedSources() {
        KitRequest request = kitRequestWithSources(
            source("suppressed-a", 10, 64, 10),
            source("suppressed-b", 10, 66, 10),
            source("other", 20, 64, 10)
        );
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        throttle.recordPathFailure(target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10)), playerPos);
        throttle.recordPathFailure(target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10)), playerPos);

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            playerPos,
            4.5,
            (source, pos) -> throttle.suppresses(target(source.containerId(), source.interactionPos()), pos)
        ).orElseThrow();

        assertEquals(2, selected, "kitbot gather skips suppressed neighborhood and chooses unrelated source");
    }

    private static void reconsidersSuppressedKitbotSourceNeighborhoodAfterMovement() {
        KitRequest request = kitRequestWithSources(
            source("suppressed-a", 10, 64, 10),
            source("suppressed-b", 10, 66, 10),
            source("other", 20, 64, 10)
        );
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        throttle.recordPathFailure(target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10)), new net.minecraft.util.math.BlockPos(0, 64, 0));
        throttle.recordPathFailure(target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10)), new net.minecraft.util.math.BlockPos(0, 64, 0));

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(13, 65, 0),
            new net.minecraft.util.math.BlockPos(13, 64, 0),
            4.5,
            (source, pos) -> throttle.suppresses(target(source.containerId(), source.interactionPos()), pos)
        ).orElseThrow();

        assertEquals(0, selected, "kitbot gather reconsiders suppressed source neighborhood after movement");
    }

    private static void updatesCachedContainerAndItemIndex() {
        CacheFile updated = StashInventoryCache.withUpdatedContainer(
            cacheFile(),
            container("a", new PositionRecord(10, 64, 10), List.of(shulker(1, "Red EChest", 4))),
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals("2026-06-11T13:00:00Z", updated.updatedAt(), "updated cache timestamp");
        assertEquals(3, updated.containers().size(), "container count preserved");
        assertEquals(List.of(shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "updated source contents");
        assertEquals(18, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index rebuilt without stale taken shulkers");
    }

    private static void depletesCachedSourceSlotAfterConfirmedTransfer() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            cacheFile(),
            "a",
            0,
            "blue echest",
            3,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(List.of(shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "depleted slot is removed from cached container");
        assertEquals(18, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index excludes depleted transferred shulkers");
        assertEquals(List.of(2), new StashKitbotStockPlanner().buildSources(updated, "blue echest").getFirst().slots(), "future source plans no longer include depleted slot");
    }

    private static void decrementsCachedSourceSlotStackAfterPartialTransfer() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            cacheFile(),
            "a",
            0,
            "blue echest",
            1,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(shulker(0, "Blue EChest", 2), updated.containers().getFirst().items().getFirst(), "cached source slot is decremented when only part of the stack transferred");
        assertEquals(20, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index reflects decremented cached stack");
    }

    private static void keepsCacheWhenDepletedSourceSlotDoesNotMatch() {
        CacheFile existing = cacheFile();
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            existing,
            "a",
            1,
            "blue echest",
            1,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertTrue(existing == updated, "non-matching depleted slot leaves cache unchanged");
        assertEquals(List.of(shulker(0, "Blue EChest", 3), shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "non-matching slot contents are preserved");
    }

    private static void depletesPureEchestCachedSourceSlot() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            echestContentCacheFile(),
            "echest-a",
            0,
            "echest",
            2,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(List.of(shulker(1, "Blue EChest", 4)), updated.containers().getFirst().items(), "echest alias depletes only pure echest shulkers");
        assertEquals(List.of(2), new StashKitbotStockPlanner().buildSources(updated, "echest").getFirst().slots(), "future echest source plans skip depleted pure echest slot");
    }
}
