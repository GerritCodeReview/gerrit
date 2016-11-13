// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.change.RelatedChanges.ChangeAndCommit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class RelatedChangesTab implements IsWidget {
  private static final String OPEN = init(DOM.createUniqueId().replace('-', '_'));
  private static final HyperlinkImpl LINK = GWT.create(HyperlinkImpl.class);
  private static final SafeHtml POINTER_HTML =
      AbstractImagePrototype.create(Gerrit.RESOURCES.arrowRight()).getSafeHtml();

  private static native String init(String o) /*-{
    $wnd[o] = $entry(@com.google.gerrit.client.change.RelatedChangesTab::onOpen(
      Lcom/google/gwt/dom/client/NativeEvent;Lcom/google/gwt/dom/client/Element;));
    return o + '(event,this)';
  }-*/;

  private static boolean onOpen(NativeEvent evt, Element e) {
    if (LINK.handleAsClick(evt.<Event>cast())) {
      Gerrit.display(e.getAttribute("href").substring(1));
      evt.preventDefault();
      return false;
    }
    return true;
  }

  private final SimplePanel panel;
  private final RelatedChanges.Tab subject;

  private boolean showBranches;
  private boolean showProjects;
  private boolean showSubmittable;
  private boolean showIndirectAncestors;
  private boolean registerKeys;
  private int maxHeight;

  private String project;
  private NavigationList view;

  RelatedChangesTab(RelatedChanges.Tab subject) {
    panel = new SimplePanel();
    this.subject = subject;
  }

  @Override
  public Widget asWidget() {
    return panel;
  }

  void setShowBranches(boolean showBranches) {
    this.showBranches = showBranches;
  }

  void setShowProjects(boolean showProjects) {
    this.showProjects = showProjects;
  }

  void setShowSubmittable(boolean submittable) {
    this.showSubmittable = submittable;
  }

  void setShowIndirectAncestors(boolean showIndirectAncestors) {
    this.showIndirectAncestors = showIndirectAncestors;
  }

  void setMaxHeight(int height) {
    maxHeight = height;
    if (view != null) {
      view.setHeight(height + "px");
      view.ensureRowMeasurements();
      view.movePointerTo(view.selectedRow, true);
    }
  }

  void registerKeys(boolean on) {
    registerKeys = on;
    if (view != null) {
      view.setRegisterKeys(on);
    }
  }

  void setError(String message) {
    panel.setWidget(new InlineLabel(message));
    view = null;
    project = null;
  }

  void setChanges(String project, String revision, JsArray<ChangeAndCommit> changes) {
    if (0 == changes.length()) {
      setError(Resources.C.noChanges());
      return;
    }

    this.project = project;
    view = new NavigationList();
    panel.setWidget(view);

    DisplayCommand display = new DisplayCommand(revision, changes, view);
    if (display.execute()) {
      Scheduler.get().scheduleIncremental(display);
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final String revision;
    private final JsArray<ChangeAndCommit> changes;
    private final List<SafeHtml> rows;
    private final Set<String> connected;
    private final NavigationList navList;

    private double start;
    private int row;
    private int connectedPos;
    private int selected;

    private DisplayCommand(
        String revision, JsArray<ChangeAndCommit> changes, NavigationList navList) {
      this.revision = revision;
      this.changes = changes;
      this.navList = navList;
      rows = new ArrayList<>(changes.length());
      connectedPos = changes.length() - 1;
      connected =
          showIndirectAncestors ? new HashSet<>(Math.max(changes.length() * 4 / 3, 16)) : null;
    }

    private boolean computeConnected() {
      // Since TOPO sorted, when can walk the list in reverse and find all
      // the connections.
      if (!connected.contains(revision)) {
        while (connectedPos >= 0) {
          CommitInfo c = changes.get(connectedPos).commit();
          connected.add(c.commit());
          if (longRunning(--connectedPos)) {
            return true;
          }
          if (c.commit().equals(revision)) {
            break;
          }
        }
      }
      while (connectedPos >= 0) {
        CommitInfo c = changes.get(connectedPos).commit();
        for (int j = 0; j < c.parents().length(); j++) {
          if (connected.contains(c.parents().get(j).commit())) {
            connected.add(c.commit());
            break;
          }
        }
        if (longRunning(--connectedPos)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean execute() {
      if (navList != view || !panel.isAttached()) {
        // If the user navigated away, we aren't in the DOM anymore.
        // Don't continue to render.
        return false;
      }

      start = System.currentTimeMillis();

      if (connected != null && computeConnected()) {
        return true;
      }

      while (row < changes.length()) {
        ChangeAndCommit info = changes.get(row);
        String commit = info.commit().commit();
        rows.add(new RowSafeHtml(info, connected != null && !connected.contains(commit)));
        if (revision.equals(commit)) {
          selected = row;
        }
        if (longRunning(++row)) {
          return true;
        }
      }

      navList.rows = rows;
      navList.ensureRowMeasurements();
      navList.movePointerTo(selected, true);
      return false;
    }

    private boolean longRunning(int i) {
      return (i % 10) == 0 && System.currentTimeMillis() - start > 50;
    }
  }

  @SuppressWarnings("serial")
  private class RowSafeHtml implements SafeHtml {
    private String html;
    private ChangeAndCommit info;
    private final boolean notConnected;

    RowSafeHtml(ChangeAndCommit info, boolean notConnected) {
      this.info = info;
      this.notConnected = notConnected;
    }

    @Override
    public String asString() {
      if (html == null) {
        SafeHtmlBuilder sb = new SafeHtmlBuilder();
        renderRow(sb);
        html = sb.asString();
        info = null;
      }
      return html;
    }

    private void renderRow(SafeHtmlBuilder sb) {
      sb.openDiv().setStyleName(RelatedChanges.R.css().row());

      sb.openSpan().setStyleName(RelatedChanges.R.css().pointer());
      sb.append(POINTER_HTML);
      sb.closeSpan();

      if (info.status() == Change.Status.ABANDONED) {
        sb.openSpan().setStyleName(RelatedChanges.R.css().strikedSubject());
      } else {
        sb.openSpan().setStyleName(RelatedChanges.R.css().subject());
      }
      sb.setAttribute("data-branch", info.branch());
      sb.setAttribute("data-project", info.project());
      String url = url();
      if (url != null) {
        sb.openAnchor().setAttribute("href", url);
        if (url.startsWith("#")) {
          sb.setAttribute("onclick", OPEN);
        }
        sb.setAttribute("title", info.commit().subject());
        if (showProjects) {
          sb.append(info.project()).append(": ");
        }
        if (showBranches) {
          sb.append(info.branch()).append(": ");
        }
        sb.append(info.commit().subject());
        sb.closeAnchor();
      } else {
        sb.append(info.commit().subject());
      }
      sb.closeSpan();

      sb.openSpan();
      if (info.status() != null && !info.status().isOpen()) {
        sb.setStyleName(RelatedChanges.R.css().gitweb());
        sb.setAttribute("title", Util.toLongString(info.status()));
        sb.append('\u25CF'); // Unicode 'BLACK CIRCLE'
      } else if (notConnected) {
        sb.setStyleName(RelatedChanges.R.css().indirect());
        sb.setAttribute("title", Resources.C.indirectAncestor());
        sb.append('~');
      } else if (info.hasCurrentRevisionNumber()
          && info.hasRevisionNumber()
          && info._currentRevisionNumber() != info._revisionNumber()) {
        sb.setStyleName(RelatedChanges.R.css().notCurrent());
        sb.setAttribute("title", Util.C.notCurrent());
        sb.append('\u25CF'); // Unicode 'BLACK CIRCLE'
      } else if (showSubmittable && info.submittable()) {
        sb.setStyleName(RelatedChanges.R.css().submittable());
        sb.setAttribute("title", Util.C.submittable());
        sb.append('\u2713'); // Unicode 'CHECK MARK'
      } else {
        sb.setStyleName(RelatedChanges.R.css().current());
      }
      sb.closeSpan();

      sb.closeDiv();
    }

    private String url() {
      if (info.hasChangeNumber() && info.hasRevisionNumber()) {
        return "#" + PageLinks.toChange(info.patchSetId());
      }
      return null;
    }
  }

  private class NavigationList extends ScrollPanel
      implements ClickHandler, DoubleClickHandler, ScrollHandler {
    private final KeyCommandSet keysNavigation;
    private final Element body;
    private final Element surrogate;
    private final Node fragment = createDocumentFragment();

    List<SafeHtml> rows;
    private HandlerRegistration regNavigation;
    private int selectedRow;
    private int startRow;
    private int rowHeight;
    private int rowWidth;

    NavigationList() {
      addDomHandler(this, ClickEvent.getType());
      addDomHandler(this, DoubleClickEvent.getType());
      addScrollHandler(this);

      keysNavigation = new KeyCommandSet(Resources.C.relatedChanges());
      keysNavigation.add(
          new KeyCommand(0, 'K', Resources.C.previousChange()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              movePointerTo(selectedRow - 1, true);
            }
          },
          new KeyCommand(0, 'J', Resources.C.nextChange()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              movePointerTo(selectedRow + 1, true);
            }
          });
      keysNavigation.add(
          new KeyCommand(0, 'O', Resources.C.openChange()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              onOpenRow(getRow(selectedRow));
            }
          });

      if (maxHeight > 0) {
        setHeight(maxHeight + "px");
      }

      body = DOM.createDiv();
      body.getStyle().setPosition(Style.Position.RELATIVE);
      body.getStyle().setVisibility(Visibility.HIDDEN);
      getContainerElement().appendChild(body);

      surrogate = DOM.createDiv();
      surrogate.getStyle().setVisibility(Visibility.HIDDEN);
    }

    private boolean ensureRowMeasurements() {
      if (rowHeight == 0 && rows != null) {
        surrogate.setInnerSafeHtml(rows.get(0));
        getContainerElement().appendChild(surrogate);
        rowHeight = surrogate.getOffsetHeight();
        rowWidth = surrogate.getOffsetWidth();
        getContainerElement().removeChild(surrogate);
        getContainerElement().getStyle().setHeight(rowHeight * rows.size(), Style.Unit.PX);
        return true;
      }
      return false;
    }

    public void movePointerTo(int row, boolean scroll) {
      if (rows != null && 0 <= row && row < rows.size()) {
        renderSelected(selectedRow, false);
        selectedRow = row;

        if (scroll && rowHeight != 0) {
          // Position the selected row in the middle.
          setVerticalScrollPosition(Math.max(rowHeight * selectedRow - maxHeight / 2, 0));
          render();
        }
        renderSelected(selectedRow, true);
      }
    }

    private void renderSelected(int row, boolean selected) {
      Element e = getRow(row);
      if (e != null) {
        if (selected) {
          e.addClassName(RelatedChanges.R.css().activeRow());
        } else {
          e.removeClassName(RelatedChanges.R.css().activeRow());
        }
      }
    }

    private void render() {
      if (rows == null || rowHeight == 0) {
        return;
      }

      int currStart = startRow;
      int currEnd = startRow + body.getChildCount();

      int vpos = getVerticalScrollPosition();
      int start = Math.max(vpos / rowHeight - 5, 0);
      int end = Math.min((vpos + maxHeight) / rowHeight + 5, rows.size());
      if (currStart <= start && end <= currEnd) {
        return; // All of the required nodes are already in the DOM.
      }

      if (end <= currStart) {
        renderRange(start, end, true, true);
      } else if (start < currStart) {
        renderRange(start, currStart, false, true);
      } else if (start >= currEnd) {
        renderRange(start, end, true, false);
      } else if (end > currEnd) {
        renderRange(currEnd, end, false, false);
      }

      renderSelected(selectedRow, true);

      if (currEnd == 0) {
        // Account for the scroll bars
        int width = body.getOffsetWidth();
        if (rowWidth > width) {
          int w = 2 * rowWidth - width;
          setWidth(w + "px");
        }
        body.getStyle().clearVisibility();
      }
    }

    private void renderRange(int start, int end, boolean removeAll, boolean insertFirst) {
      SafeHtmlBuilder sb = new SafeHtmlBuilder();
      for (int i = start; i < end; i++) {
        sb.append(rows.get(i));
      }

      if (removeAll) {
        body.setInnerSafeHtml(sb);
      } else {
        surrogate.setInnerSafeHtml(sb);
        for (int cnt = surrogate.getChildCount(); cnt > 0; cnt--) {
          fragment.appendChild(surrogate.getFirstChild());
        }
        if (insertFirst) {
          body.insertFirst(fragment);
        } else {
          body.appendChild(fragment);
        }
      }

      if (insertFirst || removeAll) {
        startRow = start;
        body.getStyle().setTop(start * rowHeight, Style.Unit.PX);
      }
    }

    @Override
    public void onClick(ClickEvent event) {
      Element row = getRow(event.getNativeEvent().getEventTarget().<Element>cast());
      if (row != null) {
        movePointerTo(startRow + DOM.getChildIndex(body, row), false);
        event.stopPropagation();
      }
      saveSelectedTab();
    }

    @Override
    public void onDoubleClick(DoubleClickEvent event) {
      Element row = getRow(event.getNativeEvent().getEventTarget().<Element>cast());
      if (row != null) {
        movePointerTo(startRow + DOM.getChildIndex(body, row), false);
        onOpenRow(row);
        event.stopPropagation();
      }
    }

    @Override
    public void onScroll(ScrollEvent event) {
      render();
    }

    private Element getRow(Element e) {
      for (Element prev = e; e != null; prev = e) {
        if ((e = DOM.getParent(e)) == body) {
          return prev;
        }
      }
      return null;
    }

    private Element getRow(int row) {
      if (startRow <= row && row < startRow + body.getChildCount()) {
        return body.getChild(row - startRow).cast();
      }
      return null;
    }

    private void onOpenRow(Element row) {
      // Find the first HREF of the anchor of the select row (if any)
      if (row != null) {
        NodeList<Element> nodes = row.getElementsByTagName(AnchorElement.TAG);
        for (int i = 0; i < nodes.getLength(); i++) {
          String url = nodes.getItem(i).getAttribute("href");
          if (!url.isEmpty()) {
            if (url.startsWith("#")) {
              Gerrit.display(url.substring(1));
            } else {
              Window.Location.assign(url);
            }
            break;
          }
        }
      }

      saveSelectedTab();
    }

    private void saveSelectedTab() {
      RelatedChanges.setSavedTab(subject);
    }

    @Override
    protected void onLoad() {
      super.onLoad();
      setRegisterKeys(registerKeys);
    }

    @Override
    protected void onUnload() {
      setRegisterKeys(false);
      super.onUnload();
    }

    public void setRegisterKeys(boolean on) {
      if (on && isAttached()) {
        if (regNavigation == null) {
          regNavigation = GlobalKey.add(this, keysNavigation);
        }
        if (view.ensureRowMeasurements()) {
          view.movePointerTo(view.selectedRow, true);
        }
      } else if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }
    }
  }

  private static native Node createDocumentFragment() /*-{
    return $doc.createDocumentFragment();
  }-*/;
}
