package me.mandoisgood.elemental;


import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.*;
public class ElementalClashPlugin extends JavaPlugin implements Listener {
    private static final int GAME_DURATION = 20 * 60;
    private static final int CONTROL_POINT_RADIUS = 10;
    private static final int CONTROL_POINT_COUNT = 4;

    private List<Team> teams;
    private Map<Location, ControlPoint> controlPoints;
    private int taskId = -1;

    private Random random;

    @Override
    public void onEnable() {
        teams = new ArrayList<>();
        controlPoints = new HashMap<>();
        random = new Random();

        getServer().getPluginManager().registerEvents(this, this);

        // Register recipes
        registerRecipes();

        // Start a new game
        startGame();
    }

    private void startGame() {
        // Reset game state
        teams.clear();
        controlPoints.clear();


        teams.add(new Team(this, TextColor.color(255, 0, 0), "Fire"));
        teams.add(new Team(this, TextColor.color(0, 0, 255), "Water"));
        teams.add(new Team(this, TextColor.color(0, 255, 0), "Earth"));
        teams.add(new Team(this, TextColor.color(128, 128, 128), "Air"));


        generateControlPoints();


        assignPlayersToTeams();


        startCountdown();
    }

    private void generateControlPoints() {
        World world = getServer().getWorlds().get(0);
        int centerX = world.getSpawnLocation().getBlockX();
        int centerZ = world.getSpawnLocation().getBlockZ();

        for (int i = 0; i < CONTROL_POINT_COUNT; i++) {
            double angle = 2 * Math.PI * i / CONTROL_POINT_COUNT;
            int x = centerX + (int) (Math.cos(angle) * 100);
            int z = centerZ + (int) (Math.sin(angle) * 100);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location location = new Location(world, x, y, z);
            ControlPoint controlPoint = new ControlPoint(location, CONTROL_POINT_RADIUS);
            controlPoints.put(location, controlPoint);
        }
    }

    private void assignPlayersToTeams() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players, random);

        Iterator<Team> teamIterator = teams.iterator();

        for (Player player : players) {
            Team team = teamIterator.next();
            team.addPlayer(player);
            teamIterator = teamIterator.hasNext() ? teamIterator : teams.iterator();
        }
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown == 0) {
                    startGame();
                    this.cancel();
                    return;
                }

                Bukkit.getServer().sendMessage(Component.text("Game starts in " + countdown + " seconds!", NamedTextColor.YELLOW));
                countdown--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void registerRecipes() {
        // Fire Sword recipe
        ItemStack fireSwordItem = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta fireSwordMeta = fireSwordItem.getItemMeta();
        fireSwordMeta.displayName(Component.text("Fire Sword", NamedTextColor.RED));
        fireSwordItem.setItemMeta(fireSwordMeta);

        NamespacedKey fireSwordKey = new NamespacedKey(this, "fire_sword");
        ShapedRecipe fireSwordRecipe = new ShapedRecipe(fireSwordKey, fireSwordItem);
        fireSwordRecipe.shape("*#*", "*%*", "*#*");
        fireSwordRecipe.setIngredient('*', Material.BLAZE_ROD);
        fireSwordRecipe.setIngredient('#', Material.IRON_INGOT);
        fireSwordRecipe.setIngredient('%', Material.FIRE_CHARGE);

        getServer().addRecipe(fireSwordRecipe);


    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team playerTeam = getPlayerTeam(player);

        if (playerTeam == null) {
            // Assign player to a team
            playerTeam = getSmallestTeam();
            playerTeam.addPlayer(player);
        }

        // Teleport player to team spawn
        player.teleport(playerTeam.getSpawnLocation());

        // Give player team items
        player.getInventory().addItem(playerTeam.getKitItems());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();

        ControlPoint controlPoint = controlPoints.get(location);
        if (controlPoint != null) {
            Team playerTeam = getPlayerTeam(player);
            if (playerTeam != null) {
                controlPoint.capturePoint(playerTeam);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            Team damagerTeam = getPlayerTeam(damager);
            Team victimTeam = getPlayerTeam(victim);

            if (damagerTeam != null && victimTeam != null && damagerTeam != victimTeam) {
                // Friendly fire is disabled
                event.setCancelled(true);
            }
        }
    }

    private Team getPlayerTeam(Player player) {
        for (Team team : teams) {
            if (team.hasPlayer(player)) {
                return team;
            }
        }
        return null;
    }

    private Team getSmallestTeam() {
        Team smallestTeam = null;
        int minSize = Integer.MAX_VALUE;

        for (Team team : teams) {
            int teamSize = team.getPlayers().size();
            if (teamSize < minSize) {
                smallestTeam = team;
                minSize = teamSize;
            }
        }

        return smallestTeam;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("startgame")) {
            startGame();
            return true;
        }

        return false;
    }

    private class Team {
        private final ElementalClashPlugin plugin;
        private final TextColor color;
        private final String name;
        private final List<Player> players;
        private final Location spawnLocation;
        private final ItemStack[] kitItems;

        public Team(ElementalClashPlugin plugin, TextColor color, String name) {
            this.plugin = plugin;
            this.color = color;
            this.name = name;
            this.players = new ArrayList<>();

            World world = plugin.getServer().getWorlds().get(0);
            int x = plugin.random.nextInt(200) - 100;
            int z = plugin.random.nextInt(200) - 100;
            int y = world.getHighestBlockYAt(x, z) + 1;
            this.spawnLocation = new Location(world, x, y, z);

            this.kitItems = new ItemStack[] {
                    new ItemStack(Material.WOODEN_SWORD),
                    new ItemStack(Material.WOODEN_PICKAXE),
                    new ItemStack(Material.COOKED_BEEF, 16)
            };
        }

        public void addPlayer(Player player) {
            players.add(player);
            player.playerListName(Component.text(player.getName(), color));
        }

        public boolean hasPlayer(Player player) {
            return players.contains(player);
        }

        public List<Player> getPlayers() {
            return players;
        }

        public Location getSpawnLocation() {
            return spawnLocation;
        }

        public ItemStack[] getKitItems() {
            return kitItems;
        }
    }

    private class ControlPoint {
        private final Location location;
        private final int radius;
        private Team controllingTeam;

        public ControlPoint(Location location, int radius) {
            this.location = location;
            this.radius = radius;
            this.controllingTeam = null;
        }

        public void capturePoint(Team team) {
            if (controllingTeam != null && controllingTeam == team) {
                
                return;
            }

            controllingTeam = team;
            team.getPlayers().forEach(player -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            });

            Bukkit.getServer().sendMessage(Component.text(team.name + " team has captured a control point!", team.color));
        }
    }
}