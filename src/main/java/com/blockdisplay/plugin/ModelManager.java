package com.blockdisplay.plugin;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModelManager {
    private final Map<String, ModelData> cache = new HashMap<>();
    private final HttpClient httpClient;
    private final Gson gson;
    private final BlockDisplayPlugin plugin;

    public ModelManager(BlockDisplayPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.gson = new Gson();

        File cacheDir = new File(plugin.getDataFolder(), "cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
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
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    try {
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
}
