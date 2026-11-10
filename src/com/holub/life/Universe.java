package com.holub.life;

import java.io.*;

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

import com.holub.io.Files;
import com.holub.ui.MenuSite;

import com.holub.life.Cell;
import com.holub.life.Storable;
import com.holub.life.Clock;
import com.holub.life.Neighborhood;
import com.holub.life.Resident;

/**
 * The Universe is a mediator that sits between the Swing
 * event model and the Life classes. It is also a singleton,
 * accessed via Universe.instance(). It handles all
 * Swing events and translates them into requests to the
 * outermost Neighborhood. It also creates the Composite
 * Neighborhood.
 *
 * @include /etc/license.txt
 */

public class Universe extends JPanel
{	private 		final Cell  	outermostCell;
	private final PatternTools patternTools;
	private final FreezingTools freezingTools;	// [Freezing]
	private JTabbedPane sideControls;			// [Freezing] right panel: capture tab + Freezing tab
	private static	final Universe 	theInstance = new Universe();

	/** The default height and width of a Neighborhood in cells.
	 *  If it's too big, you'll run too slowly because
	 *  you have to update the entire block as a unit, so there's more
	 *  to do. If it's too small, you have too many blocks to check.
	 *  I've found that 8 is a good compromise.
	 */
	private static final int  DEFAULT_GRID_SIZE = 8;

	/** The size of the smallest "atomic" cell---a Resident object.
	 *  This size is extrinsic to a Resident (It's passed into the
	 *  Resident's "draw yourself" method.
	 */
	private static final int  DEFAULT_CELL_SIZE = 8;

	// The constructor is private so that the universe can be created
	// only by an outer-class method [Neighborhood.createUniverse()].

	private Universe()
	{	// Create the nested Cells that comprise the "universe." A bug
		// in the current implementation causes the program to fail
		// miserably if the overall size of the grid is too big to fit
		// on the screen.

		outermostCell = new Neighborhood
						(	DEFAULT_GRID_SIZE,
							new Neighborhood
							(	DEFAULT_GRID_SIZE,
								new Resident()
							)
						);

		final Dimension PREFERRED_SIZE =
						new Dimension
						(  outermostCell.widthInCells() * DEFAULT_CELL_SIZE,
						   outermostCell.widthInCells() * DEFAULT_CELL_SIZE
						);

		addComponentListener
		(	new ComponentAdapter()
			{	public void componentResized(ComponentEvent e)
				{
					// Make sure that the cells fit evenly into the
					// total grid size so that each cell will be the
					// same size. For example, in a 64x64 grid, the
					// total size must be an even multiple of 63.

					Rectangle bounds = getBounds();
					bounds.height /= outermostCell.widthInCells();
					bounds.height *= outermostCell.widthInCells();
					bounds.width  =  bounds.height;
					setBounds( bounds );
				}
			}
		);

		setBackground	( Color.white	 );
		setPreferredSize( PREFERRED_SIZE );
		setMaximumSize	( PREFERRED_SIZE );
		setMinimumSize	( PREFERRED_SIZE );
		setOpaque		( true			 );
		patternTools = new PatternTools(this, outermostCell);

		// [Freezing] Starting an area selection ends pattern editing and
		// clears its undo history (an undo must not overwrite frozen cells).
		freezingTools = new FreezingTools( this, new FreezingManager(outermostCell),
			() -> {	if (patternTools.isEditing()) patternTools.cancel();
					patternTools.boardChanged();
				  } );
		patternTools.setPlacementGuard( area -> freezingTools.manager().overlapsFrozen(area) );

		// [Freezing] Esc ends whichever editing mode is on.
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel-editing");
		getActionMap().put("cancel-editing", new AbstractAction()
		{	public void actionPerformed(ActionEvent e)
			{	if (freezingTools.isEditing()) freezingTools.cancel();
				else patternTools.cancel();
			}
		});

		addMouseListener					//{=Universe.mouse}
		(	new MouseAdapter()
			{	public void mousePressed(MouseEvent e)
				{	if (patternTools.isEditing() || freezingTools.isEditing()
							|| !SwingUtilities.isLeftMouseButton(e)) return;
					Rectangle bounds = getBounds();
					bounds.x = 0;
					bounds.y = 0;
					if (!bounds.contains(e.getPoint())) return;
					int unit = bounds.width / outermostCell.widthInCells();		// [Freezing]
					if (freezingTools.manager().isFrozen(
							new Point(e.getX() / unit, e.getY() / unit)))
					{	freezingTools.blockedEdit();
						return;
					}
					patternTools.boardChanged();
					outermostCell.userClicked(e.getPoint(),bounds);
					repaint();
				}
			}
		);

		MenuSite.addLine( this, "Grid", "Clear",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	patternTools.cancel();
					patternTools.boardChanged();
					freezingTools.reset();				// [Freezing]
					outermostCell.clear();
					repaint();
				}
			}
		);

		MenuSite.addLine			// {=Universe.load.setup}
		(	this, "Grid", "Load",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	doLoad();
				}
			}
		);

		MenuSite.addLine
		(	this, "Grid", "Store",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	doStore();
				}
			}
		);

		MenuSite.addLine
		(	this, "Grid", "Exit",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
		        {	System.exit(0);
		        }
			}
		);

		Clock.instance().addClockListener //{=Universe.clock.subscribe}
		(	new Clock.Listener()
			{	public void tick()
				{	if (patternTools.isEditing() || freezingTools.isEditing()) return;
					patternTools.boardChanged();

					// [Freezing] The B3/S23 computation and transition are
					// the original calls. Between them, every Freezing region
					// asks its State object whether to hold the snapshot.
					FreezingManager freezing = freezingTools.manager();
					freezing.beginGeneration();
					boolean unstable = outermostCell.figureNextState
						   ( Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,
							 Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY
						   );
					boolean held = freezing.beforeTransition(outermostCell);
					if( unstable || held )
					{	if( outermostCell.transition() )
							refreshNow();
					}
					freezing.afterGeneration();			// countdown, Frozen -> Normal
				}
			}
		);
	}

	/** Singleton Accessor. The Universe object itself is manufactured
	 *  in Neighborhood.createUniverse()
	 */

	public static Universe instance()
	{	return theInstance;
	}

	JPanel patternControls() { return patternTools.controls(); }
	JPanel freezingControls() { return freezingTools.controls(); }	// [Freezing]
	FreezingManager freezing() { return freezingTools.manager(); }	// [Freezing]

	/** [Freezing] Right panel: the existing capture panel and the Freezing
	 *  panel as separate tabs. Switching tabs ends any editing mode.
	 */
	JTabbedPane sideControls()
	{	if (sideControls == null)
		{	sideControls = new JTabbedPane();
			sideControls.addTab("패턴 캡처", patternControls());
			sideControls.addTab("Freezing", freezingControls());
			// Same height as the board, so the tab header does not leave an empty strip under it.
			sideControls.setPreferredSize(new Dimension(
					sideControls.getPreferredSize().width, getPreferredSize().height));
			sideControls.addChangeListener(e -> {
				if (patternTools.isEditing()) patternTools.cancel();
				freezingTools.cancel();
			});
		}
		return sideControls;
	}

	private void doLoad()
	{	try
		{
			FileInputStream in = new FileInputStream(
			   Files.userSelected(".",".life","Life File","Load"));

			patternTools.cancel();
			patternTools.boardChanged();
			freezingTools.reset();			// [Freezing]
			Clock.instance().stop();		// stop the game and
			outermostCell.clear();			// clear the board.

			Storable memento = outermostCell.createMemento();
			memento.load( in );
			outermostCell.transfer( memento, new Point(0,0), Cell.LOAD );

			in.close();
		}
		catch( IOException theException )
		{	JOptionPane.showMessageDialog( null, "Read Failed!",
					"The Game of Life", JOptionPane.ERROR_MESSAGE);
		}
		repaint();
	}

	private void doStore()
	{	try
		{
			FileOutputStream out = new FileOutputStream(
				  Files.userSelected(".",".life","Life File","Write"));

			Clock.instance().stop();		// stop the game

			Storable memento = outermostCell.createMemento();
			outermostCell.transfer( memento, new Point(0,0), Cell.STORE );
			memento.flush(out);

			out.close();
		}
		catch( IOException theException )
		{	JOptionPane.showMessageDialog( null, "Write Failed!",
					"The Game of Life", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Override paint to ask the outermost Neighborhood
	 *  (and any subcells) to draw themselves recursively.
	 *  All knowledge of screen size is also encapsulated.
	 *  (The size is passed into the outermost <code>Cell</code>.)
	 */

	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Rectangle panelBounds = getBounds();
		Rectangle clipBounds  = g.getClipBounds();

		// The panel bounds is relative to the upper-left
		// corner of the screen. Pretend that it's at (0,0)
		panelBounds.x = 0;
		panelBounds.y = 0;
		outermostCell.redraw(g, panelBounds, true);		//{=Universe.redraw1}
		if (freezingTools != null) freezingTools.paintOverlay((Graphics2D) g);	// [Freezing]
		if (patternTools != null) patternTools.paintOverlay((Graphics2D) g);
	}

	/** Repaint through Swing so the board and editing overlay stay together. */

	private void refreshNow()
	{	repaint();
	}
}
