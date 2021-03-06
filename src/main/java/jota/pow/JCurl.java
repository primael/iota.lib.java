package jota.pow;

import jota.utils.Converter;
import jota.utils.Pair;

import java.util.Arrays;

/**
 * (c) 2016 Come-from-Beyond
 * <p>
 * JCurl belongs to the sponge function family.
 */
public class JCurl implements ICurl {

    /**
     * The hash length.
     */
    public static final int HASH_LENGTH = 243;
    private static final int STATE_LENGTH = 3 * HASH_LENGTH;

    private static final int NUMBER_OF_ROUNDS = 27;
    private static final int[] TRUTH_TABLE = {1, 0, -1, 2, 1, -1, 0, 2, -1, 1, 0};
    private final long[] stateLow;
    private final long[] stateHigh;
    private final int[] scratchpad = new int[STATE_LENGTH];
    private int[] state;
    private boolean pair;

    public JCurl() {
        this(false);
    }

    public JCurl(boolean pair) {
        this.pair = pair;
        if (pair) {
            stateHigh = new long[STATE_LENGTH];
            stateLow = new long[STATE_LENGTH];
            state = null;
            set();
        } else {
            state = new int[STATE_LENGTH];
            stateHigh = null;
            stateLow = null;
        }
    }

    /**
     * Absorbs the specified trits.
     *
     * @param trits  The trits.
     * @param offset The offset to start from.
     * @param length The length.
     * @return The ICurl instance (used for method chaining).
     */
    public JCurl absorb(final int[] trits, int offset, int length) {

        do {
            System.arraycopy(trits, offset, state, 0, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);

        return this;
    }

    /**
     * Absorbs the specified trits.
     *
     * @param trits The trits.
     * @return The ICurl instance (used for method chaining).
     */
    public JCurl absorb(final int[] trits) {
        return absorb(trits, 0, trits.length);
    }

    /**
     * Transforms this instance.
     *
     * @return The ICurl instance (used for method chaining).
     */
    public JCurl transform() {

        int scratchpadIndex = 0;
        int prev_scratchpadIndex = 0;
        for (int round = 0; round < NUMBER_OF_ROUNDS; round++) {
            System.arraycopy(state, 0, scratchpad, 0, STATE_LENGTH);
            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
                prev_scratchpadIndex = scratchpadIndex;
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364;
                } else {
                    scratchpadIndex += -365;
                }
                state[stateIndex] = TRUTH_TABLE[scratchpad[prev_scratchpadIndex] + (scratchpad[scratchpadIndex] << 2) + 5];
            }
        }

        return this;
    }

    /**
     * Resets this state.
     *
     * @return The ICurl instance (used for method chaining).
     */
    public JCurl reset() {
        for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
            state[stateIndex] = 0;
        }
        return this;
    }

    /**
     * Squeezes the specified trits.
     *
     * @param trits  The trits.
     * @param offset The offset to start from.
     * @param length The length.
     * @return The squeezes trits.
     */
    public int[] squeeze(final int[] trits, int offset, int length) {

        do {
            System.arraycopy(state, 0, trits, offset, length < HASH_LENGTH ? length : HASH_LENGTH);
            transform();
            offset += HASH_LENGTH;
        } while ((length -= HASH_LENGTH) > 0);

        return state;
    }

    /**
     * Squeezes the specified trits.
     *
     * @param trits The trits.
     * @return The squeezes trits.
     */
    public int[] squeeze(final int[] trits) {
        return squeeze(trits, 0, trits.length);
    }

    /**
     * Gets the states.
     *
     * @return The state.
     */
    public int[] getState() {
        return state;
    }

    /**
     * Sets the state.
     *
     * @param state The states.
     */
    public void setState(int[] state) {
        this.state = state;
    }

    private void set() {
        Arrays.fill(stateLow, Converter.HIGH_LONG_BITS);
        Arrays.fill(stateHigh, Converter.HIGH_LONG_BITS);
    }

    private void pairTransform() {
        final long[] curlScratchpadLow = new long[STATE_LENGTH];
        final long[] curlScratchpadHigh = new long[STATE_LENGTH];
        int curlScratchpadIndex = 0;
        for (int round = 27; round-- > 0; ) {
            System.arraycopy(stateLow, 0, curlScratchpadLow, 0, STATE_LENGTH);
            System.arraycopy(stateHigh, 0, curlScratchpadHigh, 0, STATE_LENGTH);
            for (int curlStateIndex = 0; curlStateIndex < STATE_LENGTH; curlStateIndex++) {
                final long alpha = curlScratchpadLow[curlScratchpadIndex];
                final long beta = curlScratchpadHigh[curlScratchpadIndex];
                final long gamma = curlScratchpadHigh[curlScratchpadIndex += (curlScratchpadIndex < 365 ? 364 : -365)];
                final long delta = (alpha | (~gamma)) & (curlScratchpadLow[curlScratchpadIndex] ^ beta);
                stateLow[curlStateIndex] = ~delta;
                stateHigh[curlStateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    public void absorb(final Pair<long[], long[]> pair, int offset, int length) {
        int o = offset, l = length, i = 0;
        do {
            System.arraycopy(pair.low, o, stateLow, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            System.arraycopy(pair.hi, o, stateHigh, 0, l < HASH_LENGTH ? l : HASH_LENGTH);
            pairTransform();
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
    }

    public Pair<long[], long[]> squeeze(Pair<long[], long[]> pair, int offset, int length) {
        int o = offset, l = length, i = 0;
        long[] low = pair.low;
        long[] hi = pair.hi;
        do {
            System.arraycopy(stateLow, 0, low, o, l < HASH_LENGTH ? l : HASH_LENGTH);
            System.arraycopy(stateHigh, 0, hi, o, l < HASH_LENGTH ? l : HASH_LENGTH);
            pairTransform();
            o += HASH_LENGTH;
        } while ((l -= HASH_LENGTH) > 0);
        return new Pair<>(low, hi);
    }

    /**
     * Clones this instance.
     *
     * @return A new instance.
     */
    @Override
    public ICurl clone() {
        return new JCurl(pair);
    }
}
