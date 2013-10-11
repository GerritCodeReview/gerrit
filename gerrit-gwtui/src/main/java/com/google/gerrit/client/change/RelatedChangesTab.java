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
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;
import com.google.gwtexpui.progress.client.ProgressBar;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class RelatedChangesTab {
  private static final String OPEN;
  private static final HyperlinkImpl link = GWT.create(HyperlinkImpl.class);

  static {
    OPEN = DOM.createUniqueId().replace('-', '_');
    init(OPEN);
  }

  private static final native void init(String o) /*-{
    $wnd[o] = $entry(function(e,i) {
      return @com.google.gerrit.client.change.RelatedChangesTab::onOpen(Lcom/google/gwt/dom/client/NativeEvent;I)(e,i);
    });
  }-*/;

  private static boolean onOpen(NativeEvent e, int idx) {
    if (link.handleAsClick(e.<Event> cast())) {
      MyTable t = getMyTable(e);
      if (t != null) {
        t.onOpenRow(idx);
        e.preventDefault();
        return false;
      }
    }
    return true;
  }

  private static MyTable getMyTable(NativeEvent event) {
    com.google.gwt.user.client.Element e = event.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof MyTable) {
        return (MyTable) l;
      }
    }
    return null;
  }

  private final RelatedChanges parent;
  private final int index;
  private final ScrollPanel scroll;
  private final ProgressBar progress;
  private final InlineLabel error;
  private final InlineLabel none;

  private String project;
  private boolean showBranches;
  private MyTable table;
  private boolean register;

  public RelatedChangesTab(RelatedChanges parent, int index, VerticalPanel panel) {
    this.parent = parent;
    this.index = index;

    scroll = new ScrollPanel();
    scroll.setVisible(false);
    panel.add(scroll);

    progress = new ProgressBar();
    panel.add(progress);

    error = new InlineLabel();
    error.setVisible(false);
    panel.add(error);

    none = new InlineLabel(Resources.C.noChanges());
    none.setVisible(false);
    panel.add(none);
  }

  void setTitle(String title) {
    parent.setTabTitle(index, title);
  }

  void setShowBranches(boolean showBranches) {
    this.showBranches = showBranches;
  }

  void setChanges(String project, String revision, JsArray<ChangeAndCommit> changes) {
    this.project = project;
    render(revision, changes);
  }

  void setError(String message) {
    progress.setVisible(false);
    scroll.setVisible(false);
    error.setVisible(true);
    error.setText(message);
  }

  void setMaxHeight(int height) {
    scroll.setHeight(height + "px");
  }

  void registerKeys() {
    register = true;

    if (table != null) {
      table.setRegisterKeys(true);
    }
  }

  private void render(String revision, JsArray<ChangeAndCommit> list) {
    if (0 < list.length()) {
      DisplayCommand cmd = new DisplayCommand(revision, list);
      if (cmd.execute()) {
        Scheduler.get().scheduleIncremental(cmd);
      }
    } else {
      progress.setVisible(false);
      none.setVisible(true);
    }
  }

  private void setTable(MyTable t) {
    progress.setVisible(false);

    scroll.clear();
    scroll.add(t);
    scroll.setVisible(true);
    table = t;

    if (register) {
      table.setRegisterKeys(true);
    }
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

  private class MyTable extends NavigationTable<ChangeAndCommit> {
    private final JsArray<ChangeAndCommit> list;

    MyTable(JsArray<ChangeAndCommit> list) {
      this.list = list;
      table.setWidth("");

      keysNavigation.setName(Gerrit.C.sectionNavigation());
      keysNavigation.add(new PrevKeyCommand(0, 'K',
          Resources.C.previousChange()));
      keysNavigation.add(new NextKeyCommand(0, 'J', Resources.C.nextChange()));
      keysNavigation.add(new OpenKeyCommand(0, 'O', Resources.C.openChange()));
    }

    @Override
    protected Object getRowItemKey(ChangeAndCommit item) {
      return item.id();
    }

    @Override
    protected ChangeAndCommit getRowItem(int row) {
      if (0 <= row && row <= list.length()) {
        return list.get(row);
      }
      return null;
    }

    @Override
    protected void onOpenRow(int row) {
      if (0 <= row && row <= list.length()) {
        ChangeAndCommit c = list.get(row);
        String url = url(c);
        if (url != null && url.startsWith("#")) {
          Gerrit.display(url.substring(1));
        } else if (url != null) {
          Window.Location.assign(url);
        }
      }
    }

    void selectRow(int select) {
      movePointerTo(select, true);
    }
  }

  private final class DisplayCommand implements RepeatingCommand {
    private final SafeHtmlBuilder sb = new SafeHtmlBuilder();
    private final MyTable table;
    private final String revision;
    private final JsArray<ChangeAndCommit> list;
    private boolean attached;
    private int row;
    private int select;
    private double start;

    private DisplayCommand(String revision, JsArray<ChangeAndCommit> list) {
      this.table = new MyTable(list);
      this.revision = revision;
      this.list = list;
    }

    public boolean execute() {
      boolean attachedNow = parent.isAttached();
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
      while (row < list.length()) {
        ChangeAndCommit info = list.get(row);
        if (revision.equals(info.commit().commit())) {
          select = row;
        }
        render(sb, row, info);
        if ((++row % 10) == 0 && longRunning()) {
          updateMeter();
          return true;
        }
      }
      table.resetHtml(sb);
      setTable(table);
      table.selectRow(select);
      return false;
    }

    private void render(SafeHtmlBuilder sb, int row, ChangeAndCommit info) {
      sb.openTr();
      sb.openTd().setStyleName(FileTable.R.css().pointer()).closeTd();

      sb.openTd().addStyleName(parent.getStyle().subject());
      String url = url(info);
      if (url != null) {
        sb.openAnchor().setAttribute("href", url);
        if (url.startsWith("#")) {
          sb.setAttribute("onclick", OPEN + "(event," + row + ")");
        }
        sb.append(info.commit().subject()
            + (showBranches ? " (" + info.branch() + ")" : ""));
        sb.closeAnchor();
      } else {
        sb.append(info.commit().subject());
      }
      sb.closeTd();

      sb.closeTr();
    }

    private void updateMeter() {
      progress.setValue((100 * row) / list.length());
    }

    private boolean longRunning() {
      return System.currentTimeMillis() - start > 200;
    }
  }
}
