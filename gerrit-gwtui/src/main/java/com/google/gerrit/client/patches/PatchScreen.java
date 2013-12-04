// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.client.PrettyFactory;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

public abstract class PatchScreen extends AbstractPatchScreen {
  static final PrettyFactory PRETTY = ClientSideFormatter.FACTORY;
  static final short LARGE_FILE_CONTEXT = 100;

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
    public AbstractPatchScreen.Type getPatchScreenType() {
      return AbstractPatchScreen.Type.SIDE_BY_SIDE;
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
    public AbstractPatchScreen.Type getPatchScreenType() {
      return AbstractPatchScreen.Type.UNIFIED;
    }
  }

  private ReviewedPanels reviewedPanels;
  private AbstractPatchContentTable contentTable;

  private PatchScript lastScript;
  private int rpcSequence;

  /** The index of the file we are currently looking at among the fileList */
  private int patchIndex;

  /** Keys that cause an action on this screen */
  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;
  private boolean intralineFailure;
  private boolean intralineTimeout;

  protected PatchScreen(final Patch.Key id, final int patchIndex,
      final PatchSetDetail detail, final PatchTable patchTable,
      final TopView top, final PatchSet.Id baseId) {
    super(id, detail, patchTable, top, baseId);
    this.patchIndex = patchIndex;

    reviewedPanels = new ReviewedPanels();
    reviewedPanels.populate(patchKey, fileList, patchIndex, getPatchScreenType());
  }

  @Override
  public void notifyDraftDelta(int delta) {
    lastScript = null;
  }

  @Override
  public void remove(CommentEditorPanel panel) {
    lastScript = null;
  }

  @Override
  protected void update(AccountDiffPreference dp) {
    // Did the user just turn on auto-review?
    if (!reviewedPanels.getValue() && prefs.getOld().isManualReview()
        && !dp.isManualReview()) {
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
      setTitleFarEast(reviewedPanels.top);
    }

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new UpToChangeCommand(patchKey.getParentKey(), 0, 'u'));
    keysNavigation.add(new FileListCmd(0, 'f', PatchUtil.C.fileList()));
    createNavs(keysNavigation);

    if (Gerrit.isSignedIn()) {
      keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
      keysAction
          .add(new ToggleReviewedCmd(0, 'm', PatchUtil.C.toggleReviewed()));
      keysAction.add(new MarkAsReviewedAndGoToNextCmd(0, 'M', PatchUtil.C
          .markAsReviewedAndGoToNext()));
    }

    contentTable = createContentTable();
    contentTable.fileList = fileList;
    createContentPanel();

    add(topNav);
    contentPanel.add(contentTable);
    add(contentPanel);
    add(bottomNav);
    if (Gerrit.isSignedIn()) {
      add(reviewedPanels.bottom);
    }

    if (fileList != null) {
      topNav.display(patchIndex, getPatchScreenType(), fileList);
      bottomNav.display(patchIndex, getPatchScreenType(), fileList);
    }
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

  protected abstract AbstractPatchContentTable createContentTable();

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

  protected void refresh(final boolean isFirst) {
    final int rpcseq = ++rpcSequence;
    lastScript = null;
    settingsPanel.setEnabled(false);
    reviewedPanels.populate(patchKey, fileList, patchIndex, getPatchScreenType());
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
    PatchUtil.DETAIL_SVC.patchScript(patchKey, idSideA, idSideB,
        settingsPanel.getValue(), cb.addFinal(
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
      Util.DETAIL_SVC.patchSetDetail(idSideB,
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

    if (contentTable instanceof SideBySideTable
        && contentTable.isPureMetaChange(script)
        && !contentTable.isDisplayBinary) {
      // User asked for SideBySide (or a link guessed, wrong) and we can't
      // show a pure-rename change there accurately. Switch to
      // the unified view instead. User can set file comments on binary file
      // in SideBySide view.
      //
      contentTable.removeFromParent();
      contentTable = new UnifiedDiffTable();
      contentTable.fileList = fileList;
      contentTable.setCommentLinkProcessor(commentLinkProcessor);
      contentPanel.add(contentTable);
      setToken(Dispatcher.toPatchUnified(idSideA, patchKey));
    }

    if (script.isHugeFile()) {
      AccountDiffPreference dp = script.getDiffPrefs();
      int context = dp.getContext();
      if (context == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
        context = Short.MAX_VALUE;
      } else if (context > Short.MAX_VALUE) {
        context = Short.MAX_VALUE;
      }
      dp.setContext((short) Math.min(context, LARGE_FILE_CONTEXT));
      dp.setSyntaxHighlighting(false);
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
      topNav.display(patchIndex, getPatchScreenType(), fileList);
      bottomNav.display(patchIndex, getPatchScreenType(), fileList);
    }

    if (Gerrit.isSignedIn()) {
      boolean isReviewed = false;
      if (isFirst && !prefs.get().isManualReview() && patchIndex >= 0) {
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
    if (intralineFailure) {
      intralineFailure = false;
      new ErrorDialog(PatchUtil.C.intralineFailure()).show();
    } else if (intralineTimeout) {
      intralineTimeout = false;
      new ErrorDialog(PatchUtil.C.intralineTimeout()).setText(
          Gerrit.C.warnTitle()).show();
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
