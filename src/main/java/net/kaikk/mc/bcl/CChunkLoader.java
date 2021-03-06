package net.kaikk.mc.bcl;


import com.flowpowered.math.vector.Vector3d;
import net.kaikk.mc.bcl.config.Config;
import net.kaikk.mc.bcl.datastore.DataStoreManager;
import net.kaikk.mc.bcl.forgelib.ChunkLoader;
import net.kaikk.mc.bcl.utils.BCLPermission;
import net.kaikk.mc.bcl.utils.InventoryCloseAfterADelayTask;
import net.kaikk.mc.bcl.utils.Messenger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.ArmorStand;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.*;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.LiteralText;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@XmlRootElement
@XmlAccessorType(value = XmlAccessType.NONE)
public class CChunkLoader extends ChunkLoader {

    final public static UUID adminUUID = new UUID(0, 1);
    private UUID owner;
    private Location<World> loc;
    private Date creationDate;
    private boolean isAlwaysOn;
    private String serverName;
    private boolean active;
    private ArmorStand armorStand;
    private Task armorStandHideTask;

    public CChunkLoader() {
    }

    public CChunkLoader(int chunkX, int chunkZ, String worldName, byte range, UUID owner, Location<World> loc, Date creationDate, boolean isAlwaysOn,
                        String serverName) {
        super(chunkX, chunkZ, worldName, range);
        this.owner = owner;
        this.loc = loc;
        this.creationDate = creationDate;
        this.isAlwaysOn = isAlwaysOn;
        this.serverName = serverName;
    }

    public CChunkLoader(int chunkX, int chunkZ, String worldName, byte range, UUID owner, Location<World> loc, Date creationDate,
                        boolean isAlwaysOn) {
        this(chunkX, chunkZ, worldName, range, owner, loc, creationDate, isAlwaysOn, Config.getConfig().get().getNode("ServerName").getString());

    }

    public CChunkLoader(String location, byte range, UUID owner, Date creationDate, boolean isAlwaysOn, String serverName) {
        super(0, 0, "", range);
        this.serverName = serverName;
        this.setLocationString(location);
        this.owner = owner;
        this.creationDate = creationDate;
        this.isAlwaysOn = isAlwaysOn;
    }

    private static void addInventoryOption(Inventory inventory, int position, ItemType icon, Text text, int size) {
        Iterable<Slot> slotIterable = inventory.slots();
        ItemStack itemStack = ItemStack.builder().itemType(icon).quantity(size).build();
        itemStack.offer(Keys.DISPLAY_NAME, text);
        Integer iter = 0;
        for (Slot slot : slotIterable) {
            if (iter == position) {
                slot.set(itemStack);
            }
            iter++;
        }

    }

    public static Consumer<ClickInventoryEvent> createClickEventConsumer(CChunkLoader chunkLoader) {
        return event -> {
            event.setCancelled(true);
            if (event.getCause().last(Player.class).isPresent()) {
                Player player = event.getCause().last(Player.class).get();

                if (chunkLoader == null) {
                    return;
                }

                if (chunkLoader.isAdminChunkLoader() && !player.hasPermission(BCLPermission.ABILITY_ADMINLOADER)) {
                    Messenger.sendNoPermission(player);
                    return;
                }

                if (!player.getUniqueId().equals(chunkLoader.getOwner()) && !player.hasPermission(BCLPermission.ABILITY_EDIT_OTHERS)) {
                    player.sendMessage(Text.of(TextColors.RED, "You can't edit others' chunk loaders."));
                    return;
                }

                if (!event.getTransactions().get(0).getOriginal().get(Keys.DISPLAY_NAME).isPresent()) {
                    return;
                }
                String firstChar = String.valueOf(event.getTransactions().get(0).getOriginal().get(Keys.DISPLAY_NAME).get().toPlain().charAt(5));
                Integer pos;

                try {
                    pos = Integer.parseInt(firstChar);
                } catch (NumberFormatException e) {
                    pos = 0;
                }


                //todo these 2 might be more useful combined as most of the logic is the same!.
                // -1 == create new chunkloader (as the old chunkLoaders range was 0)
                if (chunkLoader.getRange() == -1) {
                    pos = chunkLoader.radiusFromSide(pos);
                    if (!chunkLoader.isAdminChunkLoader() && !player.hasPermission(BCLPermission.ABILITY_UNLIMITED)) {

                        int needed = (1 + (pos * 2)) * (1 + (pos * 2));
                        int available;
                        if (chunkLoader.isAlwaysOn()) {
                            available = DataStoreManager.getDataStore().getAlwaysOnFreeChunksAmount(chunkLoader.getOwner());
                        } else {
                            available = DataStoreManager.getDataStore().getOnlineOnlyFreeChunksAmount(chunkLoader.getOwner());
                        }

                        if (needed > available) {
                            player.sendMessage(Text.of(TextColors.RED,
                                    "You don't have enough free chunks! Needed: " + needed + ". Available: " + available + "."));
                            closeInventory(player);
                            return;
                        }
                    }

                    chunkLoader.setRange(Byte.valueOf("" + pos));
                    chunkLoader.setCreationDate(new Date());
                    String type = chunkLoader.isAdminChunkLoader() ? "admin loader " : "chunk loader";
                    BetterChunkLoader.instance().getLogger()
                            .info(player.getName() + " made a new " + type + " at " + chunkLoader.getLocationString() + " with range " + pos);
                    DataStoreManager.getDataStore().addChunkLoader(chunkLoader);
                    closeInventory(player);
                    player.sendMessage(Text.of(TextColors.GOLD, "Chunk Loader created."));
                    return;
                }
                if (chunkLoader.getRange() != -1) {
                    if (pos == 0) {
                        // remove the chunk loader
                        DataStoreManager.getDataStore().removeChunkLoader(chunkLoader);
                        if (chunkLoader.getOwner().equals(player.getUniqueId())) {
                            player.sendMessage(Text.of(TextColors.RED, "You disabled your chunk loader at " + chunkLoader.getLocationString() + "."));
                        } else {
                            player.sendMessage(Text.of(TextColors.RED,
                                    player.getName() + " disabled your chunk loader at " + chunkLoader.getLocationString() + "."));
                        }
                        closeInventory(player);
                    } else {
                        pos = chunkLoader.radiusFromSide(pos);
                        // if higher range, check if the player has enough free chunks
                        if (!chunkLoader.isAdminChunkLoader() && !player.hasPermission(BCLPermission.ABILITY_UNLIMITED)) {

                            if (pos > chunkLoader.getRange()) {
                                int needed = ((1 + (pos * 2)) * (1 + (pos * 2))) - chunkLoader.size();
                                int available;
                                if (chunkLoader.isAlwaysOn()) {
                                    available = DataStoreManager.getDataStore().getAlwaysOnFreeChunksAmount(chunkLoader.getOwner());
                                } else {
                                    available = DataStoreManager.getDataStore().getOnlineOnlyFreeChunksAmount(chunkLoader.getOwner());
                                }

                                if (needed > available) {
                                    if (chunkLoader.getOwner().equals(player.getUniqueId())) {
                                        player.sendMessage(Text.of(TextColors.RED,
                                                "You don't have enough free chunks! Needed: " + needed + ". Available: " + available + "."));
                                    } else {
                                        player.sendMessage(Text.of(TextColors.RED,
                                                chunkLoader.getOwnerName() + " don't have enough free chunks! Needed: " + needed + ". Available: "
                                                        + available + "."));
                                    }
                                    closeInventory(player);
                                    return;
                                }
                            }
                        }

                        BetterChunkLoader.instance().getLogger()
                                .info(player.getName() + " edited " + chunkLoader.getOwnerName() + "'s chunk loader at " + chunkLoader
                                        .getLocationString() + " range from " + chunkLoader.getRange() + " to " + pos);
                        DataStoreManager.getDataStore().changeChunkLoaderRange(chunkLoader, Byte.valueOf("" + pos));
                        player.sendMessage(Text.of(TextColors.GOLD, "Chunk Loader updated."));
                        closeInventory(player);
                    }
                }
            }
        };
    }

    private static void closeInventory(final Player p) {
        Task.builder().execute(new InventoryCloseAfterADelayTask(p))
                .delay(10, TimeUnit.MILLISECONDS)
                .name("Closing players inventory.").submit(BetterChunkLoader.instance());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.getOwnerLastPlayed() > Config.getConfig().get().getNode("MaxHoursOffline").getInt() * 3600000L;
    }

    public String getServer() {
        return this.serverName;
    }

    public User getOfflinePlayer() {
        Optional<UserStorageService> userStorage = Sponge.getServiceManager().provide(UserStorageService.class);
        Optional<User> optUser = userStorage.get().get(this.owner);
        if (optUser.isPresent()) {
            User user = optUser.get();
            return user;
        }
        return null;
    }

    public Player getPlayer() {
        Optional<Player> onlinePlayer = Sponge.getServer().getPlayer(this.owner);
        if (onlinePlayer.isPresent()) {
            return onlinePlayer.get();
        }
        return null;
    }

    public long getOwnerLastPlayed() {
        if (this.isAdminChunkLoader()) {
            return System.currentTimeMillis();
        }
        return BetterChunkLoader.getPlayerLastPlayed(owner);
    }

    public String getOwnerName() {
        if (this.isAdminChunkLoader()) {
            return "Admin";
        }
        return this.getOfflinePlayer().getName();
    }

    public int side() {
        return 1 + (super.getRange() * 2);
    }

    public int size() {
        return this.side() * this.side();
    }

    public String sizeX() {
        return this.side() + "x" + this.side();
    }

    public Text info() {
        TextColor baseColor = TextColors.YELLOW;
        TextColor highlightColor = TextColors.GREEN;
        return Text.builder("== Chunk Loader Info ==").color(TextColors.GOLD).build().concat(Text.NEW_LINE)
                .concat(Text.of(highlightColor, "Owner: ")).concat(Text.of(baseColor, this.getOwnerName())).concat(Text.NEW_LINE)
                .concat(Text.of(highlightColor, "Position: ")).concat(Text.of(baseColor, this.getLocationString())).concat(Text.NEW_LINE)
                // .concat(Text.builder("Chunk: "+this.worldName+":"+this.chunkX+","+this.chunkZ).build()).concat(Text.NEW_LINE)
                .concat(Text.of(highlightColor, "Size: ")).concat(Text.of(baseColor, this.sizeX()));
    }

    public boolean isLoadable() {
        return (this.isOwnerOnline() || (this.isAlwaysOn && !this.isExpired())) && this.blockCheck();
    }

    public boolean blockCheck() {
        if (this.loc.getBlock() == null) {
            return false;
        }
        if (isAlwaysOn) {

            return this.loc.getBlock().getType().equals(BlockTypes.DIAMOND_BLOCK);
        } else {
            return this.loc.getBlock().getType().equals(BlockTypes.IRON_BLOCK);
        }
    }

    public boolean isOwnerOnline() {
        return this.getPlayer() != null;
    }

    @Override
    public String toString() {
        return (this.isAlwaysOn ? "y" : "n") + " - " + this.sizeX() + " - " + this.loc.toString() + " - " + this.serverName;
    }


    public Text toText(boolean showUser, Subject subject) {
        Text seperator = Text.builder(" - ").color(isAlwaysOn ? TextColors.GREEN : TextColors.YELLOW).build();
        Text.Builder builder = Text.builder();

        builder.append(Text.builder("* ").onHover(TextActions.showText(Text.of(active ? "Active" : "Inactive"))).color(active ? TextColors.GREEN : TextColors.RED).build());

        if (this.isAlwaysOn) {
            builder.append(Text.builder("[Offline] ").color(TextColors.GREEN).onHover(TextActions.showText(Text.of("The chunk is always loaded."))).build());
        } else {
            builder.append(Text.builder("[Online ] ").color(TextColors.YELLOW).onHover(TextActions.showText(Text.of("The chunk is only loaded when the owner is online."))).build());
        }

        if (showUser) {
            builder.append(Text.builder(getOwnerName()).color(TextColors.GOLD).build());
            builder.append(seperator);
        }

        LiteralText.Builder locationBuilder = Text.builder(loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " in " + loc.getExtent().getName()).color(TextColors.GOLD);

        if (subject.hasPermission("minecraft.command.tp")) {
            if (subject instanceof Player && ((Player) subject).getLocation().getExtent().equals(loc.getExtent())) {
                locationBuilder.onHover(TextActions.showText(Text.of("Teleport to the chunkloader"))).onClick(TextActions.runCommand("/tp " + (loc.getX() + 0.5) + " " + (loc.getY() + 1) + " " + (loc.getZ() + 0.5)));
            } else {
                locationBuilder.onHover(TextActions.showText(Text.of("Please enter the dimension before you can teleport to it")));
            }
        }

        return builder
                .append(Text.builder(sizeX(getRange())).color(TextColors.GOLD).build())
                .append(seperator)
                .append(locationBuilder.build())
                .toText();

        // (this.isAlwaysOn ? "y" : "n") + " - " + this.sizeX() + " - " + this.loc.toString() + " - " + this.serverName;
    }


    public UUID getOwner() {
        return owner;
    }

    @XmlAttribute(name = "own")
    void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Location<World> getLoc() {
        return loc;
    }

    public String getLocationString() {
        return loc.getExtent().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    @XmlAttribute(name = "loc")
    public void setLocationString(String location) {
        try {
            if (this.serverName.equalsIgnoreCase(Config.getConfig().get().getNode("ServerName").getString())) {
                String[] s = location.split(":");
                String[] coords = s[1].split(",");
                Integer x = Integer.valueOf(coords[0]);
                Integer y = Integer.valueOf(coords[1]);
                Integer z = Integer.valueOf(coords[2]);
                super.worldName = s[0];
                this.loc = new Location<World>(Sponge.getServer().getWorld(s[0]).get(), x, y, z);
                super.chunkX = this.loc.getChunkPosition().getX();
                super.chunkZ = this.loc.getChunkPosition().getZ();
            } else {
                super.worldName = "Another server...";
            }
        } catch (Exception e) {
            throw new RuntimeException("Wrong chunk loader location: " + location);
        }
    }

    public String getPrettyLocationString() {
        return loc.getExtent().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";

    }

    public String getServerName() {
        return serverName;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    @XmlAttribute(name = "date")
    public void setCreationDate(Date date) {
        this.creationDate = date;
    }

    public boolean isAlwaysOn() {
        return isAlwaysOn;
    }

    @XmlAttribute(name = "aon")
    void setAlwaysOn(boolean isAlwaysOn) {
        this.isAlwaysOn = isAlwaysOn;
    }


    /**
     * Shows the chunk loader's user interface to the specified player
     */
    void showUI(Player player) {
        String title = (this.range != -1 ? "BCL:" + this.getOwnerName() + "@" + getLocationString() :
                "New " + (this.isAdminChunkLoader() ? "Admin " : "") + "BetterChunkLoader");
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        InventoryArchetype inventoryArchetype = InventoryArchetype.builder()
                .from(InventoryArchetypes.MENU_ROW)
                .property(CChunkLoaderInvProp.of(this)).build("archid", "archname");

        Inventory inventory = Inventory.builder()
                .of(inventoryArchetype)
                .property(InventoryTitle.PROPERTY_NAME, InventoryTitle.of(Text.of(title)))
                .listener(ClickInventoryEvent.class, createClickEventConsumer(this))
                .build(BetterChunkLoader.instance());
        if (this.range != -1) {
            addInventoryOption(inventory, 0, ItemTypes.BARRIER, Text.of("Remove"), 1);
        }

        int pos = 2;
        int available;
        if (isAlwaysOn()) {
            available = DataStoreManager.getDataStore().getAlwaysOnFreeChunksAmount(player.getUniqueId());
        } else {
            available = DataStoreManager.getDataStore().getOnlineOnlyFreeChunksAmount(player.getUniqueId());
        }

        for (byte i = 0; i < 5; ) {
            int needed = side(i) * side(i);

            ItemType type;
            Text.Builder builder = Text.builder("Size " + this.sizeX(i));

            if (this.getRange() == i) {
                builder.color(TextColors.GREEN);
                type = ItemTypes.TNT_MINECART;
            } else if (needed > available) {
                builder.color(TextColors.RED);
                type = ItemTypes.MINECART;
            } else {
                builder.color(TextColors.YELLOW);
                type = ItemTypes.CHEST_MINECART;
            }

            addInventoryOption(inventory, pos, type, builder.build(), side(i));
            pos++;
            i++;
        }

        player.openInventory(inventory, Cause.of(NamedCause.simulated(player)));
    }

    public int radiusFromSide(int side) {
        return (side - 1) / 2;
    }

    public int side(byte i) {
        return 1 + (i * 2);
    }

    private String sizeX(byte i) {
        return this.side(i) + "x" + this.side(i);
    }

    @Override
    public byte getRange() {
        return super.range;
    }

    public void hideName() {
        if (armorStand != null) {
            armorStand.remove();
            armorStandHideTask.cancel();
            armorStand = null;
            armorStandHideTask = null;
        }
    }

    public void showName() {
        if (armorStand == null) {
            World world = loc.getExtent();

            Vector3d position = loc.getPosition().add(0.5, -0.75, 0.5);
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand && !entity.isRemoved() && entity.getLocation().getPosition().equals(position)) {
                    armorStand = (ArmorStand) entity;
                    break;
                }
            }

            if (armorStand == null) {
                armorStand = (ArmorStand) world.createEntity(EntityTypes.ARMOR_STAND, position);
                world.spawnEntity(armorStand, Cause.source(SpawnCause.builder().type(SpawnTypes.PLUGIN).build()).build());
            }

            armorStand.offer(Keys.HAS_GRAVITY, false);
            armorStand.offer(Keys.INVISIBLE, true);
            armorStand.offer(Keys.INVULNERABILITY_TICKS, Integer.MAX_VALUE);
            armorStand.offer(Keys.DISPLAY_NAME, Text.of(isAlwaysOn ? TextColors.GREEN : TextColors.YELLOW, "Chunkloader"));
            armorStand.offer(Keys.CUSTOM_NAME_VISIBLE, true);
        }

        if (armorStandHideTask != null) {
            armorStandHideTask.cancel();
        }

        armorStandHideTask = Task.builder()
                .execute(this::hideName)
                .delay(5, TimeUnit.SECONDS)
                .name("Show name").submit(BetterChunkLoader.instance());
    }

    @XmlAttribute(name = "r")
    public void setRange(byte range) {
        super.range = range;
    }

    public boolean isAdminChunkLoader() {
        return adminUUID.equals(this.owner);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (!active) {
            hideName();
        } else {

            loc.getExtent().playSound(SoundTypes.ITEM_ARMOR_EQUIP_DIAMOND, loc.getPosition(), 1, 2);
        }

        this.active = active;
    }
}
