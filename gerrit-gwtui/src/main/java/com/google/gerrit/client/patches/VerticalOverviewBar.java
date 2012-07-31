package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VerticalOverviewBar extends Widget implements ClickHandler {

  private class Marker {
    int position;
    int height;
    final Element marker;

    Marker(int position, int height) {
      marker = DOM.createDiv();
      this.position = position;
      this.height = height;
      marker.setClassName(Gerrit.RESOURCES.css().verticalOverviewBarMarker());
      DOM.appendChild(body, marker);
      updatePosition();
    }

    public void updatePosition() {
      float pos = (float)position * 100f / (float)totalLines;
      DOM.setStyleAttribute(marker, "top", pos + "%");
      float height = (float)this.height * 100f / (float)totalLines;
      if (height < 0.2f) {
        // This would probably work better as a # of px, but then we must handle resizes.
        height = 0.2f;
      }
      DOM.setStyleAttribute(marker, "height", height + "%");
    }
  }

  private class CommentMarker extends Marker {

    public CommentMarker(int position, int height) {
      super(position, height);
      marker.addClassName(Gerrit.RESOURCES.css()
          .verticalOverviewBarCommentMarker());
    }

  }

  public enum Side {
    LEFT, RIGHT, BOTH
  };

  private class CodeBlockMarker extends Marker {
    public CodeBlockMarker(int position, int height, Side side,
        PatchLine.Type type) {
      super(position, height);
      assert type != PatchLine.Type.CONTEXT;
      String sideClass = "";
      switch (side) {
        case LEFT:
          sideClass =
              Gerrit.RESOURCES.css().verticalOverviewBarCodeBlockMarkerLeft();
          break;
        case RIGHT:
          sideClass =
              Gerrit.RESOURCES.css().verticalOverviewBarCodeBlockMarkerRight();
          break;
      }
      marker.addClassName(sideClass);
      String typeClass = "";
      switch (type) {
        case INSERT:
          typeClass =
              Gerrit.RESOURCES.css().verticalOverviewBarCodeBlockMarkerInsert();
          break;
        case DELETE:
          typeClass =
              Gerrit.RESOURCES.css().verticalOverviewBarCodeBlockMarkerDelete();
          break;
      }
      marker.addClassName(typeClass);
    }
  }

  private static class CodeBlock {
    PatchLine.Type type;
    final Side side;
    int position;
    int height;
    public CodeBlock(Side side) {
      this.side = side;
      type = PatchLine.Type.CONTEXT;
    }
  }

  final Element body;
  final ScrollPanel scrollPanel;
  final List<Marker> markers = new ArrayList<Marker>(5);
  int totalLines = 1;
  final CodeBlock leftBlock = new CodeBlock(Side.LEFT);
  final CodeBlock rightBlock = new CodeBlock(Side.RIGHT);
  final CodeBlock bothBlock = new CodeBlock(Side.BOTH);

  public VerticalOverviewBar(final ScrollPanel scrollPanel) {
    body = DOM.createDiv();
    this.scrollPanel = scrollPanel;
    setElement(body);
    setStyleName(Gerrit.RESOURCES.css().verticalOverviewBar());
    sinkEvents(Event.ONCLICK);
    addHandler(this, ClickEvent.getType());
  }

  /**
   * Calculate the correct position (row) for a comment marker. Comment markers
   * add lines to the overview bar, but those lines aren't represented in the
   * diff table. The numbers we get as inputs are from the diff table. This would
   * also impact code block markers, except those are all added before we add the
   * comment markers.
   */
  private int realCommentPosition(int row) {
    int offset = 0;
    for(Marker marker : markers) {
      if(marker.position < row && marker instanceof CommentMarker) {
        offset += marker.height;
      }
    }
    return row + offset;
  }

  public void addCommentMarker(int row, int height) {
    row = realCommentPosition(row);
    markers.add(new CommentMarker(row, height));
    if(height > 1) {
      insertLines(height - 1, row);
    }
  }

  public void removeCommentMarker(int row) {
    row = realCommentPosition(row);
    Iterator<Marker> it = markers.iterator();
    Marker m;
    int oldHeight = 0;
    while (it.hasNext()) {
      m = it.next();
      if (m instanceof CommentMarker && m.position == row) {
        DOM.removeChild(body, m.marker);
        it.remove();
        oldHeight = m.height;
        break;
      }
    }
    if(oldHeight > 0) {
      insertLines(-oldHeight, row);
    }
  }

  public void setCommentMarkerHeight(int row, int height) {
    if(height < 1) {
      height = 1;
    }row = realCommentPosition(row);
    int oldHeight = 0;
    Marker marker = null;
    for(Marker m : markers) {
      if (m instanceof CommentMarker &&  m.position == row) {
        oldHeight = m.height;
        m.height = height;
        marker = m;
        break;
      }
    }
    if (marker != null) {
      // We should always hit this block.
      insertLines(height - oldHeight, row);
      marker.updatePosition();
    }
  }

  public void addLine(int position, Side side, PatchLine.Type type) {
    CodeBlock block;
    switch (side) {
      case LEFT:
        block = leftBlock;
        break;
      case RIGHT:
        block = rightBlock;
        break;
      default:
        block = bothBlock;
        break;
    }
    if (block.type != type) {
      if (block.type != PatchLine.Type.CONTEXT) {
        // End of last block. Create it.
        markers.add(new CodeBlockMarker(block.position, block.height,
            block.side, block.type));
      }
      block.position = position;
      block.height = 1;
      block.type = type;
    } else {
      block.height++;
    }
  }

  public void insertLines(int numLines, int after) {
    totalLines += numLines;
    for (Marker marker : markers) {
      if (marker.position > after) {
        marker.position += numLines;
      }
      marker.updatePosition();
    }
  }

  public void setTotalLines(int totalLines) {
    this.totalLines = totalLines;
    for (Marker marker : markers) {
      marker.updatePosition();
    }
  }

  @Override
  public void onClick(ClickEvent event) {
    int relY = event.getRelativeY(body);
    int maxScroll = scrollPanel.getMaximumVerticalScrollPosition();
    int height = body.getOffsetHeight();
    int position = relY * maxScroll / height;
    scrollPanel.setVerticalScrollPosition(position);
  }
}
