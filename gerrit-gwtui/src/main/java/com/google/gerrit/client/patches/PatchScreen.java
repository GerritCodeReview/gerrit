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
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtjsonrpc.client.VoidResult;

public abstract class PatchScreen extends Screen implements
    CommentEditorContainer {
  static final PrettyFactory PRETTY = ClientSideFormatter.FACTORY;

  public static class SideBySide extends PatchScreen {
    public SideBySide(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable) {
      super(id, patchIndex, patchSetDetail, patchTable);
    }

    @Override
    protected SideBySideTable createContentTable() {
      return new SideBySideTable();
    }

    @Override
    protected PatchScreen.Type getPatchScreenType() {
      return PatchScreen.Type.SIDE_BY_SIDE;
    }
  }

  public static class Unified extends PatchScreen {
    public Unified(final Patch.Key id, final int patchIndex,
        final PatchSetDetail patchSetDetail, final PatchTable patchTable) {
      super(id, patchIndex, patchSetDetail, patchTable);
    }

    @Override
    protected UnifiedDiffTable createContentTable() {
      return new UnifiedDiffTable();
    }

    @Override
    protected PatchScreen.Type getPatchScreenType() {
      return PatchScreen.Type.UNIFIED;
    }
  }

  // Which patch set id's are being diff'ed
  private static PatchSet.Id diffSideA = null;
  private static PatchSet.Id diffSideB = null;

  private static Boolean historyOpen = null;
  private static final OpenHandler<DisclosurePanel> cacheOpenState =
      new OpenHandler<DisclosurePanel>() {
        @Override
        public void onOpen(OpenEvent<DisclosurePanel> event) {
          historyOpen = true;
        }
      };
  private static final CloseHandler<DisclosurePanel> cacheCloseState =
      new CloseHandler<DisclosurePanel>() {
        @Override
        public void onClose(CloseEvent<DisclosurePanel> event) {
          historyOpen = false;
        }
      };

  protected final Patch.Key patchKey;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable fileList;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected PatchScriptSettingsPanel settingsPanel;

  private DisclosurePanel historyPanel;
  private HistoryTable historyTable;
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
      final PatchSetDetail detail, final PatchTable patchTable) {
    patchKey = id;
    patchSetDetail = detail;
    fileList = patchTable;

    if (patchTable != null) {
      diffSideA = patchTable.getPatchSetIdToCompareWith();
    } else {
      diffSideA = null;
    }
    if (diffSideA == null) {
      historyOpen = null;
    }

    idSideA = diffSideA; // null here means we're diff'ing from the Base
    idSideB = diffSideB != null ? diffSideB : id.getParentKey();
    this.patchIndex = patchIndex;

    prefs = fileList != null ? fileList.getPreferences() :
                               new ListenableAccountDiffPreference();
    prefs.addValueChangeHandler(
        new ValueChangeHandler<AccountDiffPreference>() {
          @Override
          public void onValueChange(ValueChangeEvent<AccountDiffPreference> event) {
            update(event.getValue());
          }
        });

    settingsPanel = new PatchScriptSettingsPanel(prefs);
    settingsPanel.getReviewedCheckBox().addValueChangeHandler(
        new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            setReviewedByCurrentUser(event.getValue());
          }
        });
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

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new UpToChangeCommand(patchKey.getParentKey(), 0, 'u'));
    keysNavigation.add(new FileListCmd(0, 'f', PatchUtil.C.fileList()));

    historyTable = new HistoryTable(this);
    historyPanel = new DisclosurePanel(PatchUtil.C.patchHistoryTitle());
    historyPanel.setContent(historyTable);
    historyPanel.setVisible(false);
    // If the user selected a different patch set than the default for either
    // side, expand the history panel
    historyPanel.setOpen(diffSideA != null || diffSideB != null
        || (historyOpen != null && historyOpen));
    historyPanel.addOpenHandler(cacheOpenState);
    historyPanel.addCloseHandler(cacheCloseState);


    VerticalPanel vp = new VerticalPanel();
    vp.add(historyPanel);
    vp.add(settingsPanel);
    commitMessageBlock = new CommitMessageBlock("6em");
    HorizontalPanel hp = new HorizontalPanel();
    hp.setWidth("100%");
    hp.add(vp);
    hp.add(commitMessageBlock);
    add(hp);

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
      Util.DETAIL_SVC.patchSetDetail(idSideB, null, null,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              patchSetDetail = result;
              if (fileList == null) {
                fileList = new PatchTable(prefs);
                fileList.display(result);
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

  protected abstract PatchScreen.Type getPatchScreenType();

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

    final Change.Key cid = script.getChangeId();
    final String path = PatchTable.getDisplayFileName(patchKey);
    String fileName = path;
    final int last = fileName.lastIndexOf('/');
    if (last >= 0) {
      fileName = fileName.substring(last + 1);
    }

    setWindowTitle(PatchUtil.M.patchWindowTitle(cid.abbreviate(), fileName));
    setPageTitle(PatchUtil.M.patchPageTitle(cid.abbreviate(), path));

    if (idSideB.equals(patchSetDetail.getPatchSet().getId())) {
      commitMessageBlock.setVisible(true);
      commitMessageBlock.display(patchSetDetail.getInfo().getMessage());
    } else {
      commitMessageBlock.setVisible(false);
      Util.DETAIL_SVC.patchSetDetail(idSideB, null, null,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              commitMessageBlock.setVisible(true);
              commitMessageBlock.display(result.getInfo().getMessage());
            }
          });
    }

    historyTable.display(script.getHistory());
    historyPanel.setVisible(true);

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
      setToken(Dispatcher.toPatchUnified(patchKey));
    }

    if (hasDifferences) {
      contentTable.display(patchKey, idSideA, idSideB, script);
      contentTable.display(script.getCommentDetail());
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

    // Mark this file reviewed as soon we display the diff screen
    if (Gerrit.isSignedIn() && isFirst) {
      settingsPanel.getReviewedCheckBox().setValue(true);
      setReviewedByCurrentUser(true /* reviewed */);
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
  }

  private void showPatch(final boolean showPatch) {
    noDifference.setVisible(!showPatch);
    contentTable.setVisible(showPatch);
    contentTable.setRegisterKeys(isCurrentView() && showPatch);
  }

  public void setSideA(PatchSet.Id patchSetId) {
    idSideA = patchSetId;
    diffSideA = patchSetId;
    if (fileList != null) {
      fileList.setPatchSetIdToCompareWith(patchSetId);
    }
  }

  public void setSideB(PatchSet.Id patchSetId) {
    idSideB = patchSetId;
    diffSideB = patchSetId;
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
        Util.DETAIL_SVC.patchSetDetail(psid, null, null,
            new GerritCallback<PatchSetDetail>() {
              public void onSuccess(final PatchSetDetail result) {
                fileList.display(result);
              }
            });
      }

      final PatchBrowserPopup p = new PatchBrowserPopup(patchKey, fileList);
      p.open();
    }
  }
}
