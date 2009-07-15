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

import static com.google.gerrit.client.reviewdb.AccountGeneralPreferences.DEFAULT_CONTEXT;
import static com.google.gerrit.client.reviewdb.AccountGeneralPreferences.WHOLE_FILE_CONTEXT;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.PatchScriptSettings;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.DirectScreenLink;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.VoidResult;

public abstract class PatchScreen extends Screen {
  public static class SideBySide extends PatchScreen {
    public SideBySide(final Patch.Key id, final int patchIndex,
        final PatchTable patchTable) {
      super(id, patchIndex, patchTable);
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
        final PatchTable patchTable) {
      super(id, patchIndex, patchTable);
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

  // The change id for which the above patch set id's are valid
  private static Change.Id currentChangeId = null;

  protected final Patch.Key patchKey;
  protected PatchTable fileList;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected final PatchScriptSettings scriptSettings;

  private DisclosurePanel historyPanel;
  private HistoryTable historyTable;
  private FlowPanel contentPanel;
  private Label noDifference;
  private AbstractPatchContentTable contentTable;

  private int rpcSequence;
  private PatchScript script;
  private CommentDetail comments;

  /** The index of the file we are currently looking at among the fileList */
  private int patchIndex;

  /** Keys that cause an action on this screen */
  private KeyCommandSet keysNavigation;
  private HandlerRegistration regNavigation;

  /** Link to the screen for the previous file, null if not applicable */
  private DirectScreenLink previousFileLink;

  /** Link to the screen for the next file, null if not applicable */
  private DirectScreenLink nextFileLink;

  private static final char SHORTCUT_PREVIOUS_FILE = '[';
  private static final char SHORTCUT_NEXT_FILE = ']';

  /**
   * How this patch should be displayed in the patch screen.
   */
  public static enum Type {
    UNIFIED, SIDE_BY_SIDE
  }

  protected PatchScreen(final Patch.Key id, final int patchIndex,
      final PatchTable patchTable) {
    patchKey = id;
    fileList = patchTable;

    // If we have any diff side stored, make sure they are applicable to the current change,
    // discard them otherwise.
    Change.Id thisChangeId = id.getParentKey().getParentKey();
    if (currentChangeId != null && !currentChangeId.equals(thisChangeId)) {
      diffSideA = null;
      diffSideB = null;
    }
    currentChangeId = thisChangeId;
    idSideA = diffSideA; // null here means we're diff'ing from the Base
    idSideB = diffSideB != null ? diffSideB : id.getParentKey();
    this.patchIndex = patchIndex;
    scriptSettings = new PatchScriptSettings();

    initContextLines();
  }

  /**
   * Initialize the context lines to the user's preference, or to the default
   * number if the user is not logged in.
   */
  private void initContextLines() {
    if (Gerrit.isSignedIn()) {
      final AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      scriptSettings.setContext(p.getDefaultContext());
    } else {
      scriptSettings.setContext(DEFAULT_CONTEXT);
    }
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(new UpToChangeCommand(0, 'u', PatchUtil.C.upToChange()));
    keysNavigation.add(new FileListCmd(0, 'f', PatchUtil.C.fileList()));

    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    final String path = patchKey.get();
    String fileName = path;
    final int last = fileName.lastIndexOf('/');
    if (last >= 0) {
      fileName = fileName.substring(last + 1);
    }

    setWindowTitle(PatchUtil.M.patchWindowTitle(changeId.get(), fileName));
    setPageTitle(PatchUtil.M.patchPageTitle(changeId.get(), path));

    historyTable = new HistoryTable(this);
    historyPanel = new DisclosurePanel(PatchUtil.C.patchHistoryTitle());
    historyPanel.setContent(historyTable);
    historyPanel.setOpen(false);
    historyPanel.setVisible(false);
    // If the user selected a different patch set than the default for either side,
    // expand the history panel
    historyPanel.setOpen(diffSideA != null || diffSideB != null);
    add(historyPanel);
    initDisplayControls();

    noDifference = new Label(PatchUtil.C.noDifference());
    noDifference.setStyleName("gerrit-PatchNoDifference");
    noDifference.setVisible(false);

    contentTable = createContentTable();
    contentTable.fileList = fileList;

    add(createNextPrevLinks());
    contentPanel = new FlowPanel();
    contentPanel.setStyleName("gerrit-SideBySideScreen-SideBySideTable");
    contentPanel.add(noDifference);
    contentPanel.add(contentTable);
    add(contentPanel);
    add(createNextPrevLinks());

    // This must be done after calling createNextPrevLinks(), which initializes these fields
    if (previousFileLink != null) {
      installLinkShortCut(previousFileLink, SHORTCUT_PREVIOUS_FILE, PatchUtil.C.previousFileHelp());
    }
    if (nextFileLink != null) {
      installLinkShortCut(nextFileLink, SHORTCUT_NEXT_FILE, PatchUtil.C.nextFileHelp());
    }
  }

  private void installLinkShortCut(final DirectScreenLink link, char shortcut, String help) {
    keysNavigation.add(new KeyCommand(0, shortcut, help) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        link.go();
      }
    });
  }

  private void initDisplayControls() {
    final Grid displayControls = new Grid(0, 5);
    displayControls.setStyleName("gerrit-PatchScreen-DisplayControls");
    add(displayControls);

    createIgnoreWhitespace(displayControls, 0, 0);
    createContext(displayControls, 0, 2);
  }

  /**
   * Add the contextual widgets for this patch: "Show full files" and "Keep unreviewed"
   */
  private void createContext(final Grid parent, final int row, final int col) {
    parent.resizeRows(row + 1);

    // Show full files
    final CheckBox cb = new CheckBox(PatchUtil.C.showFullFiles());
    cb.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        if (event.getValue()) {
          // Show a diff of the full files
          scriptSettings.setContext(WHOLE_FILE_CONTEXT);
        } else {
          // Restore the context lines to the user's preference
          initContextLines();
        }
        refresh(false /* not the first time */);
      }
    });
    parent.setWidget(row, col + 1, cb);

    // "Reviewed" check box
    if (Gerrit.isSignedIn()) {
      final CheckBox ku = new CheckBox(PatchUtil.C.reviewed());
      ku.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          setReviewedByCurrentUser(event.getValue());
        }
      });
      // Checked by default
      ku.setValue(true);
      parent.setWidget(row, col + 2, ku);
    }

  }

  private void setReviewedByCurrentUser(boolean reviewed) {
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

  private void createIgnoreWhitespace(final Grid parent, final int row,
      final int col) {
    parent.resizeRows(row + 1);    
    final ListBox ws = new ListBox();
    ws.addItem(PatchUtil.C.whitespaceIGNORE_NONE(), Whitespace.IGNORE_NONE
        .name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_SPACE_AT_EOL(),
        Whitespace.IGNORE_SPACE_AT_EOL.name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_SPACE_CHANGE(),
        Whitespace.IGNORE_SPACE_CHANGE.name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_ALL_SPACE(),
        Whitespace.IGNORE_ALL_SPACE.name());
    ws.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        final int sel = ws.getSelectedIndex();
        if(0 <= sel){
          scriptSettings.setWhitespace(Whitespace.valueOf(ws.getValue(sel)));
          refresh(false /* not the first time */);
        }
      }
    });
    parent.setText(row, col, PatchUtil.C.whitespaceIgnoreLabel());
    parent.setWidget(row, col + 1, ws);
  }

  private Widget createNextPrevLinks() {
    final Grid table = new Grid(1, 3);
    final CellFormatter fmt = table.getCellFormatter();
    table.setStyleName("gerrit-SideBySideScreen-LinkTable");
    fmt.setHorizontalAlignment(0, 0, HasHorizontalAlignment.ALIGN_LEFT);
    fmt.setHorizontalAlignment(0, 1, HasHorizontalAlignment.ALIGN_CENTER);
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    if (fileList != null) {
      previousFileLink = fileList.getPreviousPatchLink(patchIndex, getPatchScreenType());
      table.setWidget(0, 0, previousFileLink);

      nextFileLink = fileList.getNextPatchLink(patchIndex, getPatchScreenType());
      table.setWidget(0, 2, nextFileLink);
    }

    final ChangeLink up =
        new ChangeLink("", patchKey.getParentKey().getParentKey());
    SafeHtml.set(up, SafeHtml.asis(Util.C.upToChangeIconLink()));
    table.setWidget(0, 1, up);

    return table;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    refresh(true);
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
    regNavigation = GlobalKey.add(this, keysNavigation);
  }

  protected abstract AbstractPatchContentTable createContentTable();

  protected abstract PatchScreen.Type getPatchScreenType();

  protected void refresh(final boolean isFirst) {
    final int rpcseq = ++rpcSequence;
    script = null;
    comments = null;

    // Mark this file reviewed as soon we display the diff screen
    if (Gerrit.isSignedIn() && isFirst) {
      setReviewedByCurrentUser(true /* reviewed */);
    }

    PatchUtil.DETAIL_SVC.patchScript(patchKey, idSideA, idSideB,
        scriptSettings, new GerritCallback<PatchScript>() {
          public void onSuccess(final PatchScript result) {
            if (rpcSequence == rpcseq) {
              script = result;
              onResult();
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            if (rpcSequence == rpcseq) {
              super.onFailure(caught);
            }
          }
        });

    PatchUtil.DETAIL_SVC.patchComments(patchKey, idSideA, idSideB,
        new GerritCallback<CommentDetail>() {
          public void onSuccess(final CommentDetail result) {
            if (rpcSequence == rpcseq) {
              comments = result;
              onResult();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            // Ignore no such entity, the patch script RPC above would
            // also notice the problem and report it.
            //
            if (!isNoSuchEntity(caught) && rpcSequence == rpcseq) {
              super.onFailure(caught);
            }
          }
        });
  }

  private void onResult() {
    if (script != null && comments != null) {
      if (comments.getHistory().size() > 1) {
        historyTable.display(comments.getHistory());
        historyPanel.setVisible(true);
      } else {
        historyPanel.setVisible(false);
      }

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
        History.newItem(Link.toPatchUnified(patchKey), false);
      }

      if (hasDifferences) {
        contentTable.display(patchKey, idSideA, idSideB, script);
        contentTable.display(comments);
        contentTable.finishDisplay();
      }
      showPatch(hasDifferences);

      script = null;
      comments = null;

      if (!isCurrentView()) {
        display();
      }
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
  }

  public void setSideB(PatchSet.Id patchSetId) {
    idSideB = patchSetId;
    diffSideB = patchSetId;
  }

  public class UpToChangeCommand extends KeyCommand {
    public UpToChangeCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final Change.Id ck = patchKey.getParentKey().getParentKey();
      Gerrit.display(Link.toChange(ck), new ChangeScreen(ck));
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
        fileList = new PatchTable();
        fileList.setSavePointerId("PatchTable " + psid);
        Util.DETAIL_SVC.patchSetDetail(psid,
            new GerritCallback<PatchSetDetail>() {
              public void onSuccess(final PatchSetDetail result) {
                fileList.display(psid, result.getPatches());
              }
            });
      }

      final PatchBrowserPopup p = new PatchBrowserPopup(patchKey, fileList);
      p.open();
    }
  }
}
