package com.holub.life;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JLabel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/** Reads the actual displayed components without exposing production view fields. */
final class StatisticsPanelTestSupport {
    private StatisticsPanelTestSupport() { }

    /** 실제 화면 갱신을 수행하면서 콜백으로 전달된 집계 결과를 테스트에 기록한다. */
    static StatisticsPanel recordingPanel(Consumer<Statistics.Summary> onUpdated) {
        // 생성자를 직접 실행해 BoxLayout이 기록용 패널 자신을 참조하도록 한다.
        StatisticsPanel panel = spy(StatisticsPanel.class);
        doAnswer(call -> {
            call.callRealMethod();
            onUpdated.accept(call.getArgument(0));
            return null;
        }).when(panel).showStatistics(any(Statistics.Summary.class));
        return panel;
    }

    static JLabel label(StatisticsPanel panel, String field) {
        return (JLabel) named(panel, "statistics." + field);
    }

    static Component named(Container parent, String name) {
        if (name.equals(parent.getName())) return parent;
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = named(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    static Map<String, String> displayed(StatisticsPanel panel) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : new String[] {"generation", "livingCells", "livingRatio", "births", "deaths"}) {
            values.put(field, label(panel, field).getText());
        }
        return values;
    }
}
