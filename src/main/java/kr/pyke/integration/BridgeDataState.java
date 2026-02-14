package kr.pyke.integration;

import kr.pyke.util.PLATFORM;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BridgeDataState extends SavedData {
    public record TokenInfo(String accessToken, String refreshToken) { }

    public final Map<UUID, Map<PLATFORM, TokenInfo>> playerTokens = new HashMap<>();

    public BridgeDataState() { }

    public TokenInfo getToken(UUID uuid, PLATFORM platform) {
        return playerTokens.getOrDefault(uuid, new HashMap<>()).get(platform);
    }

    public void setToken(UUID uuid, PLATFORM platform, TokenInfo tokenInfo) {
        playerTokens.computeIfAbsent(uuid, k -> new HashMap<>()).put(platform, tokenInfo);
        this.setDirty();
    }

    public static BridgeDataState fromNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        BridgeDataState state = new BridgeDataState();
        CompoundTag tokensNbt = nbt.getCompound("playerTokens");

        for (String uuidKey : tokensNbt.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidKey);
            CompoundTag platformTags = tokensNbt.getCompound(uuidKey);

            Map<PLATFORM, TokenInfo> map = new HashMap<>();
            for (PLATFORM platform : PLATFORM.values()) {
                if (platformTags.contains(platform.name())) {
                    CompoundTag tag = platformTags.getCompound(platform.name());
                    map.put(platform, new TokenInfo(tag.getString("access"), tag.getString("refresh")));
                }
            }
            state.playerTokens.put(uuid, map);
        }

        return state;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag tokensNbt = new CompoundTag();

        playerTokens.forEach((uuid, map) -> {
            CompoundTag platformTags = new CompoundTag();

            map.forEach((platform, tokenInfo) -> {
                CompoundTag tag = new CompoundTag();
                tag.putString("access", tokenInfo.accessToken);
                tag.putString("refresh", tokenInfo.refreshToken);
                platformTags.put(platform.name(), tag);
            });

            tokensNbt.put(uuid.toString(), platformTags);
        });

        nbt.put("playerTokens", tokensNbt);
        return nbt;
    }

    public static BridgeDataState getServerState(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();

        return storage.computeIfAbsent(new SavedData.Factory<>(BridgeDataState::new, BridgeDataState::fromNbt, null), "cheese_bridge");
    }
}