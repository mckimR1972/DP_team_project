package com.holub.life;

import java.util.BitSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Calculates statistics from completed-board events without changing the board.
 *
 * <p>Deliver events sequentially, in operation order. Only the most recently
 * received state is retained as the next comparison baseline; this is not undo
 * history. No Swing component or Universe instance is needed.
 */
public final class Statistics implements BoardChangeListener {
    /**
     * livingRatio is a percentage in [0, 100].
     *
     * <p>When hasGenerationDelta is false, births and deaths are zero
     * placeholders. A view should display an unavailable marker such as "-",
     * rather than interpreting those placeholders as a measured zero change.
     */
    public record Summary(
            long generation,
            int livingCells,
            double livingRatio,
            int births,
            int deaths,
            boolean hasGenerationDelta) {
    }

    private final Consumer<Summary> onUpdated;
    private BitSet previousAlive;
    private int previousBoardSize;
    private long previousGeneration;
    private Summary currentSummary;

    public Statistics(Consumer<Summary> onUpdated) {
        this.onUpdated = Objects.requireNonNull(onUpdated, "onUpdated");
    }

    @Override
    public void onBoardChanged(BoardChangeEvent event) {
        Objects.requireNonNull(event, "event");
        BitSet currentAlive = event.copyAliveCells();
        Summary summary = calculate(event, currentAlive);

        // Every operation, including edits and restores, establishes a new baseline.
        previousAlive = currentAlive;
        previousBoardSize = event.boardSize();
        previousGeneration = event.generation();
        currentSummary = summary;

        onUpdated.accept(summary);
    }

    /** Empty until the first event has been received. */
    public Optional<Summary> current() {
        return Optional.ofNullable(currentSummary);
    }

    private Summary calculate(BoardChangeEvent event, BitSet currentAlive) {
        int livingCells = currentAlive.cardinality();
        double totalCells = (double) event.boardSize() * event.boardSize();

        // Missing generations or changed dimensions cannot describe a one-generation delta.
        boolean hasGenerationDelta = event.kind() == BoardChangeEvent.Kind.GENERATION
                && previousAlive != null
                && previousBoardSize == event.boardSize()
                && event.generation() > 0
                && previousGeneration == event.generation() - 1;

        int births = 0;
        int deaths = 0;
        if (hasGenerationDelta) {
            BitSet born = (BitSet) currentAlive.clone();
            born.andNot(previousAlive);
            births = born.cardinality();

            BitSet died = (BitSet) previousAlive.clone();
            died.andNot(currentAlive);
            deaths = died.cardinality();
        }

        return new Summary(event.generation(), livingCells,
                livingCells * 100.0 / totalCells, births, deaths, hasGenerationDelta);
    }
}
