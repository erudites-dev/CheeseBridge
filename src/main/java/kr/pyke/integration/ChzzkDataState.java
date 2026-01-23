package kr.pyke.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChzzkDataState extends SavedData {
    public record TokenInfo(String accessToken, String refreshToken) { }

    public final Map<UUID, TokenInfo> playerTokens = new HashMap<>();

    public ChzzkDataState() { }

    public static ChzzkDataState fromNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        ChzzkDataState state = new ChzzkDataState();
        CompoundTag tokensNbt = nbt.getCompound("playerTokens");

        for (String key : tokensNbt.getAllKeys()) {
            CompoundTag tokenTag = tokensNbt.getCompound(key);
            String accessToken = tokenTag.getString("accessToken");
            String refreshToken = tokenTag.getString("refreshToken");

            if (!accessToken.isEmpty() && !refreshToken.isEmpty()) {
                state.playerTokens.put(UUID.fromString(key), new TokenInfo(accessToken, refreshToken));
            }
        }

        return state;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag tokensNbt = new CompoundTag();

        playerTokens.forEach((uuid, tokenInfo) -> {
            CompoundTag tokenTag = new CompoundTag();
            tokenTag.putString("accessToken", tokenInfo.accessToken);
            tokenTag.putString("refreshToken", tokenInfo.refreshToken);

            tokensNbt.put(uuid.toString(), tokenTag);
        });

        nbt.put("playerTokens", tokensNbt);
        return nbt;
    }

    public static ChzzkDataState getServerState(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();

        return storage.computeIfAbsent(new SavedData.Factory<>(ChzzkDataState::new, ChzzkDataState::fromNbt, null), "Cheese_Bridge");
    }
}