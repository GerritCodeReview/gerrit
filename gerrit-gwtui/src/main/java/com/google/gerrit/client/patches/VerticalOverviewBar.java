package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

public class VerticalOverviewBar extends Widget implements ClickHandler {

  private class Marker {
    int position;
    final Element marker;

    Marker(int position) {
      marker = DOM.createDiv();
      this.position = position;
      marker.setClassName(Gerrit.RESOURCES.css().verticalOverviewBarMarker());
      DOM.appendChild(body, marker);
      updatePosition();
    }

    public void updatePosition() {
      int pos = position * 100 / totalLines;
      DOM.setStyleAttribute(marker, "top", pos + "%");
    }
  }

  private class CommentMarker extends Marker {

    public CommentMarker(int position) {
      super(position);
      marker.addClassName(Gerrit.RESOURCES.css()
          .verticalOverviewBarCommentMarker());
    }

  }

  public enum Side {
    LEFT, RIGHT, BOTH
  };

  private class CodeBlockMarker extends Marker {
    final int height;

    public CodeBlockMarker(int position, int height, Side side,
        PatchLine.Type type) {
      super(position);
      assert type != PatchLine.Type.CONTEXT;
      this.height = height;
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

    @Override
    public void updatePosition() {
      super.updatePosition();
      float height = this.height * 100 / totalLines;
      if (height < 0.2f) {
        // This would probably work better as a # of px, but then we must handle resizes.
        height = 0.2f;
      }
      DOM.setStyleAttribute(marker, "height", height + "%");
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
  final PriorityQueue<Marker> markerQueue =
      new PriorityQueue<VerticalOverviewBar.Marker>(5,
          new Comparator<Marker>() {
            @Override
            public int compare(Marker a, Marker b) {
              return a.position - b.position;
            }
          });
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

  public void addCommentMarker(int position) {
    markerQueue.add(new CommentMarker(position));
  }

  public void removeCommentMarker(int row) {
    Iterator<Marker> it = markerQueue.iterator();
    while (it.hasNext()) {
      Marker m = it.next();
      if (m.position == row) {
        DOM.removeChild(body, m.marker);
        it.remove();
        return;
      }
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
        markerQueue.add(new CodeBlockMarker(block.position, block.height,
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
    for (Marker marker : markerQueue) {
      if (marker.position > after) {
        marker.position += numLines;
      }
      marker.updatePosition();
    }
  }

  public void setTotalLines(int totalLines) {
    this.totalLines = totalLines;
    for (Marker marker : markerQueue) {
      marker.updatePosition();
    }
  }

  @Override
  public void onClick(ClickEvent event) {
    int position =
        event.getRelativeY(body)
            * scrollPanel.getMaximumVerticalScrollPosition()
            / (body.getAbsoluteBottom() - body.getAbsoluteTop());
    scrollPanel.setVerticalScrollPosition(position);
  }
}
