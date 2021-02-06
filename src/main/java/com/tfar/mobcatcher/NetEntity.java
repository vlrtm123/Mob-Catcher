package com.tfar.mobcatcher;

import static com.tfar.mobcatcher.ItemNet.containsEntity;

import javax.annotation.Nonnull;

import com.feed_the_beast.mods.ftbchunks.api.ChunkDimPos;
import com.feed_the_beast.mods.ftbchunks.api.ClaimedChunk;
import com.feed_the_beast.mods.ftbchunks.impl.FTBChunksAPIImpl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public class NetEntity extends ProjectileItemEntity {

  public ItemStack stack = ItemStack.EMPTY;
  public LivingEntity shooter;

  public NetEntity(EntityType<? extends ProjectileItemEntity> entityType, World world) {
    super(entityType, world);
  }

  public NetEntity(double x, double y, double z, World world, ItemStack newStack) {
    super(MobCatcher.net, x, y, z, world);
    this.stack = newStack;
  }

  @Nonnull
  @Override
  protected Item getDefaultItem() {
    return MobCatcher.net_item;
  }

  public Boolean canEditHere(Entity player, BlockPos pos) {
    if (player instanceof ServerPlayerEntity) {
      ClaimedChunk claimedChunk = FTBChunksAPIImpl.INSTANCE.getManager().getChunk(new ChunkDimPos(player.world, pos));
      if (claimedChunk != null && !claimedChunk.canEdit((ServerPlayerEntity) player, player.world.getBlockState(pos))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Called when this EntityThrowable hits a block or entity.
   *
   * @param result
   */
  @Override
  protected void onImpact(@Nonnull RayTraceResult result) {
    if (world.isRemote || !this.isAlive())
      return;
    RayTraceResult.Type type = result.getType();
    boolean containsEntity = containsEntity(stack);
    if (containsEntity) {
      Entity entity = ItemNet.getEntityFromStack(stack, world, true);
      BlockPos pos;
      if (type == RayTraceResult.Type.ENTITY)
        pos = ((EntityRayTraceResult) result).getEntity().getPosition();
      else
        pos = ((BlockRayTraceResult) result).getPos();
      if (!canEditHere(this.func_234616_v_() , pos)) {
        world.addEntity(new ItemEntity(this.world, this.getPosX(), this.getPosY(), this.getPosZ(), stack));
        this.remove();
        return;
      }
      entity.setPositionAndRotation(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
      stack.removeChildTag(ItemNet.KEY);
      world.addEntity(entity);
      ItemEntity emptynet = new ItemEntity(this.world, this.getPosX(), this.getPosY(), this.getPosZ(),
          new ItemStack(stack.getItem()));
      world.addEntity(emptynet);
      if (stack.isDamageable()) {
        Entity owner = this.func_234616_v_();
        if (owner instanceof LivingEntity) {
          stack.damageItem(1, (LivingEntity) owner, playerEntity -> {
          });
        }
      }
    } else {
      if (type == RayTraceResult.Type.ENTITY) {
        EntityRayTraceResult entityRayTrace = (EntityRayTraceResult) result;
        Entity target = entityRayTrace.getEntity();
        if (target instanceof PlayerEntity || !target.isAlive())
          return;
        Item item = stack.getItem();
        if (item instanceof ItemNet && ((ItemNet) item).isBlacklisted(target.getType()))
          return;
        
        if (!canEditHere(this.func_234616_v_() , target.getPosition())) {
          world.addEntity(new ItemEntity(this.world, this.getPosX(), this.getPosY(), this.getPosZ(), stack));
          this.remove();
          return;
        }

        CompoundNBT nbt = ItemNet.getNBTfromEntity(target);
        ItemStack newStack = stack.copy();
        newStack.getOrCreateTag().put(ItemNet.KEY, nbt);
        ItemEntity itemEntity = new ItemEntity(target.world, target.getPosX(), target.getPosY(), target.getPosZ(),
            newStack);
        world.addEntity(itemEntity);
        target.remove();
      } else {
        ItemEntity emptynet = new ItemEntity(this.world, this.getPosX(), this.getPosY(), this.getPosZ(),
            new ItemStack(stack.getItem()));
        world.addEntity(emptynet);
      }
    }
    this.remove();
  }

  public void writeAdditional(CompoundNBT p_213281_1_) {
    super.writeAdditional(p_213281_1_);
    if (!stack.isEmpty()) {
      p_213281_1_.put("mobcatcher", stack.write(stack.getOrCreateTag()));
    }

  }

  public void readAdditional(CompoundNBT p_70037_1_) {
    super.readAdditional(p_70037_1_);
    stack = ItemStack.read(p_70037_1_.getCompound("mobcatcher"));
  }

  @Nonnull
  @Override
  public IPacket<?> createSpawnPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }
}
