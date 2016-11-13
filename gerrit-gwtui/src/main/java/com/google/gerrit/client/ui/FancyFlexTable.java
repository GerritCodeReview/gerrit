// Copyright (C) 2008 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import java.util.Comparator;
import java.util.Iterator;

public abstract class FancyFlexTable<RowItem> extends Composite {
  private static final FancyFlexTableImpl impl = GWT.create(FancyFlexTableImpl.class);

  protected static final int C_ARROW = 0;

  protected final MyFlexTable table;

  protected FancyFlexTable() {
    table = createFlexTable();
    table.addStyleName(Gerrit.RESOURCES.css().changeTable());
    table.setWidth("100%");
    initWidget(table);

    table.setText(0, C_ARROW, "");
    table.getCellFormatter().addStyleName(0, C_ARROW, Gerrit.RESOURCES.css().iconHeader());
  }

  protected MyFlexTable createFlexTable() {
    return new MyFlexTable();
  }

  protected RowItem getRowItem(final int row) {
    return FancyFlexTable.<RowItem>getRowItem(table.getCellFormatter().getElement(row, 0));
  }

  protected void setRowItem(final int row, final RowItem item) {
    setRowItem(table.getCellFormatter().getElement(row, 0), item);
  }

  /**
   * Finds an item in the table.
   *
   * @param comparator comparator by which the items in the table are sorted
   * @param item the item that should be found
   * @return if the item is found the number of the row that contains the item; if the item is not
   *     found {@code -1}
   */
  protected int findRowItem(Comparator<RowItem> comparator, RowItem item) {
    int row = lookupRowItem(comparator, item);
    if (row < table.getRowCount() && comparator.compare(item, getRowItem(row)) == 0) {
      return row;
    }
    return -1;
  }

  /**
   * Finds the number of the row where a new item should be inserted into the table.
   *
   * @param comparator comparator by which the items in the table are sorted
   * @param item the new item that should be inserted
   * @return if the item is not yet contained in the table, the number of the row where the new item
   *     should be inserted; if the item is already contained in the table {@code -1}
   */
  protected int getInsertRow(Comparator<RowItem> comparator, RowItem item) {
    int row = lookupRowItem(comparator, item);
    if (row >= table.getRowCount() || comparator.compare(item, getRowItem(row)) != 0) {
      return row;
    }
    return -1;
  }

  /**
   * Makes a binary search for the given row item over the table.
   *
   * @param comparator comparator by which the items in the table are sorted
   * @param item the item that should be looked up
   * @return if the item is found the number of the row that contains the item; if the item is not
   *     found the number of the row where the item should be inserted according to the given
   *     comparator.
   */
  private int lookupRowItem(Comparator<RowItem> comparator, RowItem item) {
    int left = 1;
    int right = table.getRowCount() - 1;
    while (left <= right) {
      int middle = (left + right) >>> 1; // (left+right)/2
      RowItem i = getRowItem(middle);
      int cmp = comparator.compare(i, item);

      if (cmp < 0) {
        left = middle + 1;
      } else if (cmp > 0) {
        right = middle - 1;
      } else {
        // item is already contained in the table
        return middle;
      }
    }
    return left;
  }

  protected void resetHtml(final SafeHtml body) {
    for (final Iterator<Widget> i = table.iterator(); i.hasNext(); ) {
      i.next();
      i.remove();
    }
    impl.resetHtml(table, body);
  }

  protected void scrollIntoView(final int topRow, final int endRow) {
    final CellFormatter fmt = table.getCellFormatter();
    final Element top = fmt.getElement(topRow, C_ARROW).getParentElement();
    final Element end = fmt.getElement(endRow, C_ARROW).getParentElement();

    final int rTop = top.getAbsoluteTop();
    final int rEnd = end.getAbsoluteTop() + end.getOffsetHeight();
    final int rHeight = rEnd - rTop;

    final int sTop = Document.get().getScrollTop();
    final int sHeight = Document.get().getClientHeight();
    final int sEnd = sTop + sHeight;

    final int nTop;
    if (sHeight <= rHeight) {
      // The region is larger than the visible area, make the top
      // exactly the top of the region, its the most visible area.
      //
      nTop = rTop;
    } else if (sTop <= rTop && rTop <= sEnd) {
      // At least part of the region is already visible.
      //
      if (rEnd <= sEnd) {
        // ... actually its all visible. Don't scroll.
        //
        return;
      }

      // Move only enough to make the end visible.
      //
      nTop = sTop + (rHeight - (sEnd - rTop));
    } else {
      // None of the region is visible. Make it visible.
      //
      nTop = rTop;
    }
    Document.get().setScrollTop(nTop);
  }

  protected void applyDataRowStyle(final int newRow) {
    table.getCellFormatter().addStyleName(newRow, C_ARROW, Gerrit.RESOURCES.css().iconCell());
    table.getCellFormatter().addStyleName(newRow, C_ARROW, Gerrit.RESOURCES.css().leftMostCell());
  }

  /**
   * Get the td element that contains another element.
   *
   * @param target the child element whose parent td is required.
   * @return the td containing element {@code target}; null if {@code target} is not a member of
   *     this table.
   */
  protected Element getParentCell(final Element target) {
    final Element body = FancyFlexTableImpl.getBodyElement(table);
    for (Element td = target; td != null && td != body; td = DOM.getParent(td)) {
      // If it's a TD, it might be the one we're looking for.
      if ("td".equalsIgnoreCase(td.getTagName())) {
        // Make sure it's directly a part of this table.
        Element tr = DOM.getParent(td);
        if (DOM.getParent(tr) == body) {
          return td;
        }
      }
    }
    return null;
  }

  /** @return the row of the child element; -1 if the child is not in the table. */
  protected int rowOf(final Element target) {
    final Element td = getParentCell(target);
    if (td == null) {
      return -1;
    }
    final Element tr = DOM.getParent(td);
    final Element body = DOM.getParent(tr);
    return DOM.getChildIndex(body, tr);
  }

  /** @return the cell of the child element; -1 if the child is not in the table. */
  protected int columnOf(final Element target) {
    final Element td = getParentCell(target);
    if (td == null) {
      return -1;
    }
    final Element tr = DOM.getParent(td);
    return DOM.getChildIndex(tr, td);
  }

  protected static class MyFlexTable extends FlexTable {}

  private static native <ItemType> void setRowItem(Element td, ItemType c)
      /*-{ td['__gerritRowItem'] = c; }-*/ ;

  private static native <ItemType> ItemType getRowItem(Element td)
      /*-{ return td['__gerritRowItem']; }-*/ ;
}
