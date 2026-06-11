# vLobbyConnect Fork – May not be the Ultimate Lobby Manager for Velocity Proxy  

### Notice: This is not compatible with original one cause I haven't changed the names and other stuffs yet. Why? Cause I'm currently using it and lazy to migrate configurations.

vLobbyConnect was a **powerful and lightweight Velocity plugin** designed to seamlessly manage lobby connections for **players using different Minecraft protocol versions**. Whether your server supports multiple Minecraft versions or needs efficient load balancing, vLobbyConnect ensures players are sent to the **correct lobby** every time.  
But I need to use for transferring players around modded server, so I fork it and make some modifies on it.  

## 🚀 Why Use vLobbyConnect?  
- **Version-Specific Lobby Assignment** – Automatically sends players to the appropriate lobby based on their Minecraft version.  
- **Seamless Load Balancing** – Distributes players evenly across multiple lobbies, preventing overcrowding and lag.  
- **Failsafe Mechanisms** – If a lobby is misconfigured or full, players are redirected to an available fallback lobby.  
- **Easy Setup & Configuration** – Just drop the plugin into Velocity, configure the lobbies, and you're good to go!  
Yeah, just download the original one if these are already enough for you. [Download here](https://github.com/kmaba/vLobbyConnect)  

## Why use this fork?
In case you need the same modded server transferring functions :)

## Setup

1. Place the plugin jar in your Velocity plugins folder.
2. Configure your lobbies in two places:

### Plugin Config (config.yml)
This file is located in `src/main/resources/config.yml` (it will be copied to `plugins/vLobbyConnect/config.yml` on first run):

```yaml
# Client brand routing configuration
# Format: "brand-keyword" = "server-name-in-velocity-toml"
brand-routes:
  # NeoForge clients
  neoforge: "neoforge-server"

  # Forge clients (older versions)
  forge: "forge-server"

  # Fabric clients
  fabric: "fabric-server"

  # Specific clients
  lunarclient: "lunar-server"
  badlion: "badlion-server"
  vanilla: "vanilla-server"

  # You can also use partial matches (e.g., any brand containing "forge")
  # The plugin will check for contains() matches if exact match fails

# Default fallback server when no brand matches
default-server: "lobby-server"

# Force route for some usage if needed
force-route:
  # format: playername: "servername"
  example: "test"
```

### Velocity Server Configuration (velocity.toml)
In your `velocity.toml`, configure the servers with the required modifications. For example:

```toml
[servers]
name1 = "ip"
name2 = "ip"
name3 = "ip"
name4 = "ip"
try = []   # maybe not keep your fallback empty may cause strange errors (tho it sometimes also happends if you don't)
```

## ⚡ Commands  
No more commands, I deleted them cause I don't want to maintain that when I can just use others.  
And also, this version just supports one server for each modloader brand, why bothering adding /hub to it? Just use SlashHUB plugin if you have normal servers besides modded servers.

## 🛡️ Future Enhancements (Planned Features)  
- **Customizable Messages** – Modify join/fallback messages (bro if you really need this, go check out my other project called [EventMessage](https://github.com/Aruvelut-123/EventMessage), it just does this thing!)  

## 🎮 Conclusion  
vLobbyConnect was the **ultimate lobby management solution** for Velocity servers, ensuring a smooth, version-compatible experience for all players. Download it today and **enhance your network’s performance and player experience!** 🚀  
Until I fork and modified it :)  
(Original one is still works tho if you need it, just not use that with this together)  