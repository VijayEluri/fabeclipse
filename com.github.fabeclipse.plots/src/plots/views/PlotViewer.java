package plots.views;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import plots.coord.IPlottable;

class PlotViewer {
	final class PlotMouseMoveListener implements MouseMoveListener {
			private int startx = 0;
			private boolean dragging = false;
			@Override
			public void mouseMove(MouseEvent e) {
				if (e.stateMask == SWT.BUTTON1) {
					// drag
					if (!dragging) {
						startx = e.x;
						dragging = true;
					} else {
						int diffx = e.x - startx;
						if (diffx != 0) {
							PlotViewer pv = (PlotViewer) e.widget.getData();
							double dr = pv.rangeEnd - pv.rangeStart;
							double rr = pv.plotRect.width;
							double ddiff = ((double) diffx) * dr / rr;
							pv.setDomainRange(pv.rangeStart - ddiff, pv.rangeEnd - ddiff);
							rangeChange.accept(pv);
						}
						startx = e.x;
					}
				} else
					dragging = false;
			}
		}

	// UI
	public Composite canvas;
	public Sash sash;
	private Action actionFit;
	private Action actionZoomIn;
	private Action actionZoomOut;
	private Action actionRemove;

	// current range
	double rangeStart;
	double rangeEnd;
	
	// model
	public List<IPlottable> plots = new ArrayList<>();
	public List<Color> colors = new ArrayList<>();
	public Rectangle plotRect = new Rectangle(10, 10, 600, 200);

	// others
	public Consumer<Rectangle> rectChange   = r -> {};
	public Consumer<PlotViewer> rangeChange = pv -> {};
	private Color color;

	public PlotViewer(Composite parent) {
		createCanvas(parent);
		makeActions();
		hookContextMenu();
	}

	public PlotViewer onRectChange(Consumer<Rectangle> rc) {
		rectChange = rc;
		return this;
	}

	public PlotViewer onRangeChange(Consumer<PlotViewer> rc) {
		rangeChange = rc;
		return this;
	}

	public void addPlot(IPlottable p) {
		plots.add(p);
	}

	public void setPlotRect(Rectangle r) {
		if (!plotRect.equals(r)) {
			plotRect.x = r.x;
			plotRect.y = r.y;
			plotRect.width  = r.width;
			plotRect.height = r.height;
			canvas.redraw();
		}
	}

	public void setDomainRange(double start, double end) {
		if (start != rangeStart || end != rangeEnd) {
			System.out.printf("PlotViewer.setDomainRange(start=%f, end=%f)\n", start, end);
			rangeStart = start;
			rangeEnd = end;
			for (IPlottable p : plots) {
				p.setDomainRange(start, end);
			}
			canvas.redraw();
		}
	}

	private void modifyPlotRect(Rectangle r) {
		setPlotRect(r);
		rectChange.accept(plotRect);
	}

	private PlotViewer zoomXAxis(double factor, double x) {
		double c = x;
		double start = c - (c - rangeStart) * factor;
		double end   = c + (rangeEnd - c) * factor;
		setDomainRange(start, end);
		return this;
	}
	PlotViewer zoomIn(double centre) {
		return zoomXAxis(1.2, centre);
	}
	PlotViewer zoomOut(double centre) {
		if (plotRect.width < 10)
			return this; // do not zoom too much
		return zoomXAxis(1/1.2, centre);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(actionFit);
		manager.add(actionZoomIn);
		manager.add(actionZoomOut);
		manager.add(actionRemove);
	}

	private void makeActions() {
		actionFit = new Action() {
			public void run() {
				Point size = canvas.getSize();
				Rectangle r = new Rectangle(0, 0, size.x, size.y);
				modifyPlotRect(r);
			}
		};
		actionFit.setText("Fit Plot");
		actionFit.setToolTipText("Fit the plot to the current size");
		actionFit.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
			getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		
		actionZoomIn = new LambdaAction(a -> {zoomIn(0);});
		actionZoomIn.setText("><");
		actionZoomIn.setToolTipText("Zoom in");

		actionZoomOut = new LambdaAction(a -> {zoomOut(0);});
		actionZoomOut.setText("<>");
		actionZoomOut.setToolTipText("Zoom out");
		
		actionRemove = new LambdaAction(a -> {canvas.dispose();});
		actionRemove.setText("Remove");
		actionRemove.setToolTipText("Remove this plot");
	}

	private void createCanvas(Composite parent) {
		Composite canvas = new Canvas(parent, SWT.NO_REDRAW_RESIZE | SWT.DOUBLE_BUFFERED | SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false).hint(SWT.DEFAULT, 100).applyTo(canvas);
		canvas.addPaintListener(PlotViewer::paintPlot);
		canvas.addMouseMoveListener(new PlotMouseMoveListener());
		canvas.setData(this);
		color = new Color(parent.getDisplay(), 255, 255, 255);
		canvas.setBackground(color);
		this.canvas = canvas;
		if (!plots.isEmpty()) {
			String toolTip = "";
			for (IPlottable p : plots) {
				toolTip += "data.len=" + p.getCount() + "\n";
			}
			canvas.setToolTipText(toolTip );
		}
		canvas.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				PlotViewer pv = (PlotViewer) e.widget.getData();
				// remove associated sash, if present
				if (pv.sash != null)
					pv.sash.dispose();
				if (color != null && !color.isDisposed())
					color.dispose();
			}
		});
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(manager -> {fillContextMenu(manager);});
		Menu menu = menuMgr.createContextMenu(canvas);
		canvas.setMenu(menu);
	}

	private static void paintPlot(PaintEvent e) {
		PlotViewer pp = (PlotViewer) e.widget.getData();
		Color c = e.gc.getForeground();
		for (int i = 0; i < pp.plots.size(); i++) {
			if (i < pp.colors.size())
				e.gc.setForeground(pp.colors.get(i));
			else
				e.gc.setForeground(c);
			pp.plots.get(i).plot(e.gc, pp.plotRect);
		}
	}

}