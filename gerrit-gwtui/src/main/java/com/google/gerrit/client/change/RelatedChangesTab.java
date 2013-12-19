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
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.change.RelatedChanges.ChangeAndCommit;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.AnchorElement;
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
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
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
  private static final HyperlinkImpl link = GWT.create(HyperlinkImpl.class);

  private static final native String init(String o) /*-{
    $wnd[o] = $entry(@com.google.gerrit.client.change.RelatedChangesTab::onOpen(Lcom/google/gwt/dom/client/NativeEvent;Ljava/lang/String;));
    return o;
  }-*/;

  private static boolean onOpen(NativeEvent e, String token) {
    if (link.handleAsClick(e.<Event> cast())) {
      Gerrit.display(token);
      e.preventDefault();
      return false;
    }
    return true;
  }

  private final SimplePanel panel;

  private boolean showBranches;
  private boolean showIndirectAncestors;
  private boolean registerKeys;
  private int maxHeight;

  private NavigationList view;

  RelatedChangesTab() {
    panel = new SimplePanel();
  }

  @Override
  public Widget asWidget() {
    return panel;
  }

  void setShowBranches(boolean showBranches) {
    this.showBranches = showBranches;
  }

  void setShowIndirectAncestors(boolean showIndirectAncestors) {
    this.showIndirectAncestors = showIndirectAncestors;
  }

  void setMaxHeight(int height) {
    maxHeight = height;
    if (view != null) {
      view.setHeight(height + "px");
      view.movePointerTo(view.selectedRow);
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
  }

  void setChanges(String project, String revision, JsArray<ChangeAndCommit> changes) {
    if (0 == changes.length()) {
      setError(Resources.C.noChanges());
      return;
    }

    view = new NavigationList();
    panel.setWidget(view);

    DisplayCommand display = new DisplayCommand(project, revision, changes, view);
    if (display.execute()) {
      Scheduler.get().scheduleIncremental(display);
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final String project;
    private final String revision;
    private final JsArray<ChangeAndCommit> changes;
    private final List<SafeHtml> rows;
    private final Set<String> connected;
    private final NavigationList navList;

    private boolean attached;
    private double start;
    private int row;
    private int connectedPos;

    private DisplayCommand(String project, String revision,
        JsArray<ChangeAndCommit> changes, NavigationList navList) {
      this.project = project;
      this.revision = revision;
      this.changes = changes;
      this.navList = navList;
      rows = new ArrayList<SafeHtml>(changes.length());
      connectedPos = changes.length() - 1;
      connected = showIndirectAncestors
          ? new HashSet<String>(Math.max(changes.length() * 4 / 3, 16))
          : null;
    }

    private boolean computeConnected() {
      if (connected == null) {
        return false;
      }

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

    public boolean execute() {
      if (navList != view) {
        return false;
      }

      boolean attachedNow = panel.isAttached();
      if (!attached && attachedNow) {
        // Remember that we have been attached at least once. If
        // later we find we aren't attached we should stop running.
        attached = true;
      } else if (attached && !attachedNow) {
        // If the user navigated away, we aren't in the DOM anymore.
        // Don't continue to render.
        return false;
      }

      start = System.currentTimeMillis();

      if (computeConnected()) {
        return true;
      }

      int select = 0;
      while (row < changes.length()) {
        ChangeAndCommit c = changes.get(row);
        SafeHtmlBuilder sb = new SafeHtmlBuilder();
        renderRow(sb, c);
        rows.add(sb);
        if (revision.equals(c.commit().commit())) {
          select = row;
        }
        if (longRunning(++row)) {
          return true;
        }
      }

      navList.rows = rows;
      navList.movePointerTo(select);
      return false;
    }

    private String url(ChangeAndCommit c) {
      if (c.has_change_number() && c.has_revision_number()) {
        PatchSet.Id id = c.patch_set_id();
        return "#" + PageLinks.toChange(
            id.getParentKey(),
            String.valueOf(id.get()));
      }

      GitwebLink gw = Gerrit.getGitwebLink();
      if (gw != null) {
        return gw.toRevision(project, c.commit().commit());
      }
      return null;
    }

    private void renderRow(SafeHtmlBuilder sb, ChangeAndCommit info) {
      sb.openSpan().setStyleName(FileTable.R.css().pointer()).closeSpan();

      sb.openSpan().addStyleName(Gerrit.RESOURCES.css().relatedChangesSubject());
      String url = url(info);
      if (url != null) {
        sb.openAnchor().setAttribute("href", url);
        if (url.startsWith("#")) {
          sb.setAttribute("onclick", OPEN + "(event,\"" + url.substring(1) + "\")");
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
      GitwebLink gw = Gerrit.getGitwebLink();
      if (gw != null && (!info.has_change_number() || !info.has_revision_number())) {
        sb.addStyleName(Gerrit.RESOURCES.css().relatedChangesGitweb());
        sb.setAttribute("title", gw.getLinkName());
        sb.append('\u25CF');
      } else if (connected != null && !connected.contains(info.commit().commit())) {
        sb.addStyleName(Gerrit.RESOURCES.css().relatedChangesIndirect());
        sb.setAttribute("title", Resources.C.indirectAncestor());
        sb.append('~');
      } else if (info.has_current_revision_number() && info.has_revision_number()
          && info._current_revision_number() != info._revision_number()) {
        sb.addStyleName(Gerrit.RESOURCES.css().relatedChangesNotCurrent());
        sb.setAttribute("title", Util.C.notCurrent());
        sb.append('\u25CF');
      }
      sb.closeSpan();
    }

    private boolean longRunning(int i) {
      return (i % 10) == 0 && System.currentTimeMillis() - start > 50;
    }
  }

  private class NavigationList extends ScrollPanel
      implements ClickHandler, DoubleClickHandler, ScrollHandler {
    List<SafeHtml> rows;
    private final KeyCommandSet keysNavigation;
    private final Element body;
    private final Element surrogate;
    private final Node fragment = createDocumentFragment();

    private HandlerRegistration regNavigation;
    private int selectedRow;
    private int startRow;
    private int rowHeight;
    private int top;
    private int bottom;

    NavigationList() {
      addDomHandler(this, ClickEvent.getType());
      addDomHandler(this, DoubleClickEvent.getType());
      addScrollHandler(this);

      keysNavigation = new KeyCommandSet(Resources.C.relatedChanges());
      keysNavigation.add(
          new KeyCommand(0, 'K', Resources.C.previousChange()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              if (selectedRow > 0) {
                movePointerTo(selectedRow - 1);
              }
            }
          },
          new KeyCommand(0, 'J', Resources.C.nextChange()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              if (rows != null && selectedRow < rows.size()) {
                movePointerTo(selectedRow + 1);
              }
            }
          });
      keysNavigation.add(new KeyCommand(0, 'O', Resources.C.openChange()) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          onOpenRow(selectedRow);
        }
      });

      if (maxHeight > 0) {
        setHeight(maxHeight + "px");
      }
      setStyleName(Gerrit.RESOURCES.css().relatedChangesTable());

      body = DOM.createDiv();
      body.getStyle().setPosition(Style.Position.RELATIVE);
      getContainerElement().appendChild(body);

      surrogate = DOM.createDiv();
      surrogate.getStyle().setVisibility(Visibility.HIDDEN);
    }

    private int ensureRowHeight() {
      if (rowHeight == 0) {
        SafeHtmlBuilder sb = new SafeHtmlBuilder();
        renderRow(sb, 0);
        surrogate.setInnerSafeHtml(sb);

        getContainerElement().appendChild(surrogate);
        rowHeight = surrogate.getOffsetHeight();
        getContainerElement().removeChild(surrogate);
        if (rowHeight == 0) {
          return 12;
        }
        getContainerElement().getStyle()
            .setHeight(rowHeight * rows.size(), Style.Unit.PX);
      }
      return rowHeight;
    }

    public void movePointerTo(int row) {
      int lastSelectedRow = selectedRow;
      selectedRow = row;
      if (rows != null) {
        // Position the selected row in the middle.
        int h = ensureRowHeight();
        int pos = Math.max(h * selectedRow - maxHeight / 2, 0);
        setVerticalScrollPosition(pos);
        render(lastSelectedRow);
        render(selectedRow);
        render();
      }
    }

    private void renderRow(SafeHtmlBuilder sb, int row) {
      sb.openDiv().setAttribute("gerritrow", row);
      if (row == selectedRow) {
        sb.setStyleName(Gerrit.RESOURCES.css().activeRow());
      }
      sb.append(rows.get(row));
      sb.closeDiv();
    }

    private void render(int row) {
      if (startRow <= row && row < startRow + body.getChildCount()) {
        SafeHtmlBuilder sb = new SafeHtmlBuilder();
        renderRow(sb, row);
        surrogate.setInnerSafeHtml(sb);
        body.replaceChild(
            surrogate.getFirstChild(), body.getChild(row - startRow));
      }
    }

    private void render() {
      if (rows == null) {
        return;
      }

      int currChildren = body.getChildCount();
      int vpos = getVerticalScrollPosition();
      if (currChildren > 0 && top <= vpos && vpos <= bottom) {
        return;
      }

      int currStart = startRow;
      int currEnd = startRow + currChildren;

      int page = maxHeight / ensureRowHeight();
      int start = Math.max(vpos / rowHeight - 5, 0);
      int end = Math.min(vpos / rowHeight + page + 5, rows.size());

      if (end <= currStart) {
        renderRange(start, end, true, true);
      } else if (start < currStart) {
        renderRange(start, currStart, false, true);
      } else if (start >= currEnd) {
        renderRange(start, end, true, false);
      } else if (end > currEnd) {
        renderRange(currEnd, end, false, false);
      }
    }

    private void renderRange(int start, int end, boolean removeAll, boolean insertFirst) {
      if (insertFirst || removeAll) {
        startRow = start;
        top = start * rowHeight;
      }
      if (!insertFirst || removeAll) {
        bottom = end * rowHeight - maxHeight;
      }

      SafeHtmlBuilder sb = new SafeHtmlBuilder();
      for (int i = start; i < end; i++) {
        renderRow(sb, i);
      }

      if (removeAll) {
        body.setInnerSafeHtml(sb);
        body.getStyle().setTop(top, Style.Unit.PX);
      } else {
        surrogate.setInnerSafeHtml(sb);
        for (int cnt = surrogate.getChildCount(); cnt > 0; cnt--) {
          fragment.appendChild(surrogate.getFirstChild());
        }
        if (insertFirst) {
          body.insertFirst(fragment);
          body.getStyle().setTop(top, Style.Unit.PX);
        } else {
          body.appendChild(fragment);
        }
      }
    }

    @Override
    public void onClick(ClickEvent event) {
      Integer row = getRow(event.getNativeEvent().<Element>cast());
      if (row != null) {
        movePointerTo(row);
        event.stopPropagation();
      }
    }

    @Override
    public void onDoubleClick(DoubleClickEvent event) {
      Integer row = getRow(event.getNativeEvent().<Element>cast());
      if (row != null) {
        movePointerTo(row);
        onOpenRow(row);
        event.stopPropagation();
      }
    }

    @Override
    public void onScroll(ScrollEvent event) {
      render();
    }

    private Integer getRow(Element e) {
      while (e != null) {
        Element next = DOM.getParent(e);
        if (body == next) {
          try {
            return Integer.parseInt(e.getAttribute("gerritrow"));
          } catch (NumberFormatException ex) {
            return null;
          }
        }
        e = next;
      }
      return null;
    }

    private void onOpenRow(int row) {
      if (!(rows != null && 0 <= row && row < rows.size())) {
        return;
      }

      surrogate.setInnerSafeHtml(rows.get(row));
      getContainerElement().appendChild(surrogate);
      NodeList<com.google.gwt.dom.client.Element> nodes =
          surrogate.getElementsByTagName("a");
      for (int i = 0; i < nodes.getLength(); i++) {
        AnchorElement anchor = nodes.getItem(i).cast();
        String url = anchor.getHref();
        if (url == null || url.isEmpty()) {
          continue;
        }
        if (url.startsWith("#")) {
          Gerrit.display(url.substring(1));
        } else {
          Window.Location.assign(url);
        }
      }
      getContainerElement().removeChild(surrogate);
    }

    @Override
    protected void onLoad() {
      super.onLoad();
      setRegisterKeys(registerKeys);
    }

    @Override
    protected void onUnload() {
      if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }
      super.onUnload();
    }

    public void setRegisterKeys(boolean on) {
      if (on && isAttached()) {
        if (regNavigation == null) {
          regNavigation = GlobalKey.add(this, keysNavigation);
        }
      } else if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }
    }
  }

  private static final native Node createDocumentFragment() /*-{
    return $doc.createDocumentFragment();
  }-*/;
}
