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
package org.spongepowered.common.mixin.api.minecraft.server.level;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.advancement.Advancement;
import org.spongepowered.api.advancement.AdvancementProgress;
import org.spongepowered.api.advancement.AdvancementTree;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.sound.music.MusicDisc;
import org.spongepowered.api.entity.living.player.CooldownTracker;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.entity.living.player.tab.TabList;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.message.PlayerChatEvent;
import org.spongepowered.api.event.world.ChangeWorldBorderEvent;
import org.spongepowered.api.network.ServerPlayerConnection;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.resourcepack.ResourcePack;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.api.world.border.WorldBorder;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.SpongeServer;
import org.spongepowered.common.accessor.server.network.ServerGamePacketListenerImplAccessor;
import org.spongepowered.common.accessor.world.level.border.WorldBorderAccessor;
import org.spongepowered.common.adventure.SpongeAdventure;
import org.spongepowered.common.bridge.server.PlayerAdvancementsBridge;
import org.spongepowered.common.bridge.server.ServerScoreboardBridge;
import org.spongepowered.common.bridge.server.level.ServerPlayerBridge;
import org.spongepowered.common.bridge.world.level.border.WorldBorderBridge;
import org.spongepowered.common.effect.particle.SpongeParticleHelper;
import org.spongepowered.common.effect.record.SpongeMusicDisc;
import org.spongepowered.common.entity.player.SpongeUserView;
import org.spongepowered.common.entity.player.tab.SpongeTabList;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.mixin.api.minecraft.world.entity.player.PlayerMixin_API;
import org.spongepowered.common.profile.SpongeGameProfile;
import org.spongepowered.common.resourcepack.SpongeResourcePack;
import org.spongepowered.common.util.BookUtil;
import org.spongepowered.common.util.NetworkUtil;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

@Mixin(net.minecraft.server.level.ServerPlayer.class)
public abstract class ServerPlayerMixin_API extends PlayerMixin_API implements ServerPlayer {

    // @formatter:off
    @Shadow @Final public MinecraftServer server;
    @Shadow @Final private PlayerAdvancements advancements;
    @Shadow public ServerGamePacketListenerImpl connection;

    @Shadow public abstract net.minecraft.server.level.ServerLevel shadow$getLevel();
    @Shadow public abstract void shadow$sendSystemMessage(final net.minecraft.network.chat.Component $$0);

    // @formatter:on


    @Shadow @Nullable private Vec3 enteredLavaOnVehiclePosition;
    private volatile Pointers api$pointers;

    private final TabList api$tabList = new SpongeTabList((net.minecraft.server.level.ServerPlayer) (Object) this);
    @Nullable private WorldBorderBridge api$worldBorder;

    @Override
    public ServerWorld world() {
        return (ServerWorld) this.shadow$getLevel();
    }

    @Override
    public void spawnParticles(final ParticleEffect particleEffect, final Vector3d position, final int radius) {
        if (this.impl$isFake) {
            return;
        }
        Objects.requireNonNull(particleEffect, "particleEffect");
        Objects.requireNonNull(position, "position");
        if (radius <= 0) {
            throw new IllegalArgumentException("The radius has to be greater then zero!");
        }
        final List<Packet<?>> packets = SpongeParticleHelper.toPackets(particleEffect, position);

        if (!packets.isEmpty()) {
            if (position.sub(this.shadow$getX(), this.shadow$getY(), this.shadow$getZ()).lengthSquared() < (long) radius * (long) radius) {
                for (final Packet<?> packet : packets) {
                    this.connection.send(packet);
                }
            }
        }
    }

    @Override
    public User user() {
        return SpongeUserView.create(this.uuid);
    }

    @Override
    public boolean isOnline() {
        if (this.impl$isFake) {
            return true;
        }
        return this.server.getPlayerList().getPlayer(this.uuid) == (net.minecraft.server.level.ServerPlayer) (Object) this;
    }

    @Override
    public GameProfile profile() {
        return SpongeGameProfile.of(this.shadow$getGameProfile());
    }

    @Override
    public void sendWorldType(final WorldType worldType) {
        if (this.impl$isFake) {
            return;
        }
        ((ServerPlayerBridge) this).bridge$sendViewerEnvironment((DimensionType) (Object) Objects.requireNonNull(worldType, "worldType"));
    }

    @Override
    public void spawnParticles(final ParticleEffect particleEffect, final Vector3d position) {
        if (this.impl$isFake) {
            return;
        }
        this.spawnParticles(particleEffect, position, Integer.MAX_VALUE);
    }

    @Override
    public ServerPlayerConnection connection() {
        return (ServerPlayerConnection) this.connection;
    }

    /**
     * @author Minecrell - August 22nd, 2016
     * @reason Use InetSocketAddress#getHostString() where possible (instead of
     *     inspecting SocketAddress#toString()) to support IPv6 addresses
     */
    @Overwrite
    public String getIpAddress() {
        return NetworkUtil.getHostString(((ServerGamePacketListenerImplAccessor) this.connection).accessor$connection().getRemoteAddress());
    }

    @Override
    public String identifier() {
        return this.uuid.toString();
    }

    @Override
    public void setScoreboard(final Scoreboard scoreboard) {
        Objects.requireNonNull(scoreboard, "scoreboard");

        ((ServerScoreboardBridge) ((ServerPlayerBridge) this).bridge$getScoreboard()).bridge$removePlayer((net.minecraft.server.level.ServerPlayer) (Object) this, true);
        ((ServerPlayerBridge) this).bridge$replaceScoreboard(scoreboard);
        ((ServerScoreboardBridge) ((ServerPlayerBridge) this).bridge$getScoreboard()).bridge$addPlayer((net.minecraft.server.level.ServerPlayer) (Object) this, true);
    }

    @Override
    public Component teamRepresentation() {
        return SpongeAdventure.asAdventure(this.shadow$getName());
    }

    @Override
    public Scoreboard scoreboard() {
        return ((ServerPlayerBridge) this).bridge$getScoreboard();
    }

    @Override
    public boolean kick() {
        return this.kick(Component.translatable("disconnect.disconnected"));
    }

    @Override
    public boolean kick(final Component message) {
        return ((ServerPlayerBridge) this).bridge$kick(Objects.requireNonNull(message, "message"));
    }

    @Override
    public void playMusicDisc(final Vector3i position, final MusicDisc recordType) {
        this.connection.send(SpongeMusicDisc.createPacket(Objects.requireNonNull(position, "position"), Objects.requireNonNull(recordType, "recordType")));
    }

    @Override
    public void stopMusicDisc(final Vector3i position) {
        this.connection.send(SpongeMusicDisc.createPacket(position, null));
    }

    @Override
    public void sendResourcePack(final ResourcePack pack) {
        this.connection.send(new ClientboundResourcePackPacket(((SpongeResourcePack) Objects.requireNonNull(pack, "pack")).getUrlString(), pack.hash().orElse(""), false, (net.minecraft.network.chat.Component) pack.prompt()));
    }

    @Override
    public TabList tabList() {
        return this.api$tabList;
    }

    @Override
    public boolean hasPlayedBefore() {
        final Instant instant = ((SpongeServer) this.shadow$getServer()).getPlayerDataManager().getFirstJoined(this.uniqueId()).get();
        final Instant toTheMinute = instant.truncatedTo(ChronoUnit.MINUTES);
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        final Duration timeSinceFirstJoined = Duration.of(now.minusMillis(toTheMinute.toEpochMilli()).toEpochMilli(), ChronoUnit.MINUTES);
        return timeSinceFirstJoined.getSeconds() > 0;
    }

    @Override
    public void sendBlockChange(final int x, final int y, final int z, final BlockState state) {
        this.connection.send(new ClientboundBlockUpdatePacket(new BlockPos(x, y, z), (net.minecraft.world.level.block.state.BlockState) state));
    }

    @Override
    public void resetBlockChange(final int x, final int y, final int z) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.shadow$getCommandSenderWorld(), new BlockPos(x, y, z)));
    }

    @Override
    public boolean respawn() {
        if (this.impl$isFake) {
            return false;
        }
        if (this.shadow$getHealth() > 0.0F) {
            return false;
        }
        this.connection.player = this.server.getPlayerList().respawn((net.minecraft.server.level.ServerPlayer) (Object) this, false);
        return true;
    }

    @Override
    public void simulateChat(final Component message, final Cause cause) {
        // TODO maybe deprecate & remove this as we cannot fake player messages anymore
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(cause, "cause");
        final PlayerChatEvent.Decorate event = SpongeEventFactory.createPlayerChatEventDecorate(cause, message, message, Optional.of(this));
        if (!SpongeCommon.post(event)) {
            final net.minecraft.network.chat.Component decoratedMessage = SpongeAdventure.asVanilla(event.message());
            final ChatType.Bound boundType = ChatType.bind(ChatType.CHAT, this.server.registryAccess(), this.getName());
            final var thisPlayer = (net.minecraft.server.level.ServerPlayer) (Object) this;
            this.server.getPlayerList().broadcastChatMessage(PlayerChatMessage.system(decoratedMessage.getString()), thisPlayer, boundType);
        }
    }

    @Override
    @NonNull
    public Optional<WorldBorder> worldBorder() {
        if (this.api$worldBorder == null) {
            return Optional.empty();
        }
        return Optional.of(this.api$worldBorder.bridge$asImmutable());
    }

    @Override
    public CooldownTracker cooldownTracker() {
        return (CooldownTracker) this.shadow$getCooldowns();
    }

    @Override
    public AdvancementProgress progress(final Advancement advancement) {
        return (AdvancementProgress) this.advancements.getOrStartProgress((net.minecraft.advancements.Advancement) Objects.requireNonNull(advancement, "advancement"));
    }

    @Override
    public Collection<AdvancementTree> unlockedAdvancementTrees() {
        if (this.impl$isFake) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(((PlayerAdvancementsBridge) this.advancements).bridge$getAdvancementTrees());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    @NonNull
    public Optional<WorldBorder> setWorldBorder(final @Nullable WorldBorder border) {
        if (this.impl$isFake) {
            return Optional.empty();
        }
        final Optional<WorldBorder> currentBorder = this.worldBorder();
        if (Objects.equals(currentBorder.orElse(null), border)) {
            return currentBorder; // do not fire an event since nothing would have changed
        }
        final ChangeWorldBorderEvent.Player event =
                SpongeEventFactory.createChangeWorldBorderEventPlayer(PhaseTracker.getCauseStackManager().currentCause(),
                        Optional.ofNullable(border), Optional.ofNullable(border), this, Optional.ofNullable(border));
        if (SpongeCommon.post(event)) {
            return currentBorder;
        }

        if (this.api$worldBorder != null) { // is the world border about to be unset?
            ((WorldBorderAccessor) this.api$worldBorder).accessor$listeners().remove(
                    ((ServerPlayerBridge) this).bridge$getWorldBorderListener()); // remove the listener, if so
        }
        final Optional<WorldBorder> toSet = event.newBorder();
        if (toSet.isPresent()) {
            final net.minecraft.world.level.border.WorldBorder mutableWorldBorder =
                    new net.minecraft.world.level.border.WorldBorder();
            this.api$worldBorder = ((WorldBorderBridge) mutableWorldBorder);
            this.api$worldBorder.bridge$applyFrom(toSet.get());
            mutableWorldBorder.addListener(((ServerPlayerBridge) this).bridge$getWorldBorderListener());
            this.connection.send(new ClientboundInitializeBorderPacket((net.minecraft.world.level.border.WorldBorder) this.api$worldBorder));
        } else { // unset the border if null
            this.api$worldBorder = null;
            this.connection.send(new ClientboundInitializeBorderPacket(this.shadow$getCommandSenderWorld().getWorldBorder()));
        }
        return toSet;

    }

    @Override
    protected Set<Value.Immutable<?>> api$getVanillaValues() {
        final Set<Value.Immutable<?>> values = super.api$getVanillaValues();

        values.add(this.requireValue(Keys.CHAT_COLORS_ENABLED).asImmutable());
        values.add(this.requireValue(Keys.CHAT_VISIBILITY).asImmutable());
        values.add(this.requireValue(Keys.GAME_MODE).asImmutable());
        values.add(this.requireValue(Keys.HAS_VIEWED_CREDITS).asImmutable());
        values.add(this.requireValue(Keys.LOCALE).asImmutable());
        values.add(this.requireValue(Keys.PREVIOUS_GAME_MODE).asImmutable());
        values.add(this.requireValue(Keys.SKIN_PARTS).asImmutable());
        values.add(this.requireValue(Keys.SPECTATOR_TARGET).asImmutable());
        // TODO ClassCastException: ServerStatsCounter -> StatsCounterBridge
        // values.add(this.requireValue(Keys.STATISTICS).asImmutable());
        values.add(this.requireValue(Keys.VIEW_DISTANCE).asImmutable());

        this.getValue(Keys.HEALTH_SCALE).map(Value::asImmutable).ifPresent(values::add);
        this.getValue(Keys.SKIN_PROFILE_PROPERTY).map(Value::asImmutable).ifPresent(values::add);

        return values;
    }

    // Audience

    @Override
    public @NotNull Pointers pointers() {
        Pointers pointers = this.api$pointers;
        if (pointers == null) {
            synchronized (this) {
                if (this.api$pointers == null) {
                    this.api$pointers = pointers = Pointers.builder()
                        .withDynamic(Identity.NAME, () -> ((net.minecraft.server.level.ServerPlayer) (Object) this).getGameProfile().getName())
                        .withDynamic(Identity.DISPLAY_NAME, () -> this.displayName().get())
                        .withDynamic(Identity.UUID, ((Entity) (Object) this)::getUUID)
                        .withDynamic(Identity.LOCALE, this::locale)
                        .withStatic(PermissionChecker.POINTER, permission -> SpongeAdventure.asAdventure(this.permissionValue(permission)))
                        .build();
                } else {
                    return this.api$pointers;
                }
            }
        }
        return pointers;
    }

    @Override
    @Deprecated
    public void sendMessage(final Identity identity, final Component message, final MessageType type) {
        if (this.impl$isFake) {
            return;
        }
        this.shadow$sendSystemMessage(SpongeAdventure.asVanilla(Objects.requireNonNull(message, "message")));
        // TODO chatMessage
        // this.shadow$sendChatMessage(PlayerChatMessage.unsigned(mcMessage), new ChatSender(mcIdentity, name, teamName));
    }

    @Override
    public void sendMessage(final @NotNull Component message) {
        if (this.impl$isFake) {
            return;
        }
        this.shadow$sendSystemMessage(SpongeAdventure.asVanilla(message));
    }

    @Override
    public void sendMessage(final @NotNull Component message, final net.kyori.adventure.chat.ChatType.@NotNull Bound boundChatType) {
        if (this.impl$isFake) {
            return;
        }
        this.connection.sendDisguisedChatMessage(SpongeAdventure.asVanilla(message), SpongeAdventure.asVanilla(this.level.registryAccess(), boundChatType));
    }

    @Override
    public void sendMessage(final @NotNull SignedMessage signedMessage, final net.kyori.adventure.chat.ChatType.@NotNull Bound boundChatType) {
        if (this.impl$isFake) {
            return;
        }
        // TODO: implement once we actually expose a way to get signed messages in-api
        this.connection.sendDisguisedChatMessage(
            SpongeAdventure.asVanilla(Objects.requireNonNullElse(signedMessage.unsignedContent(), Component.text(signedMessage.message()))),
            SpongeAdventure.asVanilla(this.level.registryAccess(), boundChatType)
        );
    }

    @Override
    public void deleteMessage(final SignedMessage.@NotNull Signature signature) {
        if (this.impl$isFake) {
            return;
        }
        this.connection.send(new ClientboundDeleteChatPacket(((MessageSignature) (Object) signature)
            .pack(((ServerGamePacketListenerImplAccessor) this.connection).accessor$messageSignatureCache())));
    }

    @Override
    public void sendActionBar(final Component message) {
        if (this.impl$isFake) {
            return;
        }
        this.connection.send(new ClientboundSetActionBarTextPacket(SpongeAdventure.asVanilla(Objects.requireNonNull(message, "message"))));
    }

    @Override
    public void sendPlayerListHeader(final Component header) {
        this.api$tabList.setHeader(Objects.requireNonNull(header, "header"));
    }

    @Override
    public void sendPlayerListFooter(final Component footer) {
        this.api$tabList.setFooter(Objects.requireNonNull(footer, "footer"));
    }

    @Override
    public void sendPlayerListHeaderAndFooter(final Component header, final Component footer) {
        this.api$tabList.setHeaderAndFooter(Objects.requireNonNull(header, "header"), Objects.requireNonNull(footer, "footer"));
    }

    @Override
    public void showTitle(final Title title) {
        if (this.impl$isFake) {
            return;
        }
        final Title.Times times = Objects.requireNonNull(title, "title").times();
        if (times != null) {
            this.connection.send(new ClientboundSetTitlesAnimationPacket(
                this.api$durationToTicks(times.fadeIn()),
                this.api$durationToTicks(times.stay()),
                this.api$durationToTicks(times.fadeOut())
            ));
        }
        this.connection.send(new ClientboundSetSubtitleTextPacket(SpongeAdventure.asVanilla(title.subtitle())));
        this.connection.send(new ClientboundSetTitleTextPacket(SpongeAdventure.asVanilla(title.title())));
    }

    @Override
    public <T> void sendTitlePart(final @NotNull TitlePart<T> part, final @NotNull T value) {
        if (this.impl$isFake) {
            return;
        }
        Objects.requireNonNull(value, "value");
        if (part == TitlePart.TITLE) {
            this.connection.send(new ClientboundSetTitleTextPacket(SpongeAdventure.asVanilla((Component) value)));
        } else if (part == TitlePart.SUBTITLE) {
            this.connection.send(new ClientboundSetSubtitleTextPacket(SpongeAdventure.asVanilla((Component) value)));
        } else if (part == TitlePart.TIMES) {
            final Title.Times times = (Title.Times) value;
            this.connection.send(new ClientboundSetTitlesAnimationPacket(this.api$durationToTicks(times.fadeIn()), this.api$durationToTicks(times.stay()), this.api$durationToTicks(times.fadeOut())));
        } else {
            throw new IllegalArgumentException("Unknown TitlePart '" + part + "'");
        }
    }

    @Override
    public void clearTitle() {
        if (this.impl$isFake) {
            return;
        }
        this.connection.send(new ClientboundClearTitlesPacket(false));
    }

    @Override
    public void resetTitle() {
        if (this.impl$isFake) {
            return;
        }
        this.connection.send(new ClientboundClearTitlesPacket(true));
    }

    @Override
    public void showBossBar(final BossBar bar) {
        if (this.impl$isFake) {
            return;
        }
        final ServerBossEvent vanilla = SpongeAdventure.asVanillaServer(Objects.requireNonNull(bar, "bar"));
        vanilla.addPlayer((net.minecraft.server.level.ServerPlayer) (Object) this);
    }

    @Override
    public void hideBossBar(final BossBar bar) {
        if (this.impl$isFake) {
            return;
        }
        final ServerBossEvent vanilla = SpongeAdventure.asVanillaServer(Objects.requireNonNull(bar, "bar"));
        vanilla.removePlayer((net.minecraft.server.level.ServerPlayer) (Object) this);
    }

    @Override
    public void playSound(final Sound sound) {
        this.playSound(Objects.requireNonNull(sound, "sound"), this.shadow$getX(), this.shadow$getY(), this.shadow$getZ());
    }

    private Holder<SoundEvent> api$resolveEvent(final @NonNull Sound sound) {
        final ResourceLocation eventId = SpongeAdventure.asVanilla(Objects.requireNonNull(sound, "sound").name());
        final var soundEventRegistry = SpongeCommon.vanillaRegistry(Registries.SOUND_EVENT);
        final SoundEvent event = soundEventRegistry.getOptional(eventId)
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(eventId));

        return soundEventRegistry.wrapAsHolder(event);
    }

    @Override
    public void playSound(final @NonNull Sound sound, final Sound.@NotNull Emitter emitter) {
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(emitter, "emitter");
        if (this.impl$isFake) {
            return;
        }

        final Entity tracked;
        if (emitter == Sound.Emitter.self()) {
            tracked = (Entity) (Object) this;
        } else if (emitter instanceof org.spongepowered.api.entity.Entity) {
            tracked = (Entity) emitter;
        } else {
            throw new IllegalArgumentException("Specified emitter '" + emitter + "' is not a Sponge Entity or Emitter.self(), was of type '" + emitter.getClass() + "'");
        }

        this.connection.send(new ClientboundSoundEntityPacket(
            this.api$resolveEvent(sound),
            SpongeAdventure.asVanilla(sound.source()),
            tracked,
            sound.volume(),
            sound.pitch(),
            sound.seed().orElseGet(() -> tracked.level.getRandom().nextLong())
        ));
    }

    @Override
    public void playSound(final Sound sound, final double x, final double y, final double z) {
        if (this.impl$isFake) {
            return;
        }
        final SoundSource source = SpongeAdventure.asVanilla(sound.source());
        final Holder<SoundEvent> event = this.api$resolveEvent(sound);
        final long random = sound.seed().orElseGet(() -> this.shadow$getLevel().getRandom().nextLong());
        this.connection.send(new ClientboundSoundPacket(event, source, x, y, z, sound.volume(), sound.pitch(), random));
    }

    @Override
    public void stopSound(final SoundStop stop) {
        if (this.impl$isFake) {
            return;
        }
        this.connection.send(new ClientboundStopSoundPacket(SpongeAdventure.asVanillaNullable(Objects.requireNonNull(stop, "stop").sound()), SpongeAdventure.asVanillaNullable(stop.source())));
    }

    @Override
    public void openBook(@NonNull final Book book) {
        if (this.impl$isFake) {
            return;
        }
        BookUtil.fakeBookView(Objects.requireNonNull(book, "book"), Collections.singletonList(this));
    }

    @Override
    public @NonNull Locale locale() {
        return ((ServerPlayerBridge) this).bridge$getLanguage();
    }

    private int api$durationToTicks(final Duration duration) {
        return (int) (duration.toMillis() / 50L);
    }
}
