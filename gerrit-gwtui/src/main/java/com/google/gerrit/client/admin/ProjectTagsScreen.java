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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.access.AccessMap;
import com.google.gerrit.client.access.ProjectAccessInfo;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.projects.TagInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.PagingHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectTagsScreen extends PaginatedProjectScreen {
  private Hyperlink prev;
  private Hyperlink next;
  private TagsTable tagTable;
  private Button delTag;
  private Button addTag;
  private HintTextBox nameTxtBox;
  private HintTextBox irevTxtBox;
  private FlowPanel addPanel;
  private NpTextBox filterTxt;
  private Query query;

  public ProjectTagsScreen(Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  public String getScreenToken() {
    return PageLinks.toProjectTags(getProjectKey());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    addPanel.setVisible(false);
    AccessMap.get(
        getProjectKey(),
        new GerritCallback<ProjectAccessInfo>() {
          @Override
          public void onSuccess(ProjectAccessInfo result) {
            addPanel.setVisible(result.canAddRefs());
          }
        });
    query = new Query(match).start(start).run();
    savedPanel = TAGS;
  }

  private void updateForm() {
    tagTable.updateDeleteButton();
    addTag.setEnabled(true);
    nameTxtBox.setEnabled(true);
    irevTxtBox.setEnabled(true);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initPageHeader();

    prev = PagingHyperlink.createPrev();
    prev.setVisible(false);

    next = PagingHyperlink.createNext();
    next.setVisible(false);

    addPanel = new FlowPanel();

    Grid addGrid = new Grid(2, 2);
    addGrid.setStyleName(Gerrit.RESOURCES.css().addBranch());
    int texBoxLength = 50;

    nameTxtBox = new HintTextBox();
    nameTxtBox.setVisibleLength(texBoxLength);
    nameTxtBox.setHintText(AdminConstants.I.defaultTagName());
    nameTxtBox.addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              doAddNewTag();
            }
          }
        });
    addGrid.setText(0, 0, AdminConstants.I.columnTagName() + ":");
    addGrid.setWidget(0, 1, nameTxtBox);

    irevTxtBox = new HintTextBox();
    irevTxtBox.setVisibleLength(texBoxLength);
    irevTxtBox.setHintText(AdminConstants.I.defaultRevisionSpec());
    irevTxtBox.addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              doAddNewTag();
            }
          }
        });
    addGrid.setText(1, 0, AdminConstants.I.revision() + ":");
    addGrid.setWidget(1, 1, irevTxtBox);

    addTag = new Button(AdminConstants.I.buttonAddTag());
    addTag.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doAddNewTag();
          }
        });
    addPanel.add(addGrid);
    addPanel.add(addTag);

    tagTable = new TagsTable();

    delTag = new Button(AdminConstants.I.buttonDeleteTag());
    delTag.setStyleName(Gerrit.RESOURCES.css().branchTableDeleteButton());
    delTag.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            tagTable.deleteChecked();
          }
        });

    HorizontalPanel buttons = new HorizontalPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().branchTablePrevNextLinks());
    buttons.add(delTag);
    buttons.add(prev);
    buttons.add(next);
    add(tagTable);
    add(buttons);
    add(addPanel);
  }

  private void initPageHeader() {
    parseToken();
    HorizontalPanel hp = new HorizontalPanel();
    hp.setStyleName(Gerrit.RESOURCES.css().projectFilterPanel());
    Label filterLabel = new Label(AdminConstants.I.projectFilter());
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
            } else {
              if (query == null) {
                q.run();
              }
              query = q;
            }
          }
        });
    hp.add(filterTxt);
    add(hp);
  }

  private void doAddNewTag() {
    String tagName = nameTxtBox.getText().trim();
    if (tagName.isEmpty()) {
      nameTxtBox.setFocus(true);
      return;
    }

    String rev = irevTxtBox.getText().trim();
    if (rev.isEmpty()) {
      irevTxtBox.setText("HEAD");
      Scheduler.get()
          .scheduleDeferred(
              new ScheduledCommand() {
                @Override
                public void execute() {
                  irevTxtBox.selectAll();
                  irevTxtBox.setFocus(true);
                }
              });
      return;
    }

    addTag.setEnabled(false);
    ProjectApi.createTag(
        getProjectKey(),
        tagName,
        rev,
        new GerritCallback<TagInfo>() {
          @Override
          public void onSuccess(TagInfo tag) {
            showAddedTag(tag);
            nameTxtBox.setText("");
            irevTxtBox.setText("");
            query = new Query(match).start(start).run();
          }

          @Override
          public void onFailure(Throwable caught) {
            addTag.setEnabled(true);
            selectAllAndFocus(nameTxtBox);
            new ErrorDialog(caught.getMessage()).center();
          }
        });
  }

  void showAddedTag(TagInfo tag) {
    SafeHtmlBuilder b = new SafeHtmlBuilder();
    b.openElement("b");
    b.append(Gerrit.C.tagCreationConfirmationMessage());
    b.closeElement("b");

    b.openElement("p");
    b.append(tag.ref());
    b.closeElement("p");

    ConfirmationDialog confirmationDialog =
        new ConfirmationDialog(
            Gerrit.C.tagCreationDialogTitle(),
            b.toSafeHtml(),
            new ConfirmationCallback() {
              @Override
              public void onOk() {
                // do nothing
              }
            });
    confirmationDialog.center();
    confirmationDialog.setCancelVisible(false);
  }

  private static void selectAllAndFocus(TextBox textBox) {
    textBox.selectAll();
    textBox.setFocus(true);
  }

  private class TagsTable extends NavigationTable<TagInfo> {
    private ValueChangeHandler<Boolean> updateDeleteHandler;
    boolean canDelete;

    TagsTable() {
      table.setWidth("");
      table.setText(0, 2, AdminConstants.I.columnTagName());
      table.setText(0, 3, AdminConstants.I.columnTagRevision());

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());

      updateDeleteHandler =
          new ValueChangeHandler<Boolean>() {
            @Override
            public void onValueChange(ValueChangeEvent<Boolean> event) {
              updateDeleteButton();
            }
          };
    }

    Set<String> getCheckedRefs() {
      Set<String> refs = new HashSet<>();
      for (int row = 1; row < table.getRowCount(); row++) {
        TagInfo k = getRowItem(row);
        if (k != null
            && table.getWidget(row, 1) instanceof CheckBox
            && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          refs.add(k.ref());
        }
      }
      return refs;
    }

    void setChecked(Set<String> refs) {
      for (int row = 1; row < table.getRowCount(); row++) {
        TagInfo k = getRowItem(row);
        if (k != null && refs.contains(k.ref()) && table.getWidget(row, 1) instanceof CheckBox) {
          ((CheckBox) table.getWidget(row, 1)).setValue(true);
        }
      }
    }

    void deleteChecked() {
      final Set<String> refs = getCheckedRefs();

      SafeHtmlBuilder b = new SafeHtmlBuilder();
      b.openElement("b");
      b.append(Gerrit.C.tagDeletionConfirmationMessage());
      b.closeElement("b");

      b.openElement("p");
      boolean first = true;
      for (String ref : refs) {
        if (!first) {
          b.append(",").br();
        }
        b.append(ref);
        first = false;
      }
      b.closeElement("p");

      if (refs.isEmpty()) {
        updateDeleteButton();
        return;
      }

      delTag.setEnabled(false);
      ConfirmationDialog confirmationDialog =
          new ConfirmationDialog(
              Gerrit.C.tagDeletionDialogTitle(),
              b.toSafeHtml(),
              new ConfirmationCallback() {
                @Override
                public void onOk() {
                  deleteTags(refs);
                }

                @Override
                public void onCancel() {
                  tagTable.updateDeleteButton();
                }
              });
      confirmationDialog.center();
    }

    private void deleteTags(Set<String> tags) {
      ProjectApi.deleteTags(
          getProjectKey(),
          tags,
          new GerritCallback<VoidResult>() {
            @Override
            public void onSuccess(VoidResult result) {
              query = new Query(match).start(start).run();
            }

            @Override
            public void onFailure(Throwable caught) {
              query = new Query(match).start(start).run();
              super.onFailure(caught);
            }
          });
    }

    void display(List<TagInfo> tags) {
      displaySubset(tags, 0, tags.size());
    }

    void displaySubset(List<TagInfo> tags, int fromIndex, int toIndex) {
      canDelete = false;

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
      if (k.canDelete()) {
        CheckBox sel = new CheckBox();
        sel.addValueChangeHandler(updateDeleteHandler);
        table.setWidget(row, 1, sel);
        canDelete = true;
      } else {
        table.setText(row, 1, "");
      }

      table.setWidget(row, 2, new InlineHTML(highlight(k.getShortName(), match)));

      if (k.revision() != null) {
        table.setText(row, 3, k.revision());
      } else {
        table.setText(row, 3, "");
      }

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      String iconCellStyle = Gerrit.RESOURCES.css().iconCell();
      String dataCellStyle = Gerrit.RESOURCES.css().dataCell();
      fmt.addStyleName(row, 1, iconCellStyle);
      fmt.addStyleName(row, 2, dataCellStyle);
      fmt.addStyleName(row, 3, dataCellStyle);

      setRowItem(row, k);
    }

    boolean hasTagCanDelete() {
      return canDelete;
    }

    void updateDeleteButton() {
      boolean on = false;
      for (int row = 1; row < table.getRowCount(); row++) {
        Widget w = table.getWidget(row, 1);
        if (w != null && w instanceof CheckBox) {
          CheckBox sel = (CheckBox) w;
          if (sel.getValue()) {
            on = true;
            break;
          }
        }
      }
      delTag.setEnabled(on);
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
        tagTable.display(Natives.asList(result));
        next.setVisible(false);
      } else {
        tagTable.displaySubset(Natives.asList(result), 0, result.length() - 1);
        setupNavigationLink(next, qMatch, qStart + pageSize);
      }
      if (qStart > 0) {
        setupNavigationLink(prev, qMatch, qStart - pageSize);
      } else {
        prev.setVisible(false);
      }

      delTag.setVisible(tagTable.hasTagCanDelete());
      Set<String> checkedRefs = tagTable.getCheckedRefs();
      tagTable.setChecked(checkedRefs);
      updateForm();

      if (!isCurrentView()) {
        display();
      }
    }
  }
}
