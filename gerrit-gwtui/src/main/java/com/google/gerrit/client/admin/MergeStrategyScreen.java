// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.data.ProjectMergeStrategies;
import com.google.gerrit.common.data.MergeStrategySection.SubmitType;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

public class MergeStrategyScreen extends ProjectScreen {
  private VerticalPanel parentPanel;
  private Hyperlink parentName;

  private MergeStrategiesTable mergeStrategies;
  private Button addMergeStrategy;
  private Button editMergeStrategy;
  private Button cancelEditing;
  private Button cancelEditing2;
  private ListBox submitTypeBox;
  private NpTextBox refPatternTxt;
  private NpTextArea commitMessage;
  private Button save;
  private FlowPanel addPanel;
  private VerticalPanel commitMessagePanel;
  private CheckBox useContentMerge;

  public MergeStrategyScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initEdit();
    initParent();
    initStrategies();
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    Util.PROJECT_SVC.projectMergeStrategies(getProjectKey(),
        new ScreenLoadCallback<ProjectMergeStrategies>(this) {
          public void preDisplay(final ProjectMergeStrategies result) {
            enableForm(true);
            display(result);
          }
        });
  }

  private void initParent() {
    parentPanel = new VerticalPanel();
    parentPanel.setStyleName(Gerrit.RESOURCES.css().editMergeStrategiesPanel());
    parentPanel.add(new SmallHeading(Util.C.headingRmsParentProjectName()));

    parentName = new Hyperlink("", "");
    parentPanel.add(parentName);
    add(parentPanel);
  }

  private void enableForm(final boolean on) {
    final boolean canAdd = on && submitTypeBox.getItemCount() > 0;
    addMergeStrategy.setEnabled(canAdd);
    refPatternTxt.setEnabled(canAdd);
    commitMessage.setEnabled(canAdd);
    save.setEnabled(canAdd);
  }

  private void initEdit() {
    editMergeStrategy = new Button(Util.C.buttonEditMergeStrategy());
    editMergeStrategy.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        editMergeStrategy.setEnabled(false);
        useContentMerge.setEnabled(false);
        cancelEditing.setVisible(true);
        addPanel.setVisible(true);
        commitMessagePanel.setVisible(true);
        save.setVisible(true);
        cancelEditing2.setVisible(true);
        mergeStrategies.showDeleteColumn(true);
      }
    });

    cancelEditing =
        new Button(Util.C.buttonCancelEditMergeStrategy());
    cancelEditing.setVisible(false);
    cancelEditing.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        Gerrit.display(PageLinks.toProjectMergeStrategies(getProjectKey()));
      }
    });

    final HorizontalPanel editPanel = new HorizontalPanel();
    editPanel.setSpacing(3);
    editPanel.add(editMergeStrategy);
    editPanel.add(cancelEditing);

    add(editPanel);
  }

  private void initStrategies() {
    addPanel = new FlowPanel();
    addPanel.setVisible(false);
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    addPanel.setWidth("98%");

    final Grid addGrid = new Grid(3, 2);
    addGrid.setText(0, 0, Util.C.columnRefName() + ":");

    refPatternTxt = new NpTextBox();
    refPatternTxt.setVisibleLength(30);
    refPatternTxt.setText("");
    refPatternTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doAddNewStrategy();
        }
      }
    });
    refPatternTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        addMergeStrategy.setEnabled(!refPatternTxt.getText().isEmpty());
      }
    });
    addGrid.setWidget(0, 1, refPatternTxt);

    submitTypeBox = new ListBox();
    for (final MergeStrategySection.SubmitType type : MergeStrategySection.SubmitType
        .values()) {
      submitTypeBox.addItem(Util.toLongString(type), type.name());
    }
    submitTypeBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        setEnabledForUseContentMerge();
      }
    });

    addGrid.setText(1, 0, Util.C.columnSubmitAction() + ":");

    addMergeStrategy = new Button(Util.C.buttonAddMergeStrategy());
    addMergeStrategy.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewStrategy();
        mergeStrategies.setVisible(true);
      }
    });

    useContentMerge = new CheckBox(Util.C.useContentMerge(), true);

    final Grid auxGrid = new Grid(1, 2);
    auxGrid.setWidget(0, 0, submitTypeBox);
    auxGrid.setWidget(0, 1, addMergeStrategy);

    addGrid.setWidget(1, 1, auxGrid);
    addGrid.setWidget(2, 1, useContentMerge);
    addPanel.add(addGrid);

    mergeStrategies = new MergeStrategiesTable();
    mergeStrategies.setVisible(false);

    final SmallHeading heading =
        new SmallHeading(Util.C.headingCommitMessage());
    heading.removeStyleName(heading.getStyleName());

    commitMessagePanel = new VerticalPanel();
    commitMessagePanel.setVisible(false);
    commitMessagePanel
        .setStyleName(Gerrit.RESOURCES.css().commitMessagePanel());
    commitMessagePanel.add(heading);

    commitMessage = new NpTextArea();
    commitMessage.setVisibleLines(6);
    commitMessage.setCharacterWidth(60);
    commitMessagePanel.add(commitMessage);

    save = new Button(Util.C.buttonSaveChanges());
    save.setVisible(false);
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSave();
      }
    });

    cancelEditing2 = new Button(Util.C.buttonCancelEditMergeStrategy());
    cancelEditing2.setVisible(false);
    cancelEditing2.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        Gerrit.display(PageLinks.toProjectMergeStrategies(getProjectKey()));
      }
    });

    final HorizontalPanel editPanel = new HorizontalPanel();
    editPanel.setSpacing(3);
    editPanel.add(save);
    editPanel.add(cancelEditing2);

    add(editPanel);

    add(mergeStrategies);
    add(addPanel);
    add(commitMessagePanel);
    add(editPanel);

    if (submitTypeBox.getItemCount() > 0) {
      submitTypeBox.setSelectedIndex(0);
    }
  }

  /**
   * Enables the {@link #useContentMerge} checkbox if the selected submit type
   * allows the usage of content merge.
   * If the submit type (currently only 'Fast Forward Only') does not allow
   * content merge the useContentMerge checkbox gets disabled.
   */
  private void setEnabledForUseContentMerge() {
    if (SubmitType.FAST_FORWARD_ONLY.equals(SubmitType
        .valueOf(submitTypeBox.getValue(submitTypeBox.getSelectedIndex())))) {
      useContentMerge.setEnabled(false);
      useContentMerge.setValue(false);
    } else {
      useContentMerge.setEnabled(submitTypeBox.isEnabled());
    }
  }

  private void doSave() {
    enableForm(false);
    mergeStrategies.showDeleteColumn(false);

    String message = commitMessage.getText().trim();
    if ("".equals(message)) {
      message = null;
    }

    Util.PROJECT_SVC.changeMergeStrategies(getProjectKey(), mergeStrategies
        .getRevision(), message, mergeStrategies.getStrategies(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toProjectMergeStrategies(getProjectKey()));
          }
          @Override
          public void onFailure(final Throwable caught) {
            enableForm(true);
            mergeStrategies.showDeleteColumn(true);
            save.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void doAddNewStrategy() {
    final MergeStrategySection.SubmitType submitType;

    if (submitTypeBox.getSelectedIndex() >= 0) {
      submitType =
          MergeStrategySection.SubmitType.valueOf(submitTypeBox
              .getValue(submitTypeBox.getSelectedIndex()));
    } else {
      return;
    }

    final String refPattern = refPatternTxt.getText().trim();
    if (!refPattern.isEmpty()) {
      final MergeStrategySection mergeStrategySection =
          new MergeStrategySection(refPattern);
      mergeStrategySection.setSubmitType(submitType);
      mergeStrategySection.setUseContentMerge(useContentMerge.getValue());
      if (!mergeStrategies.addNew(mergeStrategySection)) {
        new ErrorDialog(Util.C.errorPatternHasStrategy());
      } else {
        refPatternTxt.setText("");
        submitTypeBox.setSelectedIndex(0);
        useContentMerge.setValue(false);
        useContentMerge.setEnabled(false);
      }
    }
  }

  void display(final ProjectMergeStrategies result) {
    final Project.NameKey wildKey = Gerrit.getConfig().getWildProject();
    Project.NameKey parent = result.getInheritsFrom();
    final boolean isWild = parent == null;
    if (parent == null) {
      parent = wildKey;
    }

    parentPanel.setVisible(!isWild);
    parentName.setTargetHistoryToken(Dispatcher.toProjectAdmin(parent,
        ProjectScreen.MERGE_STRATEGY));
    parentName.setText(parent.get());

    mergeStrategies.setRevision(result.getRevision());

    if (!result.getMergeStrategies().isEmpty()) {
      mergeStrategies.setVisible(true);
      mergeStrategies.display(result.getMergeStrategies());
    }
  }
}
