package com.nightbeam.remnants.mobs.reward;

import com.nightbeam.remnants.mobs.RemnantMobs;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Grants this mod's advancements, which all use a single {@code minecraft:impossible} criterion named "done". */
public final class Advancements {
    public static final String PROVEN_HUNTER = "proven_hunter";
    public static final String CHAMPIONS_BANE = "champions_bane";
    public static final String REMNANTS_END = "remnants_end";
    public static final String PUNCHING_UP = "punching_up";
    public static final String HUNTERS_MOON = "under_the_hunters_moon";
    public static final String TASTE_OF_VALOR = "taste_of_valor";
    public static final String KNOW_THY_ENEMY = "know_thy_enemy";
    public static final String ACCEPT_THE_CHALLENGE = "accept_the_challenge";
    public static final String NOTHING_LEFT_TO_HIDE = "nothing_left_to_hide";
    public static final String SEE_THE_DAWN = "see_the_dawn";
    public static final String KINDLED = "kindled";

    private Advancements() {
    }

    public static void award(ServerPlayer player, String name) {
        var server = player.level().getServer();
        AdvancementHolder holder = server.getAdvancements().get(RemnantMobs.id(name));
        if (holder != null) {
            player.getAdvancements().award(holder, "done");
        }
    }
}
