package io.github.kmaba.vLobbyConnect;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.yaml.snakeyaml.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Plugin(
	id = "vlobbyconnect",
	name = "vLobbyConnect",
	url = "https://kmaba.link/",
	description = "A Velocity Plugin for Lobby Connection",
	version = Constants.VERSION,
	authors = { "kmaba" }
)
public final class VelocityPlugin {
	@Inject
	private Logger logger;

	@Inject
	private com.velocitypowered.api.proxy.ProxyServer server;

	private RegisteredServer defaultServer;
	private final Map<String, RegisteredServer> brandLobbies = new HashMap<>();
	private final Map<String, RegisteredServer> forceRoutes = new HashMap<>();
	private final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();

	@Subscribe
	public void onProxyInitialize(ProxyInitializeEvent event) {
		try {
			// Load the config.yml file
			Yaml yaml = new Yaml();
			File configFile = new File("plugins/vLobbyConnect/config.yml");
			if (!configFile.exists()) {
				configFile.getParentFile().mkdirs();
				Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), configFile.toPath());
			}

			// Parse the config.yml file
			Map<String, Object> config = yaml.load(Files.newInputStream(configFile.toPath()));
			Map<String, String> brandRoutes = (Map<String, String>) config.get("brand-routes");
			if (brandRoutes == null) {
				logger.error("Failed to load brand-routes from config.yml");
				return;
			}

			String defaultServer = (String) config.get("default-server");
			if (defaultServer == null) {
				logger.error("default-server not specified in config.yml");
				return;
			}

			Map<String, String> forceRoutes = (Map<String, String>) config.get("force-route");
			if (forceRoutes == null) {
				logger.error("Failed to load force-route from config.yml");
				return;
			}

			// Validate and log the configuration
			Pattern pattern = Pattern.compile("^([a-zA-Z0-9_-]+)$");
			for (Map.Entry<String, String> entry : brandRoutes.entrySet()) {
				String brandKey = entry.getKey().toLowerCase();
				String serverName = entry.getValue();

				Matcher matcher = pattern.matcher(brandKey);
				if (matcher.matches()) {
					Optional<RegisteredServer> serverOpt = server.getServer(serverName);
					if (serverOpt.isPresent()) {
						brandLobbies.computeIfAbsent(brandKey, k -> serverOpt.get());
						logger.info("Brand Route: '{}' -> Server '{}' ({})",
								brandKey, serverName, serverOpt.get().getServerInfo().getAddress());
					} else {
						logger.warn("Server '{}' for brand '{}' not found in Velocity configuration.",
								serverName, brandKey);
					}
				} else {
					logger.warn("Invalid brand key format: '{}'. Use alphanumeric, underscores, or hyphens only.",
							entry.getKey());
				}
			}

			for (Map.Entry<String, String> entry : forceRoutes.entrySet()) {
				String brandKey = entry.getKey().toLowerCase();
				String serverName = entry.getValue();

				Matcher matcher = pattern.matcher(brandKey);
				if (matcher.matches()) {
					Optional<RegisteredServer> serverOpt = server.getServer(serverName);
					if (serverOpt.isPresent()) {
						brandLobbies.computeIfAbsent(brandKey, k -> serverOpt.get());
						logger.info("Force Route Player: '{}' -> Server '{}' ({})",
								brandKey, serverName, serverOpt.get().getServerInfo().getAddress());
					} else {
						logger.warn("Server '{}' for player '{}' not found in Velocity configuration.",
								serverName, brandKey);
					}
				} else {
					logger.warn("Invalid force player name key format: '{}'. Use alphanumeric, underscores, or hyphens only.",
							entry.getKey());
				}
			}

			// Validate default server
			Optional<RegisteredServer> defaultServerOpt = server.getServer(defaultServer);
			if (defaultServerOpt.isPresent()) {
				this.defaultServer = defaultServerOpt.get();
				logger.info("Default fallback server: '{}' ({})",
						defaultServer, defaultServerOpt.get().getServerInfo().getAddress());
			} else {
				logger.error("Default server '{}' not found in Velocity configuration.", defaultServer);
			}
		} catch (IOException e) {
			logger.error("Failed to load config.yml", e);
		}
	}

	@Subscribe
	public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();
		String username = player.getUsername();

		// We need to get the client brand - but it might not be available yet at this point!
		// The brand is usually sent after initial server selection.
		// So we should store the routing decision and apply it later.

		// Alternative approach: Store the intended server for this player
		// and handle actual connection in ServerPreConnectEvent

		logger.warn("Player {} is choosing initial server, but brand may not be available yet.", username);
	}

	@Subscribe
	public void onPlayerClientBrand(PlayerClientBrandEvent event) {
		String brand = event.getBrand().toLowerCase();
		Player player = event.getPlayer();
		String username = player.getUsername();

		// Store the brand for this player
		playerBrands.put(player.getUniqueId(), brand);
		logger.info("Player '{}' client brand detected: '{}'", username, brand);
	}

	@Subscribe
	public void onServerPreConnect(ServerPreConnectEvent event) {
		Player player = event.getPlayer();
		String username = player.getUsername();
		UUID playerId = player.getUniqueId();

		// Check if we have a brand for this player
		String brand = playerBrands.get(playerId);

		if (brand != null) {
			// Check if player is in force-routes
			RegisteredServer forceTarget = forceRoutes.get(username);
			if (forceTarget != null) {
				// Exact brand match found
				event.setResult(ServerPreConnectEvent.ServerResult.allowed(forceTarget));
				logger.info("Player '{}' (brand: '{}') force routed to specific server: {}",
						username, brand, forceTarget.getServerInfo().getName());
			}
			else {
				// Check if we have a specific route for this brand
				RegisteredServer targetServer = brandLobbies.get(brand);

				if (targetServer != null) {
					// Exact brand match found
					event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer));
					logger.info("Player '{}' (brand: '{}') routed to specific server: {}",
							username, brand, targetServer.getServerInfo().getName());
				} else {
					// Check for partial matches (e.g., "neoforge" in "neoforge-1.20")
					String matchedBrand = null;
					for (Map.Entry<String, RegisteredServer> entry : brandLobbies.entrySet()) {
						if (brand.contains(entry.getKey())) {
							matchedBrand = entry.getKey();
							targetServer = entry.getValue();
							break;
						}
					}

					if (targetServer != null) {
						// Partial match found
						event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer));
						logger.info("Player '{}' (brand: '{}') matched partial brand '{}' -> server: {}",
								username, brand, matchedBrand, targetServer.getServerInfo().getName());
					} else {
						// No match found, use default server
						event.setResult(ServerPreConnectEvent.ServerResult.allowed(defaultServer));
						logger.info("Player '{}' (brand: '{}') has no specific route, using default server: {}",
								username, brand, defaultServer.getServerInfo().getName());
					}
				}
			}

			// Clean up stored brand
			playerBrands.remove(playerId);
		}
	}

	@Subscribe
	public void onServerKick(com.velocitypowered.api.event.player.KickedFromServerEvent event) {
		Player player = event.getPlayer();
		RegisteredServer kickedServer = event.getServer();
		String serverName = kickedServer.getServerInfo().getName();

		// If the kicked server is already a lobby, do nothing.
		if (brandLobbies.get(player.getClientBrand()).getServerInfo().getName().equals(serverName)) {
			return;
		}

		RegisteredServer fallback = null;
		String brand = player.getClientBrand();
		RegisteredServer lobby = brandLobbies.get(brand);

		if (lobby != null) {
			fallback = lobby;
		}

		if (fallback != null) {
			event.setResult(com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer.create(fallback));
		}
	}

	@Subscribe
	public void onPlayerDisconnect(Player player) {
		logger.info("Player {} disconnected.", player.getUsername());
	}
}
