package feedlab.core.arbiter;

/** The two redundant lines that carry the same sequenced stream. */
public enum Line {
    A,
    B;

    public static final int COUNT = values().length;
}
