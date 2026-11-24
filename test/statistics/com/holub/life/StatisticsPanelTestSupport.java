package com.holub.life;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;

/** Reads the actual displayed components without exposing production view fields. */
final class StatisticsPanelTestSupport {
    private StatisticsPanelTestSupport() { }

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
