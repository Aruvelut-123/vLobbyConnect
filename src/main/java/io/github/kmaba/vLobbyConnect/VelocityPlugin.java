package io.github.kmaba.vLobbyConnect;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.yaml.snakeyaml.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(
	id = "vlobbyconnect",
	name = "vLobbyConnect",
	description = "A Velocity Plugin for Lobby Connection",
	version = Constants.VERSION,
	authors = { "kmaba", "Baymaxawa" }
)
public final class VelocityPlugin {
	@Inject
	private Logger logger;

	@Inject
	private com.velocitypowered.api.proxy.ProxyServer server;

	@Inject
	private com.velocitypowered.api.command.CommandManager commandManager;

	public static VelocityPlugin INSTANCE;
	private RegisteredServer defaultServer;
	private final Map<String, RegisteredServer> brandLobbies = new HashMap<>();
	private final Map<String, RegisteredServer> forceRoutes = new HashMap<>();
	private final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();
	private final ArrayList<String> onlinePlayers = new ArrayList<String>();

    @Subscribe
	public void onProxyInitialize(ProxyInitializeEvent event) {
		INSTANCE = this;

		CommandManager eventMessageCommand = new CommandManager();
		BrigadierCommand brigadierCommand = eventMessageCommand.createCommand(this.server);

		CommandMeta eventMessageMeta = this.commandManager.metaBuilder("vlobbyconnect")
				.plugin(this)
				.aliases(new String[] { "vlc" }).build();
		this.commandManager.register(eventMessageMeta, (Command)brigadierCommand);
		loadConfig();
	}

	public void loadConfig() {
		try {
			brandLobbies.clear();
			forceRoutes.clear();
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

			Map<String, String> forceRoute = (Map<String, String>) config.get("force-route");
			if (forceRoute == null) {
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

			for (Map.Entry<String, String> entry : forceRoute.entrySet()) {
				String playerName = entry.getKey().toLowerCase();
				String serverName = entry.getValue();

				Matcher matcher = pattern.matcher(playerName);
				if (matcher.matches()) {
					Optional<RegisteredServer> serverOpt = server.getServer(serverName);
					if (serverOpt.isPresent()) {
						forceRoutes.computeIfAbsent(playerName, k -> serverOpt.get());
						logger.info("Force Route Player: '{}' -> Server '{}' ({})",
								playerName, serverName, serverOpt.get().getServerInfo().getAddress());
					} else {
						logger.warn("Server '{}' for player '{}' not found in Velocity configuration.",
								serverName, playerName);
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

	public void reloadConfig() {
		loadConfig();
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
		RegisteredServer originalServer = event.getOriginalServer();
		String username = player.getUsername();
		UUID playerId = player.getUniqueId();
		if (onlinePlayers.contains(username)) {
			event.setResult(ServerPreConnectEvent.ServerResult.allowed(originalServer));
		} else {
			// Check if player is in force-routes
			RegisteredServer forceTarget = forceRoutes.get(username.toLowerCase());
			if (forceTarget != null) {
				// Exact brand match found
				event.setResult(ServerPreConnectEvent.ServerResult.allowed(forceTarget));
				logger.info("Player '{}' force routed to specific server: {}",
						username, forceTarget.getServerInfo().getName());
			} else {
				// Check if we have a brand for this player
				String brand = playerBrands.get(playerId);

				if (brand != null) {
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
	}

	@Subscribe
	public void onServerKick(com.velocitypowered.api.event.player.KickedFromServerEvent event) {
		Player player = event.getPlayer();
		RegisteredServer kickedServer = event.getServer();
		String serverName = kickedServer.getServerInfo().getName();
		Component kickReason = event.getServerKickReason().orElse(Component.text("Kicked For Unknown Reason! Let Administrator to check logs!!!"));
		onlinePlayers.remove(player.getUsername());

		try {
			if (brandLobbies.get(player.getClientBrand()).getServerInfo().getName().equals(serverName)) {
				if (!event.getServerKickReason().toString().contains("velocity.error") && !event.getServerKickReason().toString().contains("velocity.kick")) {
					event.setResult(KickedFromServerEvent.DisconnectPlayer.create(kickReason));
				}
			} else if (forceRoutes.get(player.getUsername()).getServerInfo().getName().equals(serverName)) {
				if (!event.getServerKickReason().toString().contains("velocity.error") && !event.getServerKickReason().toString().contains("velocity.kick")) {
					event.setResult(KickedFromServerEvent.DisconnectPlayer.create(kickReason));
				}
			} else {
				logger.info(kickedServer.getServerInfo().getName());
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		} finally {
			if (player.getClientBrand() != null) {
				String brand = player.getClientBrand();
				RegisteredServer fallback = brandLobbies.get(brand);

				if (fallback != null && !Objects.equals(fallback.getServerInfo().getName(), kickedServer.getServerInfo().getName())) {
					event.setResult(com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer.create(fallback));
				}
			} else {
				logger.error("Player username or client brand is null! It's most likely due to connecting with 1.13- versions.");
			}
		}
	}

	@Subscribe
	public void onPlayerDisconnect(Player player) {
		onlinePlayers.remove(player.getUsername());
		logger.info("Player {} disconnected.", player.getUsername());
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		// Check if the disconnect was due to a connection error
		DisconnectEvent.LoginStatus status = event.getLoginStatus();

		if (status == null) return;

		// Handle different login statuses
		String message = getStatusMessage(status);
		if (message != null) {
			event.getPlayer().sendMessage(Component.text(message));

			// Log it
			logger.info("Player {} disconnected with status: {}",
					event.getPlayer().getUsername(), status.name());
		}
	}

	private String getStatusMessage(DisconnectEvent.LoginStatus status) {
        return switch (status) {
            case CONFLICTING_LOGIN -> "✖ You are already logged in!";
            case CANCELLED_BY_USER -> "✖ Connection failed!\n§7User canceled the connection.";
            case CANCELLED_BY_PROXY -> "✖ Connection failed!\n§7Proxy canceled the connection.";
            case CANCELLED_BY_USER_BEFORE_COMPLETE -> "✖ Connection failed!\n§7User canceled the connection before it completes.";
            default -> "✖ Connection failed: " + status.name();
        };
	}
}
