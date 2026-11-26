package com.holub.life;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 게임판 변경이 완료된 이벤트를 받아 생존 수, 변화량, 색상별 통계를 계산한다.
 *
 * <p>Universe가 전달한 탄생·사망 좌표를 집계하므로 이전 게임판을 보관하거나 비교하지 않는다.
 * 이벤트는 게임판 작업이 완료된 순서대로 전달해야 한다.
 */
public final class Statistics implements BoardChangeListener {
    /**
     * 화면에 전달하는 통계 결과.
     * livingRatio는 전체 칸 수를 기준으로 한 생존 비율(0~100%)이다.
     *
     * <p>hasGenerationDelta가 false이면 세대 변화량을 표시할 수 없다.
     * 이때 births와 deaths의 0은 임시 값이므로 화면에는 실제 변화량 0 대신 "—"를 표시한다.
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

    /** 한 색상의 생존 셀 수와 비율. ratio는 전체 생존 셀 수를 기준으로 한 백분율이다. */
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

    private Summary calculate(BoardChangeEvent event) {
        int livingCells = 0;
        for (boolean[] row : event.copyAliveCells()) {
            for (boolean alive : row) if (alive) livingCells++;
        }
        double totalCells = (double) event.boardSize() * event.boardSize();
        boolean hasGenerationDelta = event.kind() == BoardChangeEvent.Kind.GENERATION;
        int births = hasGenerationDelta ? event.copyBirths().size() : 0;
        int deaths = hasGenerationDelta ? event.copyDeaths().size() : 0;
        // 같은 세대에서 색상 정책만 바뀌면 직전 결과의 탄생·사망 표시 상태를 유지한다.
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
            // 생존 셀이 없으면 색상별 항목도 없어 이 반복문에서 0으로 나누지 않는다.
            colorCounts.add(new ColorCount(entry.getKey(), entry.getValue(),
                    entry.getValue() * 100.0 / livingCells));
        }
        return new Summary(event.generation(), livingCells,
                livingCells * 100.0 / totalCells, births, deaths, hasGenerationDelta, colorCounts);
    }
}
