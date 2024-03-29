package me.petterim1.nocrashcrystals;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShulkerBox;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.HugeExplodeSeedParticle;
import cn.nukkit.math.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.longs.LongArraySet;

import java.util.List;

public class CrystalExplosion extends Explosion {

    private final Level level;
    private final Position source;
    private final List<Block> affectedBlocks;
    private final Entity what;
    private final double yield;

    public CrystalExplosion(Position center, Entity what, List<Block> blockList, double yield) {
        super(center, 6, what);
        this.level = center.getLevel();
        this.source = center;
        this.what = what;
        this.affectedBlocks = blockList;
        this.yield = yield;
    }

    @Override
    public boolean explodeB() {
        LongArraySet updateBlocks = new LongArraySet();
        double minX = NukkitMath.floorDouble(this.source.x - 11);
        double maxX = NukkitMath.ceilDouble(this.source.x + 11);
        double minY = NukkitMath.floorDouble(this.source.y - 11);
        double maxY = NukkitMath.ceilDouble(this.source.y + 11);
        double minZ = NukkitMath.floorDouble(this.source.z - 11);
        double maxZ = NukkitMath.ceilDouble(this.source.z + 11);
        AxisAlignedBB explosionBB = new SimpleAxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        Entity[] list = this.level.getNearbyEntities(explosionBB, this.what);
        int id = this.level.getBlockIdAt(this.what.getFloorX(), this.what.getFloorY(), this.what.getFloorZ());
        boolean doesDamage = id != BlockID.WATER && id != BlockID.STILL_WATER;
        for (Entity entity : list) {
            if (entity instanceof EntityEndCrystal) {
                continue;
            }
            double distance = entity.distance(this.source) / 12;
            if (distance <= 1) {
                Vector3 motion = entity.subtract(this.source).normalize();
                double impact = (1 - distance);
                int damage = doesDamage ? (int) (((impact * impact + impact) / 2) * 96 + 1) : 0;
                entity.attack(new EntityDamageByEntityEvent(this.what, entity, DamageCause.ENTITY_EXPLOSION, damage));
                if (!(entity instanceof EntityItem || entity instanceof EntityXPOrb)) {
                    entity.setMotion(motion.multiply(impact));
                }
            }
        }
        ItemBlock air = new ItemBlock(new BlockAir());
        BlockEntity container;
        for (Block block : this.affectedBlocks) {
            if ((container = block.getLevel().getBlockEntity(block)) instanceof InventoryHolder) {
                if (container instanceof BlockEntityShulkerBox) {
                    this.level.dropItem(block.add(0.5, 0.5, 0.5), block.toItem());
                    ((InventoryHolder) container).getInventory().clearAll();
                } else {
                    for (Item drop : ((InventoryHolder) container).getInventory().getContents().values()) {
                        this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                    }
                    ((InventoryHolder) container).getInventory().clearAll();
                }
            } else if (Math.random() * 100 < yield) {
                for (Item drop : block.getDrops(air)) {
                    this.level.dropItem(block.add(0.5, 0.5, 0.5), drop);
                }
            }
            this.level.setBlockAt((int) block.x, (int) block.y, (int) block.z, BlockID.AIR);
            Vector3 pos = new Vector3(block.x, block.y, block.z);
            for (BlockFace side : BlockFace.values()) {
                Vector3 sideBlock = pos.getSide(side);
                long index = Hash.hashBlock((int) sideBlock.x, (int) sideBlock.y, (int) sideBlock.z);
                if (!this.affectedBlocks.contains(sideBlock) && !updateBlocks.contains(index)) {
                    BlockUpdateEvent ev = new BlockUpdateEvent(this.level.getBlock(sideBlock));
                    this.level.getServer().getPluginManager().callEvent(ev);
                    if (!ev.isCancelled()) {
                        ev.getBlock().onUpdate(Level.BLOCK_UPDATE_NORMAL);
                    }
                    updateBlocks.add(index);
                }
            }
        }
        this.level.addParticle(new HugeExplodeSeedParticle(this.source));
        this.level.addLevelSoundEvent(source, LevelSoundEventPacket.SOUND_EXPLODE);
        return true;
    }
}
