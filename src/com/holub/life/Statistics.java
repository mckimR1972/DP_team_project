package com.holub.life;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Calculates statistics from completed-board events without changing the board.
 *
 * <p>Universe supplies the completed transition's change lists. Statistics
 * counts them without retaining a previous board or comparing snapshots.
 * Deliver events sequentially, in operation order.
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
            boolean hasGenerationDelta,
            List<ColorCount> colorCounts) {
        public Summary {
            colorCounts = List.copyOf(colorCounts);
        }
    }

    /** ratio is the percentage of all living cells, not of all board positions. */
    public record ColorCount(Color color, int cells, double ratio) {
        public ColorCount {
            Objects.requireNonNull(color, "color");
        }
    }

    private final Consumer<Summary> onUpdated;
    private Summary currentSummary;

    public Statistics(Consumer<Summary> onUpdated) {
        this.onUpdated = Objects.requireNonNull(onUpdated, "onUpdated");
    }

    @Override
    public void onBoardChanged(BoardChangeEvent event) {
        Objects.requireNonNull(event, "event");
        Summary summary = calculate(event);
        currentSummary = summary;

        onUpdated.accept(summary);
    }

    /** Empty until the first event has been received. */
    public Optional<Summary> current() {
        return Optional.ofNullable(currentSummary);
    }

    private Summary calculate(BoardChangeEvent event) {
        int livingCells = 0;
        for (boolean[] row : event.copyAliveCells()) {
            for (boolean alive : row) if (alive) livingCells++;
        }
        double totalCells = (double) event.boardSize() * event.boardSize();
        boolean hasGenerationDelta = event.kind() == BoardChangeEvent.Kind.GENERATION;
        int births = hasGenerationDelta ? event.copyBirths().size() : 0;
        int deaths = hasGenerationDelta ? event.copyDeaths().size() : 0;
        // A display-policy change is not an edit or a new generation.
        if (event.kind() == BoardChangeEvent.Kind.COLOR_POLICY_CHANGED
                && currentSummary != null && currentSummary.generation() == event.generation()) {
            hasGenerationDelta = currentSummary.hasGenerationDelta();
            births = currentSummary.births();
            deaths = currentSummary.deaths();
        }
        Map<Color, Integer> counts = new TreeMap<>(Comparator.comparingInt(Color::getRGB));
        for (Color color : event.aliveColors()) counts.merge(color, 1, Integer::sum);
        List<ColorCount> colorCounts = new ArrayList<>();
        for (Map.Entry<Color, Integer> entry : counts.entrySet()) {
            // An empty board has no entries, so this never divides by zero.
            colorCounts.add(new ColorCount(entry.getKey(), entry.getValue(),
                    entry.getValue() * 100.0 / livingCells));
        }
        return new Summary(event.generation(), livingCells,
                livingCells * 100.0 / totalCells, births, deaths, hasGenerationDelta, colorCounts);
    }
}
