package com.holub.life;

import java.awt.*;
import javax.swing.*;
import com.holub.ui.MenuSite;
import com.holub.life.sound.SoundEffects;

/*******************************************************************
 * An implemenation of Conway's Game of Life.
 *
 * @include /etc/license.txt
 */

public final class Life extends JFrame
{	
	private final Universe universe;
	private final Statistics statistics;

	public static void main( String[] arguments )
	{	SwingUtilities.invokeLater(Life::new);
	}

	private Life()
	{	super( "The Game of Life. "
					+"(c)2003 Allen I. Holub <http://www.holub.com>");

		// Must establish the MenuSite very early in case
		// a subcomponent puts menus on it.
		MenuSite.establish( this );		//{=life.java.establish}

		setDefaultCloseOperation	( EXIT_ON_CLOSE 		);
		universe = Universe.instance();
		StatisticsPanel statisticsPanel = new StatisticsPanel();
		JTabbedPane tabs = createSidebar(universe, statisticsPanel);
		setContentPane(createContent(universe, tabs));
		statistics = connectStatistics(universe, statisticsPanel);
		SoundEffects.install(universe);

		setResizable(false);
		pack();
		setLocationRelativeTo(null);
		setVisible( true );
	}

    /** Assemble shared tools once; changing pages does not reconnect observers. */
    static JTabbedPane createSidebar(Universe universe, StatisticsPanel statisticsPanel) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("life.sidebar");
        JPanel patterns = universe.patternControls();
        tabs.addTab("패턴 캡처", patterns);
        tabs.addTab("통계", statisticsPanel);
        JPanel freezing = universe.freezingControls();
        tabs.addTab("Freezing", freezing);
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedComponent() != patterns) universe.finishPatternEditing();
            if (tabs.getSelectedComponent() != freezing) universe.finishFreezingEditing();
        });
        return tabs;
    }

	/** Lay out supplied tabs without replacing their pages, selection, or listeners. */
	static JPanel createContent(Universe universe, JTabbedPane tabs) {
		// Recompute after all tabs are added, even if the shared pane was sized earlier.
		tabs.setPreferredSize(null);
		Dimension tabSize = tabs.getPreferredSize();
		tabs.setPreferredSize(new Dimension(tabSize.width, universe.getPreferredSize().height));

		JPanel content = new JPanel(new BorderLayout());
		content.add(universe, BorderLayout.CENTER);
		content.add(tabs, BorderLayout.EAST);
		return content;
	}

	/** Registration synchronously displays the INITIAL snapshot before the window opens. */
	static Statistics connectStatistics(Universe universe, StatisticsPanel panel) {
		Statistics statistics = new Statistics(panel::showStatistics);
		universe.addBoardChangeListener(statistics);
		return statistics;
	}

	@Override public void dispose() {
		universe.removeBoardChangeListener(statistics);
		super.dispose();
	}
}
