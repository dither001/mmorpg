package uk.ac.brighton.uni.ab607.mmorpg.common;

public abstract class Skill implements java.io.Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 442371944346845569L;

    protected static final int MAX_LEVEL = 10;

    protected int level;

    /**
     * Just some ideas at this stage
     *
     * Soul Slash - 7 consecutive attacks.
     * Performs 6 fast attacks of type NORMAL, each attack deals 10% more than previous.
     * Deals 850% of your base ATK.
     * Final hit is of type GHOST.
     * Deals 200% of your total ATK
     *
     *
     * Final Strike
     * Drains all HP/SP leaving 1 HP/0 SP.
     * Initial skill damage - 1000
     * For each HP/SP drained the skill damage increases by 0.3%
     *
     *
     * Cleanse
     *
     *
     *
     * Mind Blast
     * Drains % of target's SP based on target's level.
     * Increases cost of all skills by that % for 30s
     *
     *
     *
     *
     */
}
