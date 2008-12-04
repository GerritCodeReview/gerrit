// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.SignedInListener;
import com.google.gerrit.client.account.AccountDashboardLink;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusListener;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HasFocus;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

public class ChangeTable extends Composite implements HasFocus {
  private static final String MY_STYLE = "gerrit-ChangeTable";
  private static final String S = MY_STYLE + "-";
  private static final String S_ICON_HEADER = S + "IconHeader";
  private static final String S_DATA_HEADER = S + "DataHeader";
  private static final String S_SECTION_HEADER = S + "SectionHeader";
  private static final String S_EMPTY_SECTION = S + "EmptySection";
  private static final String S_ICON_CELL = S + "IconCell";
  private static final String S_C_ID = S + "C_ID";
  private static final String S_DATA_CELL = S + "DataCell";

  private static final int C_ARROW = 0;
  private static final int C_STAR = 1;
  private static final int C_ID = 2;
  private static final int C_SUBJECT = 3;
  private static final int C_OWNER = 4;
  private static final int C_REVIEWERS = 5;
  private static final int C_PROJECT = 6;
  private static final int C_LAST_UPDATE = 7;
  private static final int COLUMNS = 8;

  private static final LinkedHashMap<String, Change.Id> savedPositions =
      new LinkedHashMap<String, Change.Id>(10, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, Change.Id> eldest) {
          return size() >= 5;
        }
      };

  private final List<Section> sections;
  private final FlexTable table;
  private final FocusPanel focusy;
  private final Image pointer;
  private final SignedInListener signedInListener;
  private String saveId;
  private int currentRow = -1;

  public ChangeTable() {
    sections = new ArrayList<Section>();
    pointer = Gerrit.ICONS.arrowRight().createImage();
    table = new FlexTable();
    table.addStyleName(MY_STYLE);
    focusy = new FocusPanel(table);
    focusy.addKeyboardListener(new KeyboardListenerAdapter() {
      @Override
      public void onKeyPress(Widget sender, char keyCode, int modifiers) {
        boolean consume = false;
        if (modifiers == 0) {
          switch (keyCode) {
            case 'k':
            case KeyboardListener.KEY_UP:
              consume = true;
              onUp();
              break;

            case 'j':
            case KeyboardListener.KEY_DOWN:
              consume = true;
              onDown();
              break;

            case 'o':
            case KeyboardListener.KEY_ENTER:
              consume = true;
              onEnter();
              break;

            case 's':
              if (currentRow >= 0) {
                consume = true;
                onStarClick(currentRow);
              }
              break;
          }
        }
        if (consume) {
          final Event event = DOM.eventGetCurrentEvent();
          DOM.eventCancelBubble(event, true);
          DOM.eventPreventDefault(event);
        }
      }
    });
    focusy.addFocusListener(new FocusListener() {
      public void onFocus(final Widget sender) {
        if (currentRow < 0) {
          onDown();
        }
      }

      public void onLostFocus(final Widget sender) {
      }
    });
    initWidget(focusy);

    table.setText(0, C_ARROW, "");
    table.setText(0, C_STAR, "");
    table.setText(0, C_ID, Util.C.changeTableColumnID());
    table.setText(0, C_SUBJECT, Util.C.changeTableColumnSubject());
    table.setText(0, C_OWNER, Util.C.changeTableColumnOwner());
    table.setText(0, C_REVIEWERS, Util.C.changeTableColumnReviewers());
    table.setText(0, C_PROJECT, Util.C.changeTableColumnProject());
    table.setText(0, C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_ID, S_C_ID);
    for (int i = 0; i < C_ID; i++) {
      fmt.addStyleName(0, i, S_ICON_HEADER);
    }
    for (int i = C_ID; i < COLUMNS; i++) {
      fmt.addStyleName(0, i, S_DATA_HEADER);
    }

    table.addTableListener(new TableListener() {
      public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
        if (cell == C_STAR) {
          onStarClick(row);
        }
      }
    });

    signedInListener = new SignedInListener() {
      public void onSignIn() {
        if (table.getRowCount() <= sections.size()) {
          // There are no data rows in this table, so star status is
          // simply not relevant to the caller.
          //
          return;
        }

        Util.LIST_SVC.myStarredChangeIds(new GerritCallback<Set<Change.Id>>() {
          public void onSuccess(final Set<Change.Id> result) {
            final FlexCellFormatter fmt = table.getFlexCellFormatter();
            final int max = table.getRowCount();
            for (int row = 0; row < max; row++) {
              final ChangeInfo c = getChangeInfo(row);
              if (c != null) {
                c.setStarred(result.contains(c.getId()));
                setStar(row, c);
              }
            }
          }
        });
      }

      public void onSignOut() {
        final FlexCellFormatter fmt = table.getFlexCellFormatter();
        final int max = table.getRowCount();
        for (int row = 0; row < max; row++) {
          if (getChangeInfo(row) != null) {
            table.clearCell(row, C_STAR);
          }
        }
      }
    };
  }

  private void setChangeInfo(final int row, final ChangeInfo c) {
    setChangeInfo(table.getCellFormatter().getElement(row, C_ARROW), c);
  }

  protected ChangeInfo getChangeInfo(final int row) {
    return getChangeInfo(table.getCellFormatter().getElement(row, C_ARROW));
  }

  protected void onStarClick(final int row) {
    final ChangeInfo c = getChangeInfo(row);
    if (c != null && Gerrit.isSignedIn()) {
      final boolean prior = c.isStarred();
      c.setStarred(!prior);
      setStar(row, c);

      final ToggleStarRequest req = new ToggleStarRequest();
      req.toggle(c.getId(), c.isStarred());
      Util.LIST_SVC.toggleStars(req, new GerritCallback<VoidResult>() {
        public void onSuccess(final VoidResult result) {
        }

        @Override
        public void onFailure(final Throwable caught) {
          super.onFailure(caught);
          c.setStarred(prior);
          setStar(row, c);
        }
      });
    }
  }

  protected void onUp() {
    for (int row = currentRow - 1; row >= 0; row--) {
      if (getChangeInfo(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  protected void onDown() {
    final int max = table.getRowCount();
    for (int row = currentRow + 1; row < max; row++) {
      if (getChangeInfo(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  protected void onEnter() {
    if (currentRow >= 0) {
      final ChangeInfo c = getChangeInfo(currentRow);
      if (c != null) {
        History.newItem(Link.toChange(c), false);
        Gerrit.display(new ChangeScreen(c));
      }
    }
  }

  protected void movePointerTo(final int newRow) {
    if (newRow >= 0) {
      table.setWidget(newRow, C_ARROW, pointer);
      table.getCellFormatter().getElement(newRow, C_ARROW).scrollIntoView();
    } else if (currentRow >= 0) {
      table.setWidget(currentRow, C_ARROW, null);
    }
    currentRow = newRow;
  }

  public void finishDisplay() {
    if (saveId != null) {
      final Change.Id oldId = savedPositions.get(saveId);
      if (oldId != null) {
        final int max = table.getRowCount();
        for (int row = 0; row < max; row++) {
          final ChangeInfo c = getChangeInfo(row);
          if (c != null && oldId.equals(c.getId())) {
            movePointerTo(row);
            break;
          }
        }
      }
    }

    if (currentRow < 0) {
      onDown();
    }

    if (currentRow >= 0) {
      DeferredCommand.addCommand(new Command() {
        public void execute() {
          setFocus(true);
        }
      });
    }
  }

  public void setSavePointerId(final String id) {
    saveId = id;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Gerrit.addSignedInListener(signedInListener);
  }

  @Override
  public void onUnload() {
    if (saveId != null && currentRow >= 0) {
      final ChangeInfo c = getChangeInfo(currentRow);
      if (c != null) {
        savedPositions.put(saveId, c.getId());
      }
    }
    Gerrit.removeSignedInListener(signedInListener);
    super.onUnload();
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    table.setText(row, 0, Util.C.changeTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, COLUMNS);
    fmt.setStyleName(row, 0, S_EMPTY_SECTION);
  }

  private void insertChangeRow(final int row) {
    insertRow(row);
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, C_ARROW, S_ICON_CELL);
    fmt.addStyleName(row, C_STAR, S_ICON_CELL);
    for (int i = C_ID; i < COLUMNS; i++) {
      fmt.addStyleName(row, i, S_DATA_CELL);
    }
    fmt.addStyleName(row, C_ID, S_C_ID);
  }

  private void populateChangeRow(final int row, final ChangeInfo c) {
    final String idstr = String.valueOf(c.getId().get());
    table.setWidget(row, C_ARROW, null);
    if (Gerrit.isSignedIn()) {
      setStar(row, c);
    }
    table.setWidget(row, C_ID, new ChangeLink(idstr, c));

    String s = c.getSubject();
    if (c.getStatus() != null && c.getStatus() != Change.Status.NEW) {
      s += " (" + c.getStatus().name() + ")";
    }
    table.setWidget(row, C_SUBJECT, new ChangeLink(s, c));
    table.setWidget(row, C_OWNER, new AccountDashboardLink(c.getOwner()));
    table.setText(row, C_REVIEWERS, "TODO");
    table.setText(row, C_PROJECT, c.getProject().getName());
    table.setText(row, C_LAST_UPDATE, "TODO");
    setChangeInfo(row, c);
  }

  private void setStar(final int row, final ChangeInfo c) {
    final AbstractImagePrototype star;
    if (c.isStarred()) {
      star = Gerrit.ICONS.starFilled();
    } else {
      star = Gerrit.ICONS.starOpen();
    }

    final Widget i = table.getWidget(row, C_STAR);
    if (i instanceof Image) {
      star.applyTo((Image) i);
    } else {
      table.setWidget(row, C_STAR, star.createImage());
    }
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    if (s.titleText != null) {
      s.titleRow = table.getRowCount();
      table.setText(s.titleRow, 0, s.titleText);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, COLUMNS);
      fmt.addStyleName(s.titleRow, 0, S_SECTION_HEADER);
    } else {
      s.titleRow = -1;
    }

    s.parent = this;
    s.dataBegin = table.getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
  }

  private int insertRow(final int beforeRow) {
    for (final Section s : sections) {
      boolean dirty = false;
      if (beforeRow <= s.titleRow) {
        s.titleRow++;
      }
      if (beforeRow < s.dataBegin) {
        s.dataBegin++;
      }
    }
    return table.insertRow(beforeRow);
  }

  private void removeRow(final int row) {
    for (final Section s : sections) {
      if (row < s.titleRow) {
        s.titleRow--;
      }
      if (row < s.dataBegin) {
        s.dataBegin--;
      }
    }
    table.removeRow(row);
  }

  public int getTabIndex() {
    return focusy.getTabIndex();
  }

  public void setAccessKey(char key) {
    focusy.setAccessKey(key);
  }

  public void setFocus(boolean focused) {
    focusy.setFocus(focused);
  }

  public void setTabIndex(int index) {
    focusy.setTabIndex(index);
  }

  public void addFocusListener(FocusListener listener) {
    focusy.addFocusListener(listener);
  }

  public void addKeyboardListener(KeyboardListener listener) {
    focusy.addKeyboardListener(listener);
  }

  public void removeFocusListener(FocusListener listener) {
    focusy.removeFocusListener(listener);
  }

  public void removeKeyboardListener(KeyboardListener listener) {
    focusy.removeKeyboardListener(listener);
  }

  public static class Section {
    String titleText;

    ChangeTable parent;
    int titleRow = -1;
    int dataBegin;
    int rows;

    public Section() {
      this(null);
    }

    public Section(final String titleText) {
      setTitleText(titleText);
    }

    public void setTitleText(final String text) {
      titleText = text;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void display(final List<ChangeInfo> changeList) {
      final int sz = changeList != null ? changeList.size() : 0;
      final boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          parent.removeRow(dataBegin);
          rows--;
        }
      }

      if (sz == 0) {
        if (hadData) {
          parent.insertNoneRow(dataBegin);
        }
      } else {
        if (!hadData) {
          parent.removeRow(dataBegin);
        }

        while (rows < sz) {
          parent.insertChangeRow(dataBegin + rows);
          rows++;
        }
        for (int i = 0; i < sz; i++) {
          parent.populateChangeRow(dataBegin + i, changeList.get(i));
        }
      }
    }
  }

  private static final native void setChangeInfo(Element td, ChangeInfo c)/*-{ td["__gerritChangeInfo"] = c; }-*/;

  private static final native ChangeInfo getChangeInfo(Element td)/*-{ return td["__gerritChangeInfo"]; }-*/;
}
