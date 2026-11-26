package com.holub.life;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
		patternTools = new PatternTools(this, outermostCell);
        patternTools.controls().addPropertyChangeListener("TEMPLATE",
                event -> notifyBoardChanged(BoardChangeEvent.Kind.TEMPLATE));
        patternTools.controls().addPropertyChangeListener("TEMPLATE_UNDO",
                event -> notifyBoardChanged(BoardChangeEvent.Kind.TEMPLATE_UNDO));

		// [Freezing] The manager reads this board's one generation counter, the
		// same number statistics and sound receive in BoardChangeEvent.
		// Starting an area selection ends pattern editing and clears its undo
		// history, so a later undo cannot erase cells inside a frozen area.
		freezingTools = new FreezingTools( this,
			new FreezingManager( outermostCell, () -> generation ),
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

		// 누르면 셀 하나를 토글하고, 끄는 동안 지나가는 셀은 모두
		// 같은 상태로 만든다. 그래서 한 번 끌면 그리거나 지운다.
		// 패턴 도구가 캡처·배치 중이거나 왼쪽 버튼이 아니면 그리지 않는다.

		MouseAdapter brush = new MouseAdapter()		//{=Universe.mouse}
		{	private Point	lastCell;
			private boolean paintsAlive;

			public void mousePressed(MouseEvent e)
			{	lastCell = null;
				if (patternTools.isEditing() || freezingTools.isEditing()
                        || !SwingUtilities.isLeftMouseButton(e)
                        || !contains(e.getPoint())) return;
				lastCell	= cellAt( e.getPoint() );
				if (freezingTools.manager().isFrozen(lastCell))	// [Freezing] no stroke starts on a frozen cell
				{	freezingTools.blockedEdit();
					lastCell = null;
					return;
				}
				paintsAlive = !outermostCell.isAliveAt(lastCell.y, lastCell.x);
				if (setCell(lastCell, paintsAlive))
                    notifyBoardChanged(BoardChangeEvent.Kind.EDIT);
				repaint();
			}

			public void mouseDragged(MouseEvent e)
			{	if (lastCell == null || patternTools.isEditing() || freezingTools.isEditing()) return;
				Point cell = cellAt( e.getPoint() );

				// 빠르게 끌면 칸을 건너뛰므로 마지막 두 마우스 위치
				// 사이의 칸을 모두 채운다.
				int columns = cell.x - lastCell.x;
				int rows	= cell.y - lastCell.y;
				int steps	= Math.max( Math.abs(columns), Math.abs(rows) );

				boolean changed = false;
				for( int step = 1; step <= steps; ++step )
					changed |= setCell( new Point( lastCell.x + columns * step / steps,
										lastCell.y + rows    * step / steps ),
							 paintsAlive );

				lastCell = cell;
                if (changed) notifyBoardChanged(BoardChangeEvent.Kind.EDIT);
				repaint();
			}
		};
		addMouseListener( brush );
		addMouseMotionListener( brush );

		MenuSite.addLine( this, "Grid", "Clear",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	if (patternTools.isEditing()) patternTools.cancel();
					patternTools.boardChanged();
					outermostCell.clear();
                    generation = 0;
                    freezingTools.reset();				// [Freezing]
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

		Clock.instance().addClockListener //{=Universe.clock.subscribe}
		(	new Clock.Listener()
			{	public void tick()
				{	if (patternTools.isEditing() || freezingTools.isEditing()) return;
					patternTools.boardChanged();
					outermostCell.figureNextState
						   ( Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,
							 Cell.DUMMY,Cell.DUMMY,Cell.DUMMY,Cell.DUMMY
						   );
					boolean[][] before = liveCells();

					// [Freezing] 규칙 계산(figureNextState)과 전이(transition)는
					// 그대로 두고, 그 사이에서 각 Freezing 영역이 자기 State에
					// 물어 다음 상태를 스냅샷으로 되돌린다.
					FreezingManager freezing = freezingTools.manager();
					freezing.beforeTransition( outermostCell );

					// 바뀌는 셀이 없어도 매 틱 불러야 멈춘 패턴도 나이를 먹는다
					boolean changed = outermostCell.transition();

					// 같은 완료 이벤트를 통계와 사운드에 전달한다.
					// 고정된 셀은 before와 같으므로 출생·사망 목록에 들어가지 않는다.
					generation++;
					freezing.afterGeneration();			// [Freezing] 남은 세대 감소, 0이면 Frozen -> Normal
					notifyBoardChanged(BoardChangeEvent.Kind.GENERATION, before);

					// 색상 또는 생존 상태가 바뀌면 보드와 편집 오버레이를 함께 다시 그린다
					if( Resident.colorChangesWithAge() )
						refreshNow();
					else if( changed )
						refreshNow();

				}
			}
		);

		// Go 메뉴는 위의 Clock.instance()에서 생기므로 그 뒤에 추가해야 Go 다음에 붙는다
		MenuSite.addLine
		(	this, "Color", "Single Color",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	useColorPolicy( new FixedColorPolicy( Resident.LIVE_COLOR ) );
				}
			}
		);

		MenuSite.addLine
		(	this, "Color", "Cycle by Generation",
			new ActionListener()
			{	public void actionPerformed(ActionEvent e)
				{	// 10세대마다 빨강 → 파랑 → 초록 (적록 색약도 구분되는 색)
					useColorPolicy( new CyclingColorPolicy
						( 10, Resident.LIVE_COLOR, new Color(0x0072B2), new Color(0x009E73) ) );
				}
			}
		);
	}

	private void useColorPolicy( CellColorPolicy policy )
	{	Resident.setColorPolicy( policy );
		notifyBoardChanged( BoardChangeEvent.Kind.COLOR_POLICY_CHANGED );
		repaint();
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
            listener.onBoardChanged(createBoardChangeEvent(BoardChangeEvent.Kind.INITIAL, null));
        }
    }

    /** Removing an absent listener is harmless. Call on the EDT. */
    public void removeBoardChangeListener(BoardChangeListener listener) {
        requireEventDispatchThread();
        boardListeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Publish a completed edit/restore on the EDT. Does not change the board or
     * generation. GENERATION is emitted only by the Clock listener, with its
     * actual pre-transition state. Observers must not change the board in callbacks.
     */
    public void notifyBoardChanged(BoardChangeEvent.Kind kind) {
        if (kind == BoardChangeEvent.Kind.GENERATION) {
            throw new IllegalArgumentException("GENERATION requires an actual Clock transition");
        }
        notifyBoardChanged(kind, null);
    }

    private void notifyBoardChanged(BoardChangeEvent.Kind kind, boolean[][] before) {
        requireEventDispatchThread();
        Objects.requireNonNull(kind, "kind");
        if (boardListeners.isEmpty()) return;

        BoardChangeEvent event = createBoardChangeEvent(kind, before);
        // Changes to subscriptions during delivery apply to the next broadcast.
        for (BoardChangeListener listener : boardListeners.toArray(new BoardChangeListener[0])) {
            listener.onBoardChanged(event);
        }
    }

    private BoardChangeEvent createBoardChangeEvent(BoardChangeEvent.Kind kind, boolean[][] before) {
        boolean[][] after = liveCells();
        List<Point> births = new ArrayList<>();
        List<Point> deaths = new ArrayList<>();
        List<Color> aliveColors = new ArrayList<>();
        for (int row = 0; row < after.length; row++) {
            for (int column = 0; column < after.length; column++) {
                if (after[row][column]) aliveColors.add(outermostCell.colorAt(row, column));
                if (kind == BoardChangeEvent.Kind.GENERATION) {
                    if (after[row][column] && !before[row][column]) births.add(new Point(column, row));
                    else if (!after[row][column] && before[row][column]) deaths.add(new Point(column, row));
                }
            }
        }
        return new BoardChangeEvent(kind, generation, after.length, after, births, deaths, aliveColors);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Board operations must run on the Swing EDT");
        }
    }

	/** 판의 스냅샷을 [행][열] 순서로 반환한다. */
	public boolean[][] liveCells()
	{	int width = outermostCell.widthInCells();
		boolean[][] cells = new boolean[width][width];

		for( int row = 0; row < width; ++row )
			for( int column = 0; column < width; ++column )
				cells[row][column] = outermostCell.isAliveAt(row, column);

		return cells;
	}

	private static final int PLAYHEAD_WIDTH = 3;
	private static final Color PLAYHEAD_COLOR = new Color( 0, 170, 80 );

	private volatile int playhead = -1;

	/** 해당 열의 왼쪽 가장자리에 세로선을 그린다.
	 *  @param column 표시할 열. -1이면 선을 지운다.
	 */
	public void setPlayhead( int column )
	{	int previous = playhead;
		playhead = column;
		repaintPlayhead( previous );
		repaintPlayhead( column );
	}

	private void repaintPlayhead( int column )
	{	if( column >= 0 )
			repaint( column * pixelsPerCell(), 0, PLAYHEAD_WIDTH, getHeight() );
	}

	private void drawPlayhead( Graphics g )
	{	int column = playhead;
		if( column >= 0 )
		{	g.setColor( PLAYHEAD_COLOR );
			g.fillRect( column * pixelsPerCell(), 0,
						PLAYHEAD_WIDTH, getHeight() );
		}
	}

	JPanel patternControls() { return patternTools.controls(); }

    /** Finish hidden pattern input when the sidebar switches to another tool. */
    void finishPatternEditing() { patternTools.cancel(); }

	JPanel freezingControls() { return freezingTools.controls(); }	// [Freezing]

    /** [Freezing] Finish a hidden area selection when the sidebar switches to another tool. */
    void finishFreezingEditing() { freezingTools.cancel(); }

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

	private boolean setCell( Point cell, boolean alive )
	{	if( outermostCell.isAliveAt(cell.y, cell.x) == alive )
			return false;
		if( freezingTools.manager().isFrozen(cell) )	// [Freezing] a drag skips frozen cells
		{	freezingTools.blockedEdit();
			return false;
		}

		patternTools.boardChanged();
		int size  = pixelsPerCell();
		int board = size * outermostCell.widthInCells();
		outermostCell.userClicked( new Point(cell.x * size, cell.y * size),
								   new Rectangle(0, 0, board, board) );
        return true;
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

        patternTools.cancel();
        patternTools.boardChanged();
        Clock.instance().stop();
        outermostCell.clear();
        outermostCell.transfer(memento, new Point(0, 0), Cell.LOAD);
        generation = 0;
        freezingTools.reset();				// [Freezing]
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
		drawPlayhead( g );
		if (patternTools != null) patternTools.paintOverlay((Graphics2D) g);
	}

	/** Repaint the board and editing overlay together through Swing. */
	private void refreshNow()
	{	repaint();
	}
}
