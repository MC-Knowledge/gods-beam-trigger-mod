package com.mck.godsbeamtrigger;

public final class BeamKnockbackContext {
    private static final ThreadLocal<Integer> BEAM_DAMAGE_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private BeamKnockbackContext() {
    }

    public static void push() {
        BEAM_DAMAGE_DEPTH.set(BEAM_DAMAGE_DEPTH.get() + 1);
    }

    public static void pop() {
        int depth = BEAM_DAMAGE_DEPTH.get() - 1;

        if (depth <= 0) {
            BEAM_DAMAGE_DEPTH.remove();
        } else {
            BEAM_DAMAGE_DEPTH.set(depth);
        }
    }

    public static boolean isBeamDamageActive() {
        return BEAM_DAMAGE_DEPTH.get() > 0;
    }
}