package com.blockdisplay.plugin;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Library and snapshot storage of {@link ModelManager}, exercised against a temp folder with a
 * mocked plugin. Mockito 5's inline mock maker lets us stub JavaPlugin's final getters
 * (getDataFolder/getLogger/getServer) without a real server.
 */
class ModelManagerTest {

    @TempDir
    File dataFolder;

    private ModelManager manager;

    @BeforeEach
    void setUp() {
        BlockDisplayPlugin plugin = mock(BlockDisplayPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ModelManagerTest"));

        // saveSpawnedData writes async through the scheduler; run the task inline so the
        // assertion right after the call sees the file.
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(1, Runnable.class).run();
                    return null;
                });

        manager = new ModelManager(plugin);
    }

    private static ModelData sampleModel() {
        ModelData d = new ModelData();
        d.content = new ModelData.Content();
        d.content.version = "1.21.7";
        d.content.passengers = List.of("{id:\"minecraft:block_display\",Tags:[\"bde_0\"]}");
        return d;
    }

    @Test
    void libraryRoundTrip() {
        assertFalse(manager.libraryHas("silla"));

        manager.saveToLibrary("silla", sampleModel());
        assertTrue(manager.libraryHas("silla"));
        assertEquals(List.of("silla"), manager.getLibraryNames());

        ModelData loaded = manager.loadFromLibrary("silla");
        assertNotNull(loaded);
        assertTrue(loaded.hasPassengers());
        assertEquals("1.21.7", loaded.content.version);
    }

    @Test
    void deleteFromLibraryReportsWhetherItExisted() {
        manager.saveToLibrary("mesa", sampleModel());
        assertTrue(manager.deleteFromLibrary("mesa"));
        assertFalse(manager.deleteFromLibrary("mesa"));
        assertFalse(manager.libraryHas("mesa"));
    }

    @Test
    void loadFromMissingLibraryEntryReturnsNull() {
        assertNull(manager.loadFromLibrary("no_existe"));
    }

    @Test
    void spawnedSnapshotRoundTrip() {
        UUID groupId = UUID.randomUUID();
        assertNull(manager.loadSpawnedData(groupId));

        manager.saveSpawnedData(groupId, sampleModel());
        ModelData loaded = manager.loadSpawnedData(groupId);
        assertNotNull(loaded);
        assertTrue(loaded.hasPassengers());

        manager.deleteSpawnedData(groupId);
        assertNull(manager.loadSpawnedData(groupId));
    }

    @Test
    void snapshotWithoutPassengersIsNeverWritten() {
        UUID groupId = UUID.randomUUID();
        manager.saveSpawnedData(groupId, new ModelData());
        assertNull(manager.loadSpawnedData(groupId));
    }

    @Test
    void cleanupRemovesOnlyOrphanSnapshots() {
        UUID kept = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        manager.saveSpawnedData(kept, sampleModel());
        manager.saveSpawnedData(orphan, sampleModel());

        manager.cleanupOrphanSnapshots(Set.of(kept.toString()));

        assertNotNull(manager.loadSpawnedData(kept));
        assertNull(manager.loadSpawnedData(orphan));
    }
}
