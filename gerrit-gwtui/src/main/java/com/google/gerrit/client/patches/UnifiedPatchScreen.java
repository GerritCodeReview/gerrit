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
import com.google.gerrit.client.diff.DiffApi;
import com.google.gerrit.client.diff.DiffInfo;
import com.google.gerrit.client.info.WebLinkInfo;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.client.PrettyFactory;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.util.Collections;
import java.util.List;

public class UnifiedPatchScreen extends Screen implements
    CommentEditorContainer {
  static final PrettyFactory PRETTY = ClientSideFormatter.FACTORY;
  static final short LARGE_FILE_CONTEXT = 100;

  /**
   * What should be displayed in the top of the screen
   */
  public static enum TopView {
    MAIN, COMMIT, PREFERENCES, PATCH_SETS, FILES
  }

  protected final DiffType diffType;
  protected final Patch.Key patchKey;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable fileList;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected PatchScriptSettingsPanel settingsPanel;
  protected TopView topView;
  protected CommentLinkProcessor commentLinkProcessor;

  private ReviewedPanels reviewedPanels;
  private HistoryTable historyTable;
  private FlowPanel topPanel;
  private FlowPanel contentPanel;
  private UnifiedDiffTable contentTable;
  private CommitMessageBlock commitMessageBlock;
  private NavLinks topNav;
  private NavLinks bottomNav;

  private int rpcSequence;
  private PatchScript lastScript;

  /** The index of the file we are currently looking at among the fileList */
  private int patchIndex;
  private ListenableAccountDiffPreference prefs;
  private HandlerRegistration prefsHandler;

  /** Keys that cause an action on this screen */
  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;
  private boolean intralineFailure;
  private boolean intralineTimeout;

  public UnifiedPatchScreen(Patch.Key id, TopView top, PatchSet.Id baseId,
      DiffType diffType) {
    patchKey = id;
    topView = top;
    this.diffType = diffType;

    idSideA = baseId; // null here means we're diff'ing from the Base
    idSideB = id.getParentKey();

    prefs = fileList != null
        ? fileList.getPreferences()
        : new ListenableAccountDiffPreference();
    if (Gerrit.isSignedIn()) {
      prefs.reset();
    }
    reviewedPanels = new ReviewedPanels();
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

  private void update(DiffPreferencesInfo dp) {
    // Did the user just turn on auto-review?
    if (!reviewedPanels.getValue() && prefs.getOld().manualReview
        && !dp.manualReview) {
      reviewedPanels.setValue(true);
      reviewedPanels.setReviewedByCurrentUser(true);
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

  private boolean canReuse(DiffPreferencesInfo dp, PatchScript last) {
    if (last.getDiffPrefs().ignoreWhitespace != dp.ignoreWhitespace) {
      // Whitespace ignore setting requires server computation.
      return false;
    }

    final int ctx = dp.context;
    if (ctx == DiffPreferencesInfo.WHOLE_FILE_CONTEXT
        && !last.getA().isWholeFile()) {
      // We don't have the entire file here, so we can't render it.
      return false;
    }

    if (last.getDiffPrefs().context < ctx && !last.getA().isWholeFile()) {
      // We don't have sufficient context.
      return false;
    }

    if (dp.syntaxHighlighting && !last.getA().isWholeFile()) {
      // We need the whole file to syntax highlight accurately.
      return false;
    }

    return true;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    if (Gerrit.isSignedIn()) {
      setTitleFarEast(reviewedPanels.top);
    }

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new UpToChangeCommand(patchKey.getParentKey(), 0, 'u', diffType));
    keysNavigation.add(new FileListCmd(0, 'f', PatchUtil.C.fileList()));

    if (Gerrit.isSignedIn()) {
      keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
      keysAction
          .add(new ToggleReviewedCmd(0, 'm', PatchUtil.C.toggleReviewed()));
      keysAction.add(new MarkAsReviewedAndGoToNextCmd(0, 'M', PatchUtil.C
          .markAsReviewedAndGoToNext()));
    }

    historyTable = new HistoryTable(this);

    commitMessageBlock = new CommitMessageBlock();

    topPanel = new FlowPanel();
    add(topPanel);

    contentTable = new UnifiedDiffTable(diffType);
    contentTable.fileList = fileList;

    topNav = new NavLinks(keysNavigation, patchKey.getParentKey(), diffType);
    bottomNav = new NavLinks(null, patchKey.getParentKey(), diffType);

    add(topNav);
    contentPanel = new FlowPanel();
    contentPanel.setStyleName(Gerrit.RESOURCES.css().unifiedTable());

    contentPanel.add(contentTable);
    add(contentPanel);
    add(bottomNav);
    if (Gerrit.isSignedIn()) {
      add(reviewedPanels.bottom);
    }

    if (fileList != null) {
      displayNav();
    }
  }

  private void displayNav() {
    DiffApi.diff(idSideB, diffType, patchKey.getFileName())
      .base(idSideA)
      .webLinksOnly()
      .get(new GerritCallback<DiffInfo>() {
        @Override
        public void onSuccess(DiffInfo diffInfo) {
          topNav.display(patchIndex, fileList, diffType,
              getLinks(), getWebLinks(diffInfo));
          bottomNav.display(patchIndex, fileList, diffType,
              getLinks(), getWebLinks(diffInfo));
        }
      });
  }

  private List<InlineHyperlink> getLinks() {
    InlineHyperlink toSideBySideDiffLink = new InlineHyperlink();
    toSideBySideDiffLink.setHTML(new ImageResourceRenderer().render(Gerrit.RESOURCES.sideBySideDiff()));
    toSideBySideDiffLink.setTargetHistoryToken(getSideBySideDiffUrl());
    toSideBySideDiffLink.setTitle(PatchUtil.C.sideBySideDiff());
    return Collections.singletonList(toSideBySideDiffLink);
  }

  private List<WebLinkInfo> getWebLinks(DiffInfo diffInfo) {
    return diffInfo.unifiedWebLinks();
  }

  private String getSideBySideDiffUrl() {
    return Dispatcher.toPatch("sidebyside", idSideA, diffType,
        new Patch.Key(idSideB, patchKey.getFileName()));
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    if (patchSetDetail == null) {
      PatchUtil.CHANGE_SVC.patchSetDetail(idSideB, diffType,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              patchSetDetail = result;
              if (fileList == null) {
                fileList = new PatchTable(prefs, diffType);
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
    if (prefsHandler != null) {
      prefsHandler.removeHandler();
      prefsHandler = null;
    }
    if (regNavigation != null) {
      regNavigation.removeHandler();
      regNavigation = null;
    }
    if (regAction != null) {
      regAction.removeHandler();
      regAction = null;
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
    if (regAction != null) {
      regAction.removeHandler();
      regAction = null;
    }
    if (keysAction != null) {
      regAction = GlobalKey.add(this, keysAction);
    }
  }

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
    reviewedPanels.populate(patchKey, fileList, patchIndex, diffType);
    if (isFirst && fileList != null && fileList.isLoaded()) {
      fileList.movePointerTo(patchKey);
    }

    CallbackGroup cb = new CallbackGroup();
    ConfigInfoCache.get(patchSetDetail.getProject(),
        cb.add(new AsyncCallback<ConfigInfoCache.Entry>() {
          @Override
          public void onSuccess(ConfigInfoCache.Entry result) {
            commentLinkProcessor = result.getCommentLinkProcessor();
            contentTable.setCommentLinkProcessor(commentLinkProcessor);
            setTheme(result.getTheme());
          }

          @Override
          public void onFailure(Throwable caught) {
            // Handled by ScreenLoadCallback.onFailure.
          }
        }));
    PatchUtil.PATCH_SVC.patchScript(patchKey, diffType, idSideA, idSideB,
        settingsPanel.getValue(),
        cb.addFinal(
            new ScreenLoadCallback<PatchScript>(this) {
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
        }));
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
      commitMessageBlock.display(patchSetDetail.getInfo().getMessage(),
          commentLinkProcessor);
    } else {
      commitMessageBlock.setVisible(false);
      PatchUtil.CHANGE_SVC.patchSetDetail(idSideB, diffType,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              commitMessageBlock.setVisible(true);
              commitMessageBlock.display(result.getInfo().getMessage(),
                  commentLinkProcessor);
            }
          });
    }

    historyTable.display(script.getHistory());

    for (Patch p : patchSetDetail.getPatches()) {
      if (p.getKey().equals(patchKey)) {
        if (p.getPatchType().equals(Patch.PatchType.BINARY)) {
          contentTable.isDisplayBinary = true;
        }
        break;
      }
    }

    if (script.isHugeFile()) {
      DiffPreferencesInfo dp = script.getDiffPrefs();
      int context = dp.context;
      if (context == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
        context = Short.MAX_VALUE;
      } else if (context > Short.MAX_VALUE) {
        context = Short.MAX_VALUE;
      }
      dp.context = Math.min(context, LARGE_FILE_CONTEXT);
      dp.syntaxHighlighting = false;
      script.setDiffPrefs(dp);
    }

    contentTable.display(patchKey, idSideA, idSideB, script, patchSetDetail);
    contentTable.display(script.getCommentDetail(), script.isExpandAllComments());
    contentTable.finishDisplay();
    contentTable.setRegisterKeys(isCurrentView());

    settingsPanel.setEnableSmallFileFeatures(!script.isHugeFile());
    settingsPanel.setEnableIntralineDifference(script.hasIntralineDifference());
    settingsPanel.setEnabled(true);
    lastScript = script;

    if (fileList != null) {
      displayNav();
    }

    if (Gerrit.isSignedIn()) {
      boolean isReviewed = false;
      if (isFirst && !prefs.get().manualReview) {
        isReviewed = true;
        reviewedPanels.setReviewedByCurrentUser(isReviewed);
      } else {
        for (Patch p : patchSetDetail.getPatches()) {
          if (p.getKey().equals(patchKey)) {
            isReviewed = p.isReviewedByCurrentUser();
            break;
          }
        }
      }
      reviewedPanels.setValue(isReviewed);
    }

    intralineFailure = isFirst && script.hasIntralineFailure();
    intralineTimeout = isFirst && script.hasIntralineTimeout();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (prefsHandler == null) {
      prefsHandler = prefs.addValueChangeHandler(
          new ValueChangeHandler<DiffPreferencesInfo>() {
            @Override
            public void onValueChange(ValueChangeEvent<DiffPreferencesInfo> event) {
              update(event.getValue());
            }
          });
    }
    if (intralineFailure) {
      intralineFailure = false;
      new ErrorDialog(PatchUtil.C.intralineFailure()).show();
    } else if (intralineTimeout) {
      intralineTimeout = false;
      new ErrorDialog(PatchUtil.C.intralineTimeout()).setText(
          Gerrit.C.warnTitle()).show();
    }
    if (topView != null && prefs.get().retainHeader) {
      setTopView(topView);
    }
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
      case MAIN:
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
        fileList = new PatchTable(prefs, diffType);
        fileList.setSavePointerId("PatchTable " + psid);
        PatchUtil.CHANGE_SVC.patchSetDetail(psid, diffType,
            new GerritCallback<PatchSetDetail>() {
              @Override
              public void onSuccess(final PatchSetDetail result) {
                fileList.display(idSideA, result);
              }
            });
      }

      final PatchBrowserPopup p = new PatchBrowserPopup(patchKey, fileList);
      p.open();
    }
  }

  public class ToggleReviewedCmd extends KeyCommand {
    public ToggleReviewedCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final boolean isReviewed = !reviewedPanels.getValue();
      reviewedPanels.setValue(isReviewed);
      reviewedPanels.setReviewedByCurrentUser(isReviewed);
    }
  }

  public class MarkAsReviewedAndGoToNextCmd extends KeyCommand {
    public MarkAsReviewedAndGoToNextCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      reviewedPanels.go();
    }
  }
}
