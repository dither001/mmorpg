package uk.ac.brighton.uni.ab607.mmorpg.common;


public class Rune implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -208840540966936340L;

    public final Attribute attribute;
    public final int bonus;

    public Rune(Attribute attr, int bonus) {
        this.attribute = attr;
        this.bonus = bonus;
    }

    @Override
    public String toString() {
        return attribute + " +" + bonus;
    }
}
