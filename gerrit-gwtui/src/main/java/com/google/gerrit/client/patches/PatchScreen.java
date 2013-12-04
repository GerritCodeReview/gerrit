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
import com.google.gerrit.client.patches.AbstractPatchContentTable.CommentList;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ContentTableKeyNavigation;
import com.google.gerrit.client.ui.Diff;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.client.PrettyFactory;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.util.ArrayList;
import java.util.List;

public abstract class PatchScreen extends AbstractPatchScreen implements Diff.Delegate {
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

    @Override
    public void onFocus(final Diff diff) {

    }

    @Override
    public void onLoad(final Diff diff) {

    }

  }

  public static class AllSideBySide extends PatchScreen {
    public AllSideBySide(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable,
        final TopView topView, final PatchSet.Id baseId) {
       super(id, patchIndex, patchSetDetail, patchTable, topView, baseId);
       diffFactory = new Diff.SideBySideFactory();

    }

    @Override
    protected SideBySideTable createContentTable() {
      return new SideBySideTable();
    }

    @Override
    public AbstractPatchScreen.Type getPatchScreenType() {
      return AbstractPatchScreen.Type.SIDE_BY_SIDE;
    }

    @Override
    protected void onLoad() {
      super.onLoad();

      if (patchSetDetail == null) {
        Util.DETAIL_SVC.patchSetDetail(getPatchId(),
            new GerritCallback<PatchSetDetail>() {
              @Override
              public void onSuccess(PatchSetDetail result) {
                patchSetDetail = result;

                if (fileList == null) {
                  fileList = new PatchTable(prefs);
                  fileList.display(idSideA, result);
                  patchIndex = fileList.indexOf(patchKey);
                }

                if (!result.getPatches().isEmpty()) {
                  patchKey = new Patch.Key(result.getPatches().get(0).getKey()
                      .getParentKey(), Patch.ALL);
                }
                allRefresh(true);
              }
            });
      } else {
        allRefresh(true);
      }
    }
    @Override
    protected void onInitUI() {
      super.onInitUI();
      Window.scrollTo(0, getAbsoluteTop());
      createNavs(null);

      contentTable = createContentTable();
      contentTable.setAllMode(true);
      contentTable.fileList = fileList;
      createContentPanel();

      files = new FlowPanel();
      contentPanel.add(files);

      contentPanel.add(contentTable);
      add(contentPanel);
      keyNavigation.initializeKeys();
    }

    @Override
    public void onFocus(final Diff diff) {
      if (diff != keyNavigation.getDiff()) {
        keyNavigation.getDiff().getContentTable().hideCursor();
      }
      keyNavigation.setDiff(diff);
    }

    @Override
    public void onLoad(final Diff diff) {
      intralineFailure = diff.hasIntralineFailure();
      if (keyNavigation.getDiff() == null) {
        keyNavigation.setDiff(diff);
      }
      diffs.add(diff);  //add comment history

      // Delay displaying diff if previous diffs are not loaded yet.
      for (Widget widget : files) {
        final Diff df = (Diff) widget;
        if (df.isLoaded()) {
          if (!df.isVisible()) {
            df.setVisible(true);
          }
        } else {
          break;
        }
      }
      if (!isCurrentView()) {
        display();
      }
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
    @Override
    public void onFocus(final Diff diff) {

    }

    @Override
    public void onLoad(final Diff diff) {

    }
  }


  public static class AllUnified extends PatchScreen {
    public AllUnified(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable,
        final TopView topView, final PatchSet.Id baseId) {
       super(id, patchIndex, patchSetDetail, patchTable, topView, baseId);
       diffFactory = new Diff.UnifiedFactory();

    }

    @Override
    protected UnifiedDiffTable createContentTable() {
      return new UnifiedDiffTable();
    }

    @Override
    public AbstractPatchScreen.Type getPatchScreenType() {
      return AbstractPatchScreen.Type.UNIFIED;
    }

    @Override
    protected void onLoad() {
      super.onLoad();
      keyNavigation.setRegisterKeys(true);

      //load file list
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

                if (!result.getPatches().isEmpty()) {
                  patchKey = new Patch.Key(result.getPatches().get(0).getKey()
                      .getParentKey(), Patch.ALL);
                }
                allRefresh(true);
              }
            });
      } else {
        allRefresh(true);
      }
    }

    @Override
    public void onFocus(final Diff diff) {
      if (diff != keyNavigation.getDiff()) {
        keyNavigation.getDiff().getContentTable().hideCursor();
      }
      keyNavigation.setDiff(diff);
    }

    @Override
    public void onLoad(final Diff diff) {
      intralineFailure = diff.hasIntralineFailure();
      if (keyNavigation.getDiff() == null) {
        keyNavigation.setDiff(diff);
      }
      diffs.add(diff);  //add comment history

      // Delay displaying diff if previous diffs are not loaded yet.
      for (Widget widget : files) {
        final Diff df = (Diff) widget;
        if (df.isLoaded()) {
          if (!df.isVisible()) {
            df.setVisible(true);
          }
        } else {
          break;
        }
      }
      if (!isCurrentView()) {
        display();
      }
    }

    @Override
    protected void onInitUI() {
      super.onInitUI();
      Window.scrollTo(0, getAbsoluteTop());
      createNavs(null);

      contentTable = createContentTable();
      contentTable.fileList = fileList;
      contentTable.setAllMode(true);
      createContentPanel();

      files = new FlowPanel();
      contentPanel.add(files);

      contentPanel.add(contentTable);
      add(contentPanel);
      keyNavigation.initializeKeys();
    }

  }

  protected ReviewedPanels reviewedPanels;
  protected AbstractPatchContentTable contentTable;

  private PatchScript lastScript;
  private int rpcSequence;

  /** The index of the file we are currently looking at among the fileList */
  protected int patchIndex;

  /** Keys that cause an action on this screen */
  protected KeyCommandSet keysNavigation;
  protected KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;
  protected boolean intralineFailure;
  private boolean intralineTimeout;
  protected KeyNavigation keyNavigation;


  protected PatchScreen(final Patch.Key id, final int patchIndex,
      final PatchSetDetail detail, final PatchTable patchTable,
      final TopView top, final PatchSet.Id baseId) {
    super(id, detail, patchTable, top, baseId);
    this.patchIndex = patchIndex;
    setPatchId(id.getParentKey());
    diffs = new ArrayList<Diff>();
    keyNavigation = new KeyNavigation(this);

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

    files = new FlowPanel();
    contentPanel.add(files);

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
    keyNavigation.setRegisterKeys(false);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    keyNavigation.setRegisterKeys(false);
    keyNavigation.setRegisterKeys(true);

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

  protected FlowPanel files;
  protected List<Diff> diffs;
  protected Diff.Factory diffFactory;
  private PatchSet.Id id;

  protected void allRefresh(final boolean isFirst) {
    final int rpcseq = ++rpcSequence;
    lastScript = null;
    settingsPanel.setEnabled(false);

    files.clear();
    diffs.clear();
    keyNavigation.clear();

    // loadDiffs
    final List<Patch> patchList = fileList.getPatchList();
    for (int i = 0; i < patchList.size(); ++i) {
      final Patch patch = patchList.get(i);
      Diff diff =
          diffFactory.createDiff(patch.getKey(), idSideA, idSideB,
              settingsPanel.getValue());
      diff.addDelegate(this);
      diff.setVisible(false);
      files.add(diff);
      diff.load();
    }

    CallbackGroup cb = new CallbackGroup();
    ConfigInfoCache.get(patchSetDetail.getProject(),
        cb.add(new AsyncCallback<ConfigInfoCache.Entry>() {
          @Override
          public void onSuccess(ConfigInfoCache.Entry result) {
            commentLinkProcessor = result.getCommentLinkProcessor();
            for (int i = 0; i < diffs.size(); ++i) {
              final Diff dff = diffs.get(i);
              dff.getContentTable().setCommentLinkProcessor(commentLinkProcessor);
            }
            updateCommitInformation(patchKey);
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

  private void setPatchId(PatchSet.Id id) {
    this.id = id;
  }
  protected PatchSet.Id getPatchId() {
    return id;
  }

  private void updateCommitInformation(final Patch.Key patchKey) {
    final Change.Id cid = patchKey.getParentKey().getParentKey();

    setWindowTitle(cid.toString());
    final String subject = patchSetDetail.getInfo().getSubject();
    setPageTitle(cid.toString() + ": " + subject);

    if (idSideB.equals(patchSetDetail.getPatchSet().getId())) {
      commitMessageBlock.setVisible(true);
      if (commentLinkProcessor != null) {
        commitMessageBlock.display(patchSetDetail.getInfo().getMessage(), commentLinkProcessor);
      }
    } else {
      commitMessageBlock.setVisible(false);
      Util.DETAIL_SVC.patchSetDetail(idSideB,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              commitMessageBlock.setVisible(true);
              if (commentLinkProcessor != null) {
                commitMessageBlock.display(result.getInfo().getMessage(), commentLinkProcessor);
              }
            }
          });
    }
  }
  private class KeyNavigation extends ContentTableKeyNavigation {
    private class NextFileCmd extends KeyCommand {
      public NextFileCmd(int mask, char key, String help) {
        super(mask, key, help);
      }

      @Override
      public void onKeyPress(final KeyPressEvent event) {
        onFileNext();
      }
    }
    private class PrevFileCmd extends KeyCommand {
      public PrevFileCmd(int mask, char key, String help) {
        super(mask, key, help);
      }

      @Override
      public void onKeyPress(final KeyPressEvent event) {
        onFilePrev();
      }
    }

    private Diff diff;

    private AbstractPatchContentTable contentTable;

    public KeyNavigation(Widget parent) {
      super(parent);
    }

    public void clear() {
      diff = null;
      contentTable = null;
    }

    public Diff getDiff() {
      return diff;
    }

    @Override
    public void initializeKeys() {
      if (!initialized) {
        keysNavigation.add(new NextFileCmd(0, 'w', PatchUtil.C.nextFileHelp()));
        keysNavigation.add(new PrevFileCmd(0, 'q', PatchUtil.C
            .previousFileHelp()));
        super.initializeKeys();
      }
    }

    public void setDiff(Diff diff) {
      this.diff = diff;
      if (diff != null) {
        contentTable = diff.getContentTable();
        contentTable.ensurePointerVisible();
      } else {
        contentTable = null;
      }
    }

    @Override
    protected void onChunkNext() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.moveToNextChunk(contentTable.getCurrentRow());
      }
    }

    @Override
    protected void onChunkPrev() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.moveToPrevChunk(contentTable.getCurrentRow());
      }
    }

    @Override
    protected void onCommentNext() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.moveToNextComment(contentTable.getCurrentRow());
      }
    }

    @Override
    protected void onCommentPrev() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.moveToPrevComment(contentTable.getCurrentRow());
      }
    }

    protected void onFileNext() {
      contentTable.hideCursor();
      diff = getNextDiff();
      contentTable = diff.getContentTable();
      Window.scrollTo(0, diff.getAbsoluteTop());
      contentTable.ensurePointerVisible();
      contentTable.showCursor();
    }

    protected void onFilePrev() {
      contentTable.hideCursor();
      diff = getPrevDiff();
      contentTable = diff.getContentTable();
      Window.scrollTo(0, diff.getAbsoluteTop());
      contentTable.ensurePointerVisible();
      contentTable.showCursor();
    }

    @Override
    protected void onInsertComment() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        for (int row = contentTable.getCurrentRow(); 0 <= row; row--) {
          final Object item = contentTable.getRowItem(row);
          if (item instanceof PatchLine) {
            contentTable.onInsertComment((PatchLine) item);
            return;
          } else if (item instanceof CommentList) {
            continue;
          } else {
            return;
          }
        }
      }
    }

    @Override
    protected void onNext() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.onDown();
      }
    }

    @Override
    protected void onOpen() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.onOpenCurrent();
      }
    }

    @Override
    protected void onPrev() {
      if (contentTable != null) {
        contentTable.ensurePointerVisible();
        contentTable.onUp();
      }
    }

    @Override
    protected void onPublishComments() {
      final PatchSet.Id id = patchKey.getParentKey();
      Gerrit.display(Dispatcher.toPublish(id));
    }
  }
  private Diff getNextDiff() {
    if (keyNavigation.getDiff() == null) {
      if (!diffs.isEmpty()) {
        return diffs.get(0);
      } else {
        return null;
      }
    } else {
      int index = diffs.indexOf(keyNavigation.getDiff());
      if (index + 1 < diffs.size()) {
        return diffs.get(index + 1);
      } else {
        return diffs.get(0);
      }
    }
  }

  private Diff getPrevDiff() {
    if (keyNavigation.getDiff() == null) {
      if (!diffs.isEmpty()) {
        return diffs.get(0);
      } else {
        return null;
      }
    } else {
      int index = diffs.indexOf(keyNavigation.getDiff());
      if (index - 1 >= 0) {
        return diffs.get(index - 1);
      } else {
        return diffs.get(diffs.size() - 1);
      }
    }
  }

}
