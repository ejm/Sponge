/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.projectile.source.ProjectileSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.PhaseTracker;

@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileMixin extends ProjectileMixin {

    @Redirect(method = "tick()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;onHit(Lnet/minecraft/world/phys/HitResult;)V"
        )
    )
    private void impl$handleProjectileImpact(final ThrowableProjectile projectile, final HitResult movingObjectPosition) {
        if (((WorldBridge) this.level).bridge$isFake() || movingObjectPosition.getType() == HitResult.Type.MISS) {
            this.shadow$onHit(movingObjectPosition);
            return;
        }

        if (SpongeCommonEventFactory.handleCollideImpactEvent(projectile, (ProjectileSource) this.shadow$getOwner(), movingObjectPosition)) {
            this.shadow$discard();
        } else {
            this.shadow$onHit(movingObjectPosition);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;teleportEntity(Lnet/minecraft/world/entity/Entity;)V"))
    private void impl$createCauseFrameForGatewayTeleport(TheEndGatewayBlockEntity endGatewayTileEntity,
        Level var0, BlockPos var1, BlockState var2, Entity entityIn, TheEndGatewayBlockEntity var4) {
        if (this.shadow$getCommandSenderWorld().isClientSide) {
            return;
        }

        try (final CauseStackManager.StackFrame frame = PhaseTracker.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(endGatewayTileEntity);
            frame.pushCause(entityIn);
            TheEndGatewayBlockEntity.teleportEntity(var0, var1, var2, entityIn, var4);
        }
    }

}
