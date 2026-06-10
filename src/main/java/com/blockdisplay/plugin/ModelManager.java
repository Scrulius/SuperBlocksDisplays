package com.blockdisplay.plugin;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ModelManager {
    // ConcurrentHashMap: populated from the HTTP client's worker thread, read from the main thread.
    private final Map<String, ModelData> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final Gson gson;
    private final BlockDisplayPlugin plugin;
    private final File libraryDir;
    private final File spawnedDataDir;

    public ModelManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();

        File cacheDir = new File(plugin.getDataFolder(), "cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        this.libraryDir = new File(plugin.getDataFolder(), "library");
        if (!libraryDir.exists()) {
            libraryDir.mkdirs();
        }

        // Per-group snapshots of the full model data, so re-spawning on restart
        // never depends on the remote API or a name that happens to match the cache.
        this.spawnedDataDir = new File(plugin.getDataFolder(), "data");
        if (!spawnedDataDir.exists()) {
            spawnedDataDir.mkdirs();
        }
    }

    /**
     * Resolve model data from any local source first (in-memory cache, library, API cache file),
     * only hitting the remote API as a last resort. Works for both numeric API ids and library names.
     */
    public CompletableFuture<ModelData> resolveModelData(String source) {
        ModelData fromLibrary = loadFromLibrary(source);
        if (fromLibrary != null && fromLibrary.hasPassengers()) {
            return CompletableFuture.completedFuture(fromLibrary);
        }
        return fetchModel(source);
    }

    // ---- Per-group spawned snapshots (authoritative, API-independent) ----

    /**
     * Write a full snapshot of a spawned model's data, keyed by its group id. This is what gets
     * re-loaded on restart, so a model survives even if its API id expired or its library entry
     * was deleted.
     */
    public void saveSpawnedData(UUID groupId, ModelData data) {
        if (data == null || !data.hasPassengers()) return;
        File file = new File(spawnedDataDir, groupId + ".json");
        // ModelData is immutable after fetch and snapshots run to ~500 KB; serialize and write
        // off the main thread so spawning never stalls a tick on disk I/O.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not snapshot model data for group " + groupId + ": " + e.getMessage());
            }
        });
    }

    public ModelData loadSpawnedData(UUID groupId) {
        File file = new File(spawnedDataDir, groupId + ".json");
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            ModelData data = gson.fromJson(reader, ModelData.class);
            return (data != null && data.hasPassengers()) ? data : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read snapshot for group " + groupId + ": " + e.getMessage());
            return null;
        }
    }

    public void deleteSpawnedData(UUID groupId) {
        File file = new File(spawnedDataDir, groupId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Delete snapshots whose group no longer exists in spawned.yml (e.g. files left behind by a
     * crash between snapshot write and group save). Called once at startup so data/ can't grow forever.
     */
    public void cleanupOrphanSnapshots(Set<String> validGroupIds) {
        File[] files = spawnedDataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        int removed = 0;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 5);
            if (!validGroupIds.contains(id) && f.delete()) {
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleaned up " + removed + " orphaned model snapshot(s).");
        }
    }

    public CompletableFuture<ModelData> fetchModel(String modelId) {
        if (cache.containsKey(modelId)) {
            return CompletableFuture.completedFuture(cache.get(modelId));
        }

        File cacheFile = new File(plugin.getDataFolder() + File.separator + "cache", modelId + ".json");
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile)) {
                ModelData data = gson.fromJson(reader, ModelData.class);
                if (data != null && data.hasPassengers()) {
                    cache.put(modelId, data);
                    return CompletableFuture.completedFuture(data);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to read cache for model " + modelId + ", re-fetching.");
                cacheFile.delete();
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://block-display.com/server-api/?id=" + modelId))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() != 200) {
                            plugin.getLogger().warning("API returned HTTP " + response.statusCode() + " for model " + modelId);
                            return null;
                        }
                        String body = response.body();

                        // Check for error response
                        if (body.contains("\"error\"")) {
                            plugin.getLogger().warning("API error for model " + modelId + ": " + body.trim());
                            return null;
                        }

                        ModelData data = gson.fromJson(body, ModelData.class);
                        if (data != null && data.hasPassengers()) {
                            cache.put(modelId, data);
                            try (FileWriter writer = new FileWriter(cacheFile)) {
                                writer.write(body);
                            } catch (Exception writeEx) {
                                plugin.getLogger().warning("Could not save cache file for " + modelId);
                            }
                            return data;
                        } else {
                            plugin.getLogger().warning("Failed to parse model " + modelId + " - no passengers found.");
                            return null;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error parsing JSON for model " + modelId + ": " + e.getMessage());
                        return null;
                    }
                })
                // Without this, a network failure (no internet, timeout, DNS) completes the future
                // exceptionally and the caller's thenAccept NEVER runs - the player would get no
                // feedback at all. Map every failure to null so callers always hear back.
                .exceptionally(ex -> {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    plugin.getLogger().warning("Could not reach block-display.com for model " + modelId + ": " + cause.getMessage());
                    return null;
                });
    }

    public void invalidateCache(String modelId) {
        cache.remove(modelId);
        File cacheFile = new File(plugin.getDataFolder() + File.separator + "cache", modelId + ".json");
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    public void clearCache() {
        cache.clear();
        File cacheDir = new File(plugin.getDataFolder(), "cache");
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    // ---- Model Library ----

    public void saveToLibrary(String libraryName, ModelData data) {
        File file = new File(libraryDir, libraryName + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save model to library '" + libraryName + "': " + e.getMessage());
        }
    }

    public ModelData loadFromLibrary(String libraryName) {
        File file = new File(libraryDir, libraryName + ".json");
        if (!file.exists()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, ModelData.class);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load model from library '" + libraryName + "': " + e.getMessage());
            return null;
        }
    }

    public boolean deleteFromLibrary(String libraryName) {
        File file = new File(libraryDir, libraryName + ".json");
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public List<String> getLibraryNames() {
        List<String> names = new ArrayList<>();
        File[] files = libraryDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                names.add(name.substring(0, name.length() - 5));
            }
        }
        return names;
    }

    public boolean libraryHas(String libraryName) {
        return new File(libraryDir, libraryName + ".json").exists();
    }
}
