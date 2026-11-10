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

		// 누르면 셀 하나를 토글하고, 끄는 동안 지나가는 셀은 모두
		// 같은 상태로 만든다. 그래서 한 번 끌면 그리거나 지운다.

		MouseAdapter brush = new MouseAdapter()		//{=Universe.mouse}
		{	private boolean paintsAlive;

			public void mousePressed(MouseEvent e)
			{	Point cell  = cellAt( e.getPoint() );
				paintsAlive = !outermostCell.isAliveAt(cell.y, cell.x);
				setCell( cell, paintsAlive );
				repaint();
			}

			public void mouseDragged(MouseEvent e)
			{	setCell( cellAt(e.getPoint()), paintsAlive );
				repaint();
			}
		};
		addMouseListener( brush );
		addMouseMotionListener( brush );

		MenuSite.addLine( this, "Grid", "Clear",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	outermostCell.clear();
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
				{	if( outermostCell.figureNextState
						   ( Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,
							 Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY
						   )
					  )
					{	if( outermostCell.transition() )
							refreshNow();
					}
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

	private int pixelsPerCell()
	{	return Math.max( 1, getWidth() / outermostCell.widthInCells() );
	}

	/** 마우스 위치 아래의 셀을 (x = 열, y = 행)으로 반환한다.
	 *  판 밖이면 가장 가까운 가장자리 셀로 맞춘다.
	 */
	private Point cellAt( Point pixel )
	{	int last = outermostCell.widthInCells() - 1;
		return new Point
		(	Math.max( 0, Math.min(last, pixel.x / pixelsPerCell()) ),
			Math.max( 0, Math.min(last, pixel.y / pixelsPerCell()) )
		);
	}

	private void setCell( Point cell, boolean alive )
	{	if( outermostCell.isAliveAt(cell.y, cell.x) == alive )
			return;

		int size  = pixelsPerCell();
		int board = size * outermostCell.widthInCells();
		outermostCell.userClicked( new Point(cell.x * size, cell.y * size),
								   new Rectangle(0, 0, board, board) );
	}

	private void doLoad()
	{	try
		{
			FileInputStream in = new FileInputStream(
			   Files.userSelected(".",".life","Life File","Load"));

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

	/** Force a screen refresh by queing a request on
	 *  the Swing event queue. This is an example of the
	 *  Active Object pattern (not covered by the Gang of Four).
	 *  This method is called on every clock tick. Note that
	 *  the redraw() method on a given <code>Cell</code>
	 *  does nothing if the <code>Cell</code> doesn't
	 *  have to be refreshed.
	 */

	private void refreshNow()
	{	SwingUtilities.invokeLater
		(	new Runnable()
			{	public void run()
				{	Graphics g = getGraphics();
					if( g == null )		// Universe not displayable
						return;
					try
					{
						Rectangle panelBounds = getBounds();
						panelBounds.x = 0;
						panelBounds.y = 0;
						outermostCell.redraw(g, panelBounds, false); //{=Universe.redraw2}
					}
					finally
					{	g.dispose();
					}
				}
			}
		);
	}
}
