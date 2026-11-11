package com.holub.life;

import java.io.*;

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
	private final Set<BoardChangeListener> boardListeners = new LinkedHashSet<>();
	private long generation;
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

		addMouseListener					//{=Universe.mouse}
		(	new MouseAdapter()
			{	public void mousePressed(MouseEvent e)
				{	Rectangle bounds = getBounds();
					bounds.x = 0;
					bounds.y = 0;
					if (!SwingUtilities.isLeftMouseButton(e)
							|| !bounds.contains(e.getPoint())) return;
					outermostCell.userClicked(e.getPoint(),bounds);
					notifyBoardChanged(BoardChangeEvent.Kind.EDIT);
					repaint();
				}
			}
		);

		MenuSite.addLine( this, "Grid", "Clear",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	outermostCell.clear();
					generation = 0;
					notifyBoardChanged(BoardChangeEvent.Kind.CLEAR);
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

		Clock.instance().addClockListener(this::advanceOneGeneration);
	}

	/** Singleton Accessor. The Universe object itself is manufactured
	 *  in Neighborhood.createUniverse()
	 */

	public static Universe instance()
	{	return theInstance;
	}

    /** Register on the EDT. Only a newly registered observer receives INITIAL. */
    public void addBoardChangeListener(BoardChangeListener listener) {
        requireEventDispatchThread();
        if (boardListeners.add(Objects.requireNonNull(listener, "listener"))) {
            listener.onBoardChanged(createBoardChangeEvent(BoardChangeEvent.Kind.INITIAL));
        }
    }

    /** Removing an absent listener is harmless. Call on the EDT. */
    public void removeBoardChangeListener(BoardChangeListener listener) {
        requireEventDispatchThread();
        boardListeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    /** Complete one generation before publishing its state, even for a stable board. */
    public void advanceOneGeneration() {
        requireEventDispatchThread();
        if (outermostCell.figureNextState(
                Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY,
                Cell.DUMMY, Cell.DUMMY, Cell.DUMMY, Cell.DUMMY)) {
            outermostCell.transition();
        }
        generation++;
        notifyBoardChanged(BoardChangeEvent.Kind.GENERATION);
        repaint();
    }

    /**
     * Publish after a complete board operation on the EDT. Future template tools
     * can use this after applying all cells. This method does not change the board
     * or advance the generation. Observers must not modify the board in callbacks.
     * Subscription changes during delivery take effect for the next broadcast.
     */
    public void notifyBoardChanged(BoardChangeEvent.Kind kind) {
        requireEventDispatchThread();
        Objects.requireNonNull(kind, "kind");
        if (boardListeners.isEmpty()) return;

        BoardChangeEvent event = createBoardChangeEvent(kind);
        for (BoardChangeListener listener : boardListeners.toArray(new BoardChangeListener[0])) {
            listener.onBoardChanged(event);
        }
    }

    private BoardChangeEvent createBoardChangeEvent(BoardChangeEvent.Kind kind) {
        return new BoardChangeEvent(kind, generation, outermostCell.widthInCells(),
                captureAliveCells());
    }

    private BitSet captureAliveCells() {
        int size = outermostCell.widthInCells();
        BitSet aliveCells = new BitSet(size * size);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (outermostCell.isAliveAt(row, column)) {
                    aliveCells.set(row * size + column);
                }
            }
        }
        return aliveCells;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Board operations must run on the Swing EDT");
        }
    }

    private void doLoad() {
        File source;
        try {
            source = Files.userSelected(".", ".life", "Life File", "Load");
        } catch (FileNotFoundException cancelled) {
            // The existing file chooser signals cancellation with this exception.
            return;
        }

        // Read completely before clearing the board: a failed read changes nothing.
        Storable memento = outermostCell.createMemento();
        try (FileInputStream in = new FileInputStream(source)) {
            memento.load(in);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(null, "Read Failed!",
                    "The Game of Life", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Clock.instance().stop();
        outermostCell.clear();
        outermostCell.transfer(memento, new Point(0, 0), Cell.LOAD);
        generation = 0;
        notifyBoardChanged(BoardChangeEvent.Kind.LOAD);
        repaint();
    }

	private void doStore()
	{	try
		{
			FileOutputStream out = new FileOutputStream(
				  Files.userSelected(".",".life","Life File","Write"));

			Clock.instance().stop();		// stop the game

			Storable memento = outermostCell.createMemento();
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

	public void paint(Graphics g)
	{
		Rectangle panelBounds = getBounds();
		Rectangle clipBounds  = g.getClipBounds();

		// The panel bounds is relative to the upper-left
		// corner of the screen. Pretend that it's at (0,0)
		panelBounds.x = 0;
		panelBounds.y = 0;
		outermostCell.redraw(g, panelBounds, true);		//{=Universe.redraw1}
	}

}
