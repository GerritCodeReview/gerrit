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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.changes.CommitMessageBlock;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.common.PrettyFactory;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtjsonrpc.client.VoidResult;

public abstract class PatchScreen extends Screen implements
    CommentEditorContainer {
  static final PrettyFactory PRETTY = ClientSideFormatter.FACTORY;

  public static class SideBySide extends PatchScreen {
    public SideBySide(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable,
        final TopView topView, final PatchSet.Id baseId) {
       super(id, patchIndex, patchSetDetail, patchTable, topView, baseId);
    }

    @Override
    protected SideBySideTable createContentTable() {
      return new SideBySideTable();
    }

    @Override
    public PatchScreen.Type getPatchScreenType() {
      return PatchScreen.Type.SIDE_BY_SIDE;
    }
  }

  public static class Unified extends PatchScreen {
    public Unified(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable,
        final TopView topView, final PatchSet.Id baseId) {
      super(id, patchIndex, patchSetDetail, patchTable, topView, baseId);
    }

    @Override
    protected UnifiedDiffTable createContentTable() {
      return new UnifiedDiffTable();
    }

    @Override
    public PatchScreen.Type getPatchScreenType() {
      return PatchScreen.Type.UNIFIED;
    }
  }

  /**
   * What should be displayed in the top of the screen
   */
  public static enum TopView {
    MAIN, COMMIT, PREFERENCES, PATCH_SETS, FILES
  }

  protected final Patch.Key patchKey;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable fileList;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected PatchScriptSettingsPanel settingsPanel;
  protected TopView topView;

  private CheckBox reviewed;
  private HistoryTable historyTable;
  private FlowPanel topPanel;
  private FlowPanel contentPanel;
  private Label noDifference;
  private AbstractPatchContentTable contentTable;
  private CommitMessageBlock commitMessageBlock;
  private NavLinks topNav;
  private NavLinks bottomNav;

  private int rpcSequence;
  private PatchScript lastScript;

  /** The index of the file we are currently looking at among the fileList */
  private int patchIndex;
  private ListenableAccountDiffPreference prefs;

  /** Keys that cause an action on this screen */
  private KeyCommandSet keysNavigation;
  private HandlerRegistration regNavigation;
  private boolean intralineFailure;

  /**
   * How this patch should be displayed in the patch screen.
   */
  public static enum Type {
    UNIFIED, SIDE_BY_SIDE
  }

  protected PatchScreen(final Patch.Key id, final int patchIndex,
      final PatchSetDetail detail, final PatchTable patchTable,
      final TopView top, final PatchSet.Id baseId) {
    patchKey = id;
    patchSetDetail = detail;
    fileList = patchTable;
    topView = top;

    idSideA = baseId; // null here means we're diff'ing from the Base
    idSideB = id.getParentKey();
    this.patchIndex = patchIndex;

    reviewed = new CheckBox(Util.C.reviewed());
    reviewed.addValueChangeHandler(
        new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            setReviewedByCurrentUser(event.getValue());
          }
        });

    prefs = fileList != null ? fileList.getPreferences() :
                               new ListenableAccountDiffPreference();
    if (Gerrit.isSignedIn()) {
      prefs.reset();
    }
    prefs.addValueChangeHandler(
        new ValueChangeHandler<AccountDiffPreference>() {
          @Override
          public void onValueChange(ValueChangeEvent<AccountDiffPreference> event) {
            update(event.getValue());
          }
        });

    settingsPanel = new PatchScriptSettingsPanel(prefs);
  }

  @Override
  public void notifyDraftDelta(int delta) {
    lastScript = null;
  }

  @Override
  public void remove(CommentEditorPanel panel) {
    lastScript = null;
  }

  private void update(AccountDiffPreference dp) {
    // Did the user just turn on auto-review?
    if (!reviewed.getValue() && prefs.getOld().isManualReview()
        && !dp.isManualReview()) {
      reviewed.setValue(true);
      setReviewedByCurrentUser(true);
    }

    if (lastScript != null && canReuse(dp, lastScript)) {
      lastScript.setDiffPrefs(dp);
      RpcStatus.INSTANCE.onRpcStart(null);
      settingsPanel.setEnabled(false);
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          try {
            onResult(lastScript, false /* not the first time */);
          } finally {
            RpcStatus.INSTANCE.onRpcComplete(null);
          }
        }
      });
    } else {
      refresh(false);
    }
  }

  private boolean canReuse(AccountDiffPreference dp, PatchScript last) {
    if (last.getDiffPrefs().getIgnoreWhitespace() != dp.getIgnoreWhitespace()) {
      // Whitespace ignore setting requires server computation.
      return false;
    }

    final int ctx = dp.getContext();
    if (ctx == AccountDiffPreference.WHOLE_FILE_CONTEXT && !last.getA().isWholeFile()) {
      // We don't have the entire file here, so we can't render it.
      return false;
    }

    if (last.getDiffPrefs().getContext() < ctx && !last.getA().isWholeFile()) {
      // We don't have sufficient context.
      return false;
    }

    if (dp.isSyntaxHighlighting()
        && !last.getA().isWholeFile()) {
      // We need the whole file to syntax highlight accurately.
      return false;
    }

    return true;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    if (Gerrit.isSignedIn()) {
      setTitleFarEast(reviewed);
    }

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new UpToChangeCommand(patchKey.getParentKey(), 0, 'u'));
    keysNavigation.add(new FileListCmd(0, 'f', PatchUtil.C.fileList()));

    historyTable = new HistoryTable(this);

    commitMessageBlock = new CommitMessageBlock();

    topPanel = new FlowPanel();
    add(topPanel);

    noDifference = new Label(PatchUtil.C.noDifference());
    noDifference.setStyleName(Gerrit.RESOURCES.css().patchNoDifference());
    noDifference.setVisible(false);

    contentTable = createContentTable();
    contentTable.fileList = fileList;

    topNav = new NavLinks(keysNavigation, patchKey.getParentKey());
    bottomNav = new NavLinks(null, patchKey.getParentKey());

    add(topNav);
    contentPanel = new FlowPanel();
    contentPanel.setStyleName(Gerrit.RESOURCES.css()
        .sideBySideScreenSideBySideTable());
    contentPanel.add(noDifference);
    contentPanel.add(contentTable);
    add(contentPanel);
    add(bottomNav);

    if (fileList != null) {
      topNav.display(patchIndex, getPatchScreenType(), fileList);
      bottomNav.display(patchIndex, getPatchScreenType(), fileList);
    }
  }

  void setReviewedByCurrentUser(boolean reviewed) {
    if (fileList != null) {
      fileList.updateReviewedStatus(patchKey, reviewed);
    }

    PatchUtil.DETAIL_SVC.setReviewedByCurrentUser(patchKey, reviewed,
        new AsyncCallback<VoidResult>() {
          @Override
          public void onFailure(Throwable arg0) {
            // nop
          }

          @Override
          public void onSuccess(VoidResult result) {
            // nop
          }
        });
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    if (patchSetDetail == null) {
      Util.DETAIL_SVC.patchSetDetail(idSideB,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              patchSetDetail = result;
              if (fileList == null) {
                fileList = new PatchTable(prefs);
                fileList.setAccountInfoCache(lastScript.getCommentDetail().getAccounts());
                fileList.display(idSideA, result);
                patchIndex = fileList.indexOf(patchKey);
              }
              refresh(true);
            }
          });
    } else {
      refresh(true);
    }
  }

  @Override
  protected void onUnload() {
    if (regNavigation != null) {
      regNavigation.removeHandler();
      regNavigation = null;
    }
    super.onUnload();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    contentTable.setRegisterKeys(contentTable.isVisible());
    if (regNavigation != null) {
      regNavigation.removeHandler();
      regNavigation = null;
    }
    regNavigation = GlobalKey.add(this, keysNavigation);
  }

  protected abstract AbstractPatchContentTable createContentTable();

  public abstract PatchScreen.Type getPatchScreenType();

  public PatchSet.Id getSideA() {
    return idSideA;
  }

  public Patch.Key getPatchKey() {
    return patchKey;
  }

  public int getPatchIndex() {
    return patchIndex;
  }

  public PatchSetDetail getPatchSetDetail() {
    return patchSetDetail;
  }

  public PatchTable getFileList() {
    return fileList;
  }

  public TopView getTopView() {
    return topView;
  }

  protected void refresh(final boolean isFirst) {
    final int rpcseq = ++rpcSequence;
    lastScript = null;
    settingsPanel.setEnabled(false);
    PatchUtil.DETAIL_SVC.patchScript(patchKey, idSideA, idSideB, //
        settingsPanel.getValue(), new ScreenLoadCallback<PatchScript>(this) {
          @Override
          protected void preDisplay(final PatchScript result) {
            if (rpcSequence == rpcseq) {
              onResult(result, isFirst);
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            if (rpcSequence == rpcseq) {
              settingsPanel.setEnabled(true);
              super.onFailure(caught);
            }
          }
        });
  }

  private void onResult(final PatchScript script, final boolean isFirst) {
    final String path = PatchTable.getDisplayFileName(patchKey);
    String fileName = path;
    final int last = fileName.lastIndexOf('/');
    if (last >= 0) {
      fileName = fileName.substring(last + 1);
    }

    setWindowTitle(fileName);
    setPageTitle(path);

    if (idSideB.equals(patchSetDetail.getPatchSet().getId())) {
      commitMessageBlock.setVisible(true);
      commitMessageBlock.display(patchSetDetail.getInfo().getMessage());
    } else {
      commitMessageBlock.setVisible(false);
      Util.DETAIL_SVC.patchSetDetail(idSideB,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              commitMessageBlock.setVisible(true);
              commitMessageBlock.display(result.getInfo().getMessage());
            }
          });
    }

    historyTable.display(script.getHistory());

    // True if there are differences between the two patch sets
    boolean hasEdits = !script.getEdits().isEmpty();
    // True if this change is a mode change or a pure rename/copy
    boolean hasMeta = !script.getPatchHeader().isEmpty();

    boolean hasDifferences = hasEdits || hasMeta;
    boolean pureMetaChange = !hasEdits && hasMeta;

    if (contentTable instanceof SideBySideTable && pureMetaChange) {
      // User asked for SideBySide (or a link guessed, wrong) and we can't
      // show a binary or pure-rename change there accurately. Switch to
      // the unified view instead.
      //
      contentTable.removeFromParent();
      contentTable = new UnifiedDiffTable();
      contentTable.fileList = fileList;
      contentPanel.add(contentTable);
      setToken(Dispatcher.toPatchUnified(idSideA, patchKey));
    }

    if (hasDifferences) {
      contentTable.display(patchKey, idSideA, idSideB, script);
      contentTable.display(script.getCommentDetail(), script.isExpandAllComments());
      contentTable.finishDisplay();
    }
    showPatch(hasDifferences);
    settingsPanel.setEnableSmallFileFeatures(!script.isHugeFile());
    settingsPanel.setEnableIntralineDifference(script.hasIntralineDifference());
    settingsPanel.setEnabled(true);
    lastScript = script;

    if (fileList != null) {
      topNav.display(patchIndex, getPatchScreenType(), fileList);
      bottomNav.display(patchIndex, getPatchScreenType(), fileList);
    }

    if (Gerrit.isSignedIn()) {
      boolean isReviewed = false;
      if (isFirst && !prefs.get().isManualReview()) {
        isReviewed = true;
        setReviewedByCurrentUser(isReviewed);
      } else {
        for (Patch p : patchSetDetail.getPatches()) {
          if (p.getKey().equals(patchKey)) {
            isReviewed = p.isReviewedByCurrentUser();
            break;
          }
        }
      }
      reviewed.setValue(isReviewed);
    }

    intralineFailure = isFirst && script.hasIntralineFailure();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (intralineFailure) {
      intralineFailure = false;
      new ErrorDialog(PatchUtil.C.intralineFailure()).show();
    }
    if (topView != null && prefs.get().isRetainHeader()) {
      setTopView(topView);
    }
  }

  private void showPatch(final boolean showPatch) {
    noDifference.setVisible(!showPatch);
    contentTable.setVisible(showPatch);
    contentTable.setRegisterKeys(isCurrentView() && showPatch);
  }

  public void setTopView(TopView tv) {
    topView = tv;
    topPanel.clear();
    switch(tv) {
      case COMMIT:      topPanel.add(commitMessageBlock);
        break;
      case PREFERENCES: topPanel.add(settingsPanel);
        break;
      case PATCH_SETS:  topPanel.add(historyTable);
        break;
      case FILES:       topPanel.add(fileList);
        break;
    }
  }

  public class FileListCmd extends KeyCommand {
    public FileListCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      if (fileList == null || fileList.isAttached()) {
        final PatchSet.Id psid = patchKey.getParentKey();
        fileList = new PatchTable(prefs);
        fileList.setSavePointerId("PatchTable " + psid);
        Util.DETAIL_SVC.patchSetDetail(psid,
            new GerritCallback<PatchSetDetail>() {
              public void onSuccess(final PatchSetDetail result) {
                fileList.display(idSideA, result);
              }
            });
      }

      final PatchBrowserPopup p = new PatchBrowserPopup(patchKey, fileList);
      p.open();
    }
  }
}
