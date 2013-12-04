// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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
package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommitMessageBlock;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.client.PrettyFactory;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

public abstract class AbstractPatchScreen extends Screen implements
    CommentEditorContainer {
  static final PrettyFactory PRETTY = ClientSideFormatter.FACTORY;

  /**
   * How this patch should be displayed in the patch screen.
   */
  public static enum Type {
    UNIFIED, SIDE_BY_SIDE
  }

  /**
   * What should be displayed in the top of the screen
   */
  public static enum TopView {
    MAIN, COMMIT, PREFERENCES, PATCH_SETS, FILES
  }

  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected HistoryTable historyTable;
  protected FlowPanel topPanel;
  protected FlowPanel contentPanel;
  protected CommitMessageBlock commitMessageBlock;
  protected NavLinks topNav;
  protected NavLinks bottomNav;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable fileList;
  protected Patch.Key patchKey;
  protected PatchScriptSettingsPanel settingsPanel;
  protected ListenableAccountDiffPreference prefs;
  protected TopView topView;
  protected CommentLinkProcessor commentLinkProcessor;
  protected HandlerRegistration prefsHandler;

  public AbstractPatchScreen(final Patch.Key patchKey,
      final PatchSetDetail patchSetDetail, final PatchTable patchTable,
      final TopView top, final PatchSet.Id baseId) {
    this.patchKey = patchKey;
    this.patchSetDetail = patchSetDetail;
    fileList = patchTable;
    topView = top;

    idSideA = baseId; // null here means we're diff'ing from the Base
    idSideB = patchKey.getParentKey();

    prefs = fileList != null ? fileList.getPreferences() :
      new ListenableAccountDiffPreference();
    if (Gerrit.isSignedIn()) {
      prefs.reset();
    }

    settingsPanel = new PatchScriptSettingsPanel(prefs);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    historyTable = new HistoryTable(this);
    commitMessageBlock = new CommitMessageBlock();
    topPanel = new FlowPanel();
    add(topPanel);
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (prefsHandler == null) {
      prefsHandler = prefs.addValueChangeHandler(
          new ValueChangeHandler<AccountDiffPreference>() {
            @Override
            public void onValueChange(ValueChangeEvent<AccountDiffPreference> event) {
              update(event.getValue());
            }
          });
    }
    if (topView != null && prefs.get().isRetainHeader()) {
      setTopView(topView);
    }
  }

  @Override
  protected void onUnload() {
    if (prefsHandler != null) {
      prefsHandler.removeHandler();
      prefsHandler = null;
    }
    super.onUnload();
  }

  protected void createContentPanel() {
    contentPanel = new FlowPanel();
    if (getPatchScreenType() == PatchScreen.Type.SIDE_BY_SIDE) {
      contentPanel.setStyleName(
          Gerrit.RESOURCES.css().sideBySideScreenSideBySideTable());
    } else {
      contentPanel.setStyleName(Gerrit.RESOURCES.css().unifiedTable());
    }
  }
  protected void createNavs(KeyCommandSet kcs) {
    topNav = new NavLinks(kcs, patchKey.getParentKey());
    bottomNav = new NavLinks(null, patchKey.getParentKey());
  }

  PatchSet.Id getSideA() {
    return idSideA;
  }

  public Patch.Key getPatchKey() {
    return patchKey;
  }

  abstract public int getPatchIndex();
  abstract void refresh(boolean first);
  abstract public AbstractPatchScreen.Type getPatchScreenType();
  abstract protected void update(AccountDiffPreference dp);

  public PatchSetDetail getPatchSetDetail() {
    return patchSetDetail;
  }

  public PatchTable getFileList() {
    return fileList;
  }

  public TopView getTopView() {
    return topView;
  }

  public void setTopView(TopView tv) {
    topView = tv;
    topPanel.clear();
    switch (tv) {
      case COMMIT:      topPanel.add(commitMessageBlock);
        break;
      case PREFERENCES: topPanel.add(settingsPanel);
        break;
      case PATCH_SETS:  topPanel.add(historyTable);
        break;
      case FILES:       topPanel.add(fileList);
        break;
      case MAIN:
        break;
    }
  }
}

