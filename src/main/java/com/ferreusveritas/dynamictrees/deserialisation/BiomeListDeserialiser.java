package com.ferreusveritas.dynamictrees.deserialisation;

import com.ferreusveritas.dynamictrees.api.applier.Applier;
import com.ferreusveritas.dynamictrees.api.applier.PropertyApplierResult;
import com.ferreusveritas.dynamictrees.api.applier.VoidApplier;
import com.ferreusveritas.dynamictrees.deserialisation.result.JsonResult;
import com.ferreusveritas.dynamictrees.deserialisation.result.Result;
import com.ferreusveritas.dynamictrees.util.JsonMapWrapper;
import com.ferreusveritas.dynamictrees.util.holderset.DTBiomeHolderSet;
import com.ferreusveritas.dynamictrees.util.holderset.DelayedHolderSet;
import com.ferreusveritas.dynamictrees.util.holderset.NameRegexMatchHolderSet;
import com.ferreusveritas.dynamictrees.util.holderset.TagsRegexMatchHolderSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.holdersets.OrHolderSet;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * @author Harley O'Connor, Max Hyper
 */
public final class BiomeListDeserialiser implements JsonDeserialiser<DTBiomeHolderSet> {

    public static final Supplier<Registry<Biome>> DELAYED_BIOME_REGISTRY = () -> {
        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        if (currentServer == null)
            throw new IllegalStateException("Queried biome registry too early; server does not exist yet!");

        return currentServer.registryAccess().registryOrThrow(Registries.BIOME);
    };

    private final VoidApplier<DTBiomeHolderSet, JsonObject> AND_OPERATOR =
            (biomes, jsonObject) -> applyAllAppliers(jsonObject, biomes);

    private final VoidApplier<DTBiomeHolderSet, JsonArray> OR_OPERATOR = (biomeList, json) -> {
        List<HolderSet<Biome>> appliedList = new LinkedList<>();

        JsonResult.forInput(json)
                .mapEachIfArray(JsonObject.class, object -> {
                    DTBiomeHolderSet subList = new DTBiomeHolderSet();
                    applyAllAppliers(object, subList);
                    appliedList.add(subList);
                    return object;
                })
                .orElse(null, LogManager.getLogger()::error, LogManager.getLogger()::warn);

        if (!appliedList.isEmpty())
            biomeList.getIncludeComponents().add(new OrHolderSet<>(appliedList));
    };

    private final VoidApplier<DTBiomeHolderSet, JsonObject> NOT_OPERATOR = (biomeList, jsonObject) -> {
        final DTBiomeHolderSet notBiomeList = new DTBiomeHolderSet();
        applyAllAppliers(jsonObject, notBiomeList);
        biomeList.getExcludeComponents().add(notBiomeList);
    };

    private static boolean usingNotCharacter(String categoryString) {
        return categoryString.charAt(0) == '!';
    }

    private final Applier<DTBiomeHolderSet, String> TAG_APPLIER = (biomeList, tagRegex) -> {
        tagRegex = tagRegex.toLowerCase(Locale.ENGLISH);
        final boolean notOperator = usingNotCharacter(tagRegex);
        if (notOperator)
            tagRegex = tagRegex.substring(1);
        if (tagRegex.charAt(0) == '#')
            tagRegex = tagRegex.substring(1);

        try {
            String[] decomp = ResourceLocation.decompose(tagRegex, ':');
            String tagLocation = decomp[0]+":"+decomp[1];
            (notOperator ? biomeList.getExcludeComponents() : biomeList.getIncludeComponents()).add(new DelayedHolderSet<>(
                            () -> new TagsRegexMatchHolderSet<>(DELAYED_BIOME_REGISTRY.get().asLookup(), tagLocation)));
        } catch (ResourceLocationException e) {
            return PropertyApplierResult.failure(e.getMessage());
        }

        return PropertyApplierResult.success();
    };

    private final Applier<DTBiomeHolderSet, String> NAME_APPLIER = (biomeList, nameRegex) -> {
        nameRegex = nameRegex.toLowerCase(Locale.ENGLISH);
        final boolean notOperator = usingNotCharacter(nameRegex);
        if (notOperator)
            nameRegex = nameRegex.substring(1);

        try {
            String[] decomp = ResourceLocation.decompose(nameRegex, ':');
            String nameLocation = decomp[0]+":"+decomp[1];
            (notOperator ? biomeList.getExcludeComponents() : biomeList.getIncludeComponents()).add(new DelayedHolderSet<>(
                    () -> new NameRegexMatchHolderSet<>(DELAYED_BIOME_REGISTRY.get().asLookup(), nameLocation)));
        } catch (ResourceLocationException e) {
            return PropertyApplierResult.failure(e.getMessage());
        }

        return PropertyApplierResult.success();
    };


    private VoidApplier<DTBiomeHolderSet, JsonArray> arrayOrApplier(String applier) {
        return (biomeList, json) -> {
            JsonArray array = new JsonArray();
            JsonResult.forInput(json)
                    .mapEachIfArray(String.class, (s)->{
                        JsonObject ob = new JsonObject();
                        ob.add(applier, new JsonPrimitive(s));
                        array.add(ob);
                        return s;
                    })
                    .orElse(Collections.emptyList(), LogManager.getLogger()::error, LogManager.getLogger()::warn);
            OR_OPERATOR.apply(biomeList, array);
        };
    }

    private final JsonPropertyAppliers<DTBiomeHolderSet> appliers = new JsonPropertyAppliers<>(DTBiomeHolderSet.class);

    public BiomeListDeserialiser() {
        registerAppliers();
    }

    private void registerAppliers() {
        this.appliers
                .register("tag", String.class, TAG_APPLIER)
                .registerArrayApplier("tags", String.class, TAG_APPLIER)
                .register("tags_or", JsonArray.class, arrayOrApplier("tag"))
                .register("name", String.class, NAME_APPLIER)
                .registerArrayApplier("names", String.class, NAME_APPLIER)
                .register("names_or", JsonArray.class, arrayOrApplier("name"))
                .registerArrayApplier("AND", JsonObject.class, AND_OPERATOR)
                .register("OR", JsonArray.class, OR_OPERATOR)
                .register("NOT", JsonObject.class, NOT_OPERATOR);
    }

    private void applyAllAppliers(JsonObject json, DTBiomeHolderSet biomes) {
        appliers.applyAll(new JsonMapWrapper(json), biomes);
    }

    @Override
    public Result<DTBiomeHolderSet, JsonElement> deserialise(final JsonElement input) {
        return JsonResult.forInput(input)
                .mapIfType(String.class, biomeName -> {
                    DTBiomeHolderSet biomes = new DTBiomeHolderSet();
                    biomes.getIncludeComponents().add(new DelayedHolderSet<>(() -> new NameRegexMatchHolderSet<>(DELAYED_BIOME_REGISTRY.get().asLookup(), biomeName.toLowerCase(Locale.ENGLISH))));
                    return biomes;
                })
                .elseMapIfType(JsonObject.class, selectorObject -> {
                    final DTBiomeHolderSet biomes = new DTBiomeHolderSet();
                    // Apply from all appliers
                    applyAllAppliers(selectorObject, biomes);
                    return biomes;
                }).elseTypeError();
    }

}