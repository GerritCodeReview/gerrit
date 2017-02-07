// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import static com.google.gerrit.client.ui.Util.highlight;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.projects.TagInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.PagingHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import java.util.List;

public class ProjectTagsScreen extends PaginatedProjectScreen {
  private NpTextBox filterTxt;
  private Query query;
  private Hyperlink prev;
  private Hyperlink next;
  private TagsTable tagsTable;

  public ProjectTagsScreen(Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  public String getScreenToken() {
    return PageLinks.toProjectTags(getProjectKey());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initPageHeader();
    prev = PagingHyperlink.createPrev();
    prev.setVisible(false);

    next = PagingHyperlink.createNext();
    next.setVisible(false);

    tagsTable = new TagsTable();

    HorizontalPanel buttons = new HorizontalPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().branchTablePrevNextLinks());
    buttons.add(prev);
    buttons.add(next);
    add(tagsTable);
    add(buttons);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    query = new Query(match).start(start).run();
    savedPanel = TAGS;
  }

  private void initPageHeader() {
    parseToken();
    HorizontalPanel hp = new HorizontalPanel();
    hp.setStyleName(Gerrit.RESOURCES.css().projectFilterPanel());
    Label filterLabel = new Label(Util.C.projectFilter());
    filterLabel.setStyleName(Gerrit.RESOURCES.css().projectFilterLabel());
    hp.add(filterLabel);
    filterTxt = new NpTextBox();
    filterTxt.setValue(match);
    filterTxt.addKeyUpHandler(
        new KeyUpHandler() {
          @Override
          public void onKeyUp(KeyUpEvent event) {
            Query q = new Query(filterTxt.getValue());
            if (match.equals(q.qMatch)) {
              q.start(start);
            } else if (query == null) {
              q.run();
              query = q;
            }
          }
        });
    hp.add(filterTxt);
    add(hp);
  }

  private class TagsTable extends NavigationTable<TagInfo> {

    TagsTable() {
      table.setWidth("");
      table.setText(0, 1, Util.C.columnTagName());
      table.setText(0, 2, Util.C.columnBranchRevision());

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    }

    void display(List<TagInfo> tags) {
      displaySubset(tags, 0, tags.size());
    }

    void displaySubset(List<TagInfo> tags, int fromIndex, int toIndex) {
      while (1 < table.getRowCount()) {
        table.removeRow(table.getRowCount() - 1);
      }

      for (TagInfo k : tags.subList(fromIndex, toIndex)) {
        int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(int row, TagInfo k) {
      table.setWidget(row, 1, new InlineHTML(highlight(k.getShortName(), match)));

      if (k.revision() != null) {
        table.setText(row, 2, k.revision());
      } else {
        table.setText(row, 2, "");
      }

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      String dataCellStyle = Gerrit.RESOURCES.css().dataCell();
      fmt.addStyleName(row, 1, dataCellStyle);
      fmt.addStyleName(row, 2, dataCellStyle);

      setRowItem(row, k);
    }

    @Override
    protected void onOpenRow(int row) {
      if (row > 0) {
        movePointerTo(row);
      }
    }

    @Override
    protected Object getRowItemKey(TagInfo item) {
      return item.ref();
    }
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (match != null) {
      filterTxt.setCursorPos(match.length());
    }
    filterTxt.setFocus(true);
  }

  private class Query {
    private String qMatch;
    private int qStart;

    Query(String match) {
      this.qMatch = match;
    }

    Query start(int start) {
      this.qStart = start;
      return this;
    }

    Query run() {
      // Retrieve one more tag than page size to determine if there are more
      // tags to display
      ProjectApi.getTags(
          getProjectKey(),
          pageSize + 1,
          qStart,
          qMatch,
          new ScreenLoadCallback<JsArray<TagInfo>>(ProjectTagsScreen.this) {
            @Override
            public void preDisplay(JsArray<TagInfo> result) {
              if (!isAttached()) {
                // View has been disposed.
              } else if (query == Query.this) {
                query = null;
                showList(result);
              } else {
                query.run();
              }
            }
          });
      return this;
    }

    void showList(JsArray<TagInfo> result) {
      setToken(getTokenForScreen(qMatch, qStart));
      ProjectTagsScreen.this.match = qMatch;
      ProjectTagsScreen.this.start = qStart;

      if (result.length() <= pageSize) {
        tagsTable.display(Natives.asList(result));
        next.setVisible(false);
      } else {
        tagsTable.displaySubset(Natives.asList(result), 0, result.length() - 1);
        setupNavigationLink(next, qMatch, qStart + pageSize);
      }
      if (qStart > 0) {
        setupNavigationLink(prev, qMatch, qStart - pageSize);
      } else {
        prev.setVisible(false);
      }

      if (!isCurrentView()) {
        display();
      }
    }
  }
}
