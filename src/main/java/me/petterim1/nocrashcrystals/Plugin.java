package me.petterim1.nocrashcrystals;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockTNT;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.HugeExplodeSeedParticle;
import cn.nukkit.math.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Hash;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.longs.LongArraySet;

import java.util.Iterator;
import java.util.List;

public class Plugin extends PluginBase implements Listener {

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void EntityExplodeEvent(EntityExplodeEvent e) {
        if (e.getEntity() instanceof EntityEndCrystal) {
            e.setCancelled(true);

            final Level level = e.getPosition().getLevel();
            final Position source = e.getPosition();
            final List<Block> affectedBlocks = e.getBlockList();
            final Entity what = e.getEntity();
            final double yield = e.getYield();

            LongArraySet updateBlocks = new LongArraySet();
            double minX = NukkitMath.floorDouble(source.x - 12.0 - 1.0);
            double maxX = NukkitMath.ceilDouble(source.x + 12.0 + 1.0);
            double minY = NukkitMath.floorDouble(source.y - 12.0 - 1.0);
            double maxY = NukkitMath.ceilDouble(source.y + 12.0 + 1.0);
            double minZ = NukkitMath.floorDouble(source.z - 12.0 - 1.0);
            double maxZ = NukkitMath.ceilDouble(source.z + 12.0 + 1.0);
            AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
            Entity[] list = level.getNearbyEntities(explosionBB, what);
            int id = level.getBlockIdAt(what.chunk, what.getFloorX(), what.getFloorY(), what.getFloorZ());
            boolean doesDamage = id != BlockID.WATER && id != BlockID.STILL_WATER;

            for (Entity entity : list) {
                if (entity instanceof EntityEndCrystal) {
                    continue;
                }

                double distance = entity.distance(source) / 12.0;
                if (distance <= 1.0) {
                    Vector3 motion = entity.subtract(source).normalize();
                    double impact = 1.0 - distance;
                    int damage = doesDamage ? Math.max((int) ((impact * impact + impact) / 2.0 * 8.0 * 12.0 + 1.0), 0) : 0;
                    entity.attack(new EntityDamageByEntityEvent(what, entity, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, (float) damage));

                    if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
                        if (entity instanceof Player) {
                            int netheritePieces = 0;
                            Item[] var33 = ((Player) entity).getInventory().getArmorContents();

                            for (Item armor : var33) {
                                if (armor.getTier() == 6) {
                                    ++netheritePieces;
                                }
                            }

                            if (netheritePieces > 0) {
                                impact *= 1.0 - 0.1 * (double) netheritePieces;
                            }
                        }

                        entity.setMotion(entity.getMotion().add(motion.multiply(impact)));
                    }
                }
            }

            Item air = Item.get(0);

            for (Block block : affectedBlocks) {
                if (block.getId() == 26 && (block.getDamage() & 8) == 8) {
                    level.setBlockAt((int) block.x, (int) block.y, (int) block.z, BlockID.AIR);
                    continue;
                }

                BlockEntity container;
                if ((container = level.getBlockEntity(block)) instanceof InventoryHolder && !container.closed) {
                    if (level.getGameRules().getBoolean(GameRule.DO_TILE_DROPS)) {
                        Inventory inv = ((InventoryHolder) container).getInventory();
                        if (inv != null) {
                            inv.getViewers().clear();
                        }

                        if (container instanceof BlockEntityShulkerBox) {
                            level.dropItem(block.add(0.5, 0.5, 0.5), block.toItem());
                            ((BlockEntityShulkerBox) container).getInventory().clearAll();
                        } else {
                            container.onBreak();
                        }
                    }

                    container.close();
                } else if (block.alwaysDropsOnExplosion() || Math.random() * 100.0 < yield) {
                    if (level.getBlockIdAt((int) block.x, (int) block.y, (int) block.z) == 0) {
                        continue;
                    }

                    Item[] var43 = block.getDrops(air);

                    for (Item drop : var43) {
                        level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                    }
                }

                level.setBlockAt((int) block.x, (int) block.y, (int) block.z, BlockID.AIR);
                Vector3 pos = new Vector3(block.x, block.y, block.z);

                for (BlockFace side : BlockFace.values()) {
                    Vector3 sideBlock = pos.getSide(side);
                    long index = Hash.hashBlock((int) sideBlock.x, (int) sideBlock.y, (int) sideBlock.z);
                    if (!updateBlocks.contains(index)) {
                        Iterator<Block> var51 = affectedBlocks.iterator();

                        Block affected;
                        do {
                            if (!var51.hasNext()) {
                                BlockUpdateEvent ev = new BlockUpdateEvent(level.getBlock(sideBlock));
                                level.getServer().getPluginManager().callEvent(ev);
                                if (!ev.isCancelled()) {
                                    ev.getBlock().onUpdate(Level.BLOCK_UPDATE_NORMAL);
                                }

                                updateBlocks.add(index);
                                break;
                            }

                            affected = var51.next();
                        } while (affected.x != sideBlock.x || affected.y != sideBlock.y || affected.z != sideBlock.z);
                    }
                }
            }

            level.addParticle(new HugeExplodeSeedParticle(source));
            level.addLevelSoundEvent(source, LevelSoundEventPacket.SOUND_EXPLODE);
        }
    }
}
