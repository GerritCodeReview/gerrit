//Copyright (C) 2008 The Android Open Source Project
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
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.common.PrettyFormatter;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import org.eclipse.jgit.diff.Edit;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPatchContentTable extends NavigationTable<Object>
    implements CommentEditorContainer, FocusHandler, BlurHandler {
  public static final int R_HEAD = 0;
  static final short FILE_SIDE_A = (short) 0;
  static final short FILE_SIDE_B = (short) 1;
  protected PatchTable fileList;
  protected AccountInfoCache accountCache = AccountInfoCache.empty();
  protected Patch.Key patchKey;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected boolean onlyOneHunk;
  protected PatchSetSelectBox headerSideA;
  protected PatchSetSelectBox headerSideB;
  protected Image iconA;
  protected Image iconB;

  private final KeyCommandSet keysComment;
  private HandlerRegistration regComment;
  private final KeyCommandSet keysOpenByEnter;
  private HandlerRegistration regOpenByEnter;
  boolean isDisplayBinary;

  protected AbstractPatchContentTable() {
    keysNavigation.add(new PrevKeyCommand(0, 'k', PatchUtil.C.linePrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', PatchUtil.C.lineNext()));
    keysNavigation.add(new PrevChunkKeyCmd(0, 'p', PatchUtil.C.chunkPrev()));
    keysNavigation.add(new NextChunkKeyCmd(0, 'n', PatchUtil.C.chunkNext()));
    keysNavigation.add(new PrevCommentCmd(0, 'P', PatchUtil.C.commentPrev()));
    keysNavigation.add(new NextCommentCmd(0, 'N', PatchUtil.C.commentNext()));

    keysAction.add(new OpenKeyCommand(0, 'o', PatchUtil.C.expandComment()));
    keysOpenByEnter = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysOpenByEnter.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, PatchUtil.C.expandComment()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new InsertCommentCommand(0, 'c', PatchUtil.C
          .commentInsert()));
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));

      // See CommentEditorPanel
      //
      keysComment = new KeyCommandSet(PatchUtil.C.commentEditorSet());
      keysComment.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 's', PatchUtil.C
          .commentSaveDraft()));
      keysComment.add(new NoOpKeyCommand(0, KeyCodes.KEY_ESCAPE, PatchUtil.C
          .commentCancelEdit()));
    } else {
      keysComment = null;
    }

    table.setStyleName(Gerrit.RESOURCES.css().patchContentTable());
  }

  abstract void createFileCommentEditorOnSideA();

  abstract void createFileCommentEditorOnSideB();

  abstract PatchScreen.Type getPatchScreenType();

  protected void initHeaders(PatchScript script, PatchSetDetail detail) {
    PatchScreen.Type type = getPatchScreenType();
    headerSideA = new PatchSetSelectBox(PatchSetSelectBox.Side.A, type);
    headerSideA.display(detail, script, patchKey, idSideA, idSideB);
    headerSideA.addDoubleClickHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent event) {
        if (headerSideA.isFileOrCommitMessage()) {
          createFileCommentEditorOnSideA();
        }
      }
    });
    headerSideB = new PatchSetSelectBox(PatchSetSelectBox.Side.B, type);
    headerSideB.display(detail, script, patchKey, idSideA, idSideB);
    headerSideB.addDoubleClickHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent event) {
        if (headerSideB.isFileOrCommitMessage()) {
          createFileCommentEditorOnSideB();
        }
      }
    });

    // Prepare icons.
    iconA = new Image(Gerrit.RESOURCES.addFileComment());
    iconA.setTitle(PatchUtil.C.addFileCommentToolTip());
    iconA.addStyleName(Gerrit.RESOURCES.css().link());
    iconA.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        createFileCommentEditorOnSideA();
      }
    });
    iconB = new Image(Gerrit.RESOURCES.addFileComment());
    iconB.setTitle(PatchUtil.C.addFileCommentToolTip());
    iconB.addStyleName(Gerrit.RESOURCES.css().link());
    iconB.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        createFileCommentEditorOnSideB();
      }
    });
  }

  @Override
  public void notifyDraftDelta(final int delta) {
    if (fileList != null) {
      fileList.notifyDraftDelta(patchKey, delta);
    }

    Widget p = getParent();
    while (p != null) {
      if (p instanceof CommentEditorContainer) {
        ((CommentEditorContainer) p).notifyDraftDelta(delta);
        break;
      }
      p = p.getParent();
    }
  }

  @Override
  public void remove(CommentEditorPanel panel) {
    final int nRows = table.getRowCount();
    for (int row = 0; row < nRows; row++) {
      final int nCells = table.getCellCount(row);
      for (int cell = 0; cell < nCells; cell++) {
        if (table.getWidget(row, cell) == panel) {
          destroyEditor(row, cell);
          Widget p = table;
          while (p != null) {
            if (p instanceof Focusable) {
              ((Focusable) p).setFocus(true);
              break;
            }
            p = p.getParent();
          }

          if (table.getCellFormatter().getStyleName(row - 1, cell)
              .contains(Gerrit.RESOURCES.css().commentHolder())) {
            table.getCellFormatter().addStyleName(row - 1, cell,
                Gerrit.RESOURCES.css().commentPanelLast());
          }
          return;
        }
      }
    }
  }

  @Override
  public void setRegisterKeys(final boolean on) {
    super.setRegisterKeys(on);
    if (on && keysComment != null && regComment == null) {
      regComment = GlobalKey.add(this, keysComment);
    } else if (!on && regComment != null) {
      regComment.removeHandler();
      regComment = null;
    }

    if (on && keysOpenByEnter != null && regOpenByEnter == null) {
      regOpenByEnter = GlobalKey.add(this, keysOpenByEnter);
    } else if (!on && regOpenByEnter != null) {
      regOpenByEnter.removeHandler();
      regOpenByEnter = null;
    }
  }

  public void display(final Patch.Key k, final PatchSet.Id a,
      final PatchSet.Id b, final PatchScript s, final PatchSetDetail d) {
    patchKey = k;
    idSideA = a;
    idSideB = b;

    render(s, d);
  }

  protected boolean hasDifferences(PatchScript script) {
    return hasEdits(script) || hasMeta(script);
  }

  public boolean isPureMetaChange(PatchScript script) {
    return !hasEdits(script) && hasMeta(script);
  }

  // True if there are differences between the two patch sets
  private boolean hasEdits(PatchScript script) {
    for (Edit e : script.getEdits()) {
      if (e.getType() != Edit.Type.EMPTY) {
        return true;
      }
    }
    return false;
  }

  // True if this change is a mode change or a pure rename/copy
  private boolean hasMeta(PatchScript script) {
    return !script.getPatchHeader().isEmpty();
  }

  protected void appendNoDifferences(SafeHtmlBuilder m) {
    m.openTr();
    m.openTd();
    m.setAttribute("colspan", 5);
    m.openDiv();
    m.addStyleName(Gerrit.RESOURCES.css().patchNoDifference());
    m.append(PatchUtil.C.noDifference());
    m.closeDiv();
    m.closeTd();
    m.closeTr();
  }

  protected SparseHtmlFile getSparseHtmlFileA(PatchScript s) {
    AccountDiffPreference dp = new AccountDiffPreference(s.getDiffPrefs());
    dp.setShowWhitespaceErrors(false);

    PrettyFormatter f = ClientSideFormatter.FACTORY.get();
    f.setDiffPrefs(dp);
    f.setFileName(s.getA().getPath());
    f.setEditFilter(PrettyFormatter.A);
    f.setEditList(s.getEdits());
    f.format(s.getA());
    return f;
  }

  protected SparseHtmlFile getSparseHtmlFileB(PatchScript s) {
    AccountDiffPreference dp = new AccountDiffPreference(s.getDiffPrefs());

    PrettyFormatter f = ClientSideFormatter.FACTORY.get();
    f.setDiffPrefs(dp);
    f.setFileName(s.getB().getPath());
    f.setEditFilter(PrettyFormatter.B);
    f.setEditList(s.getEdits());

    if ((dp.isSyntaxHighlighting() || dp.getContext() != AccountDiffPreference.WHOLE_FILE_CONTEXT)
        && s.getA().isWholeFile() && !s.getB().isWholeFile()) {
      f.format(s.getB().apply(s.getA(), s.getEdits()));
    } else {
      f.format(s.getB());
    }
    return f;
  }

  protected abstract void render(PatchScript script, final PatchSetDetail detail);

  protected abstract void onInsertComment(PatchLine pl);

  public abstract void display(CommentDetail comments, boolean expandComments);

  @Override
  protected Object getRowItemKey(final Object item) {
    return null;
  }

  protected void initScript(final PatchScript script) {
    if (script.getEdits().size() == 1) {
      final SparseFileContent a = script.getA();
      final SparseFileContent b = script.getB();
      onlyOneHunk = a.size() == 0 || b.size() == 0;
    } else {
      onlyOneHunk = false;
    }
  }

  private boolean isChunk(final int row) {
    final Object o = getRowItem(row);
    if (!onlyOneHunk && o instanceof PatchLine) {
      final PatchLine pl = (PatchLine) o;
      switch (pl.getType()) {
        case DELETE:
        case INSERT:
        case REPLACE:
          return true;
        case CONTEXT:
          break;
      }
    } else if (o instanceof CommentList) {
      return true;
    }
    return false;
  }

  private int findChunkStart(int row) {
    while (0 <= row && isChunk(row)) {
      row--;
    }
    return row + 1;
  }

  private int findChunkEnd(int row) {
    final int max = table.getRowCount();
    while (row < max && isChunk(row)) {
      row++;
    }
    return row - 1;
  }

  private static int oneBefore(final int begin) {
    return 1 <= begin ? begin - 1 : begin;
  }

  private int oneAfter(final int end) {
    return end + 1 < table.getRowCount() ? end + 1 : end;
  }

  private void moveToPrevChunk(int row) {
    while (0 <= row && isChunk(row)) {
      row--;
    }
    for (; 0 <= row; row--) {
      if (isChunk(row)) {
        final int start = findChunkStart(row);
        movePointerTo(start, false);
        scrollIntoView(oneBefore(start), oneAfter(row));
        return;
      }
    }

    // No prior hunk found? Try to hit the first line in the file.
    //
    for (row = 0; row < table.getRowCount(); row++) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private void moveToNextChunk(int row) {
    final int max = table.getRowCount();
    while (row < max && isChunk(row)) {
      row++;
    }
    for (; row < max; row++) {
      if (isChunk(row)) {
        movePointerTo(row, false);
        scrollIntoView(oneBefore(row), oneAfter(findChunkEnd(row)));
        return;
      }
    }

    // No next hunk found? Try to hit the last line in the file.
    //
    for (row = max - 1; row >= 0; row--) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private void moveToPrevComment(int row) {
    while (0 <= row && isComment(row)) {
      row--;
    }
    for (; 0 <= row; row--) {
      if (isComment(row)) {
        movePointerTo(row, false);
        scrollIntoView(oneBefore(row), oneAfter(row));
        return;
      }
    }

    // No prior comment found? Try to hit the first line in the file.
    //
    for (row = 0; row < table.getRowCount(); row++) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private void moveToNextComment(int row) {
    final int max = table.getRowCount();
    while (row < max && isComment(row)) {
      row++;
    }
    for (; row < max; row++) {
      if (isComment(row)) {
        movePointerTo(row, false);
        scrollIntoView(oneBefore(row), oneAfter(row));
        return;
      }
    }

    // No next comment found? Try to hit the last line in the file.
    //
    for (row = max - 1; row >= 0; row--) {
      if (getRowItem(row) != null) {
        movePointerTo(row);
        break;
      }
    }
  }

  private boolean isComment(int row) {
    return getRowItem(row) instanceof CommentList;
  }

  /**
   * Invokes createCommentEditor() with an empty string as value for the comment
   * parent UUID. This method is invoked by callers that want to create an
   * editor for a comment that is not a reply.
   */
  protected void createCommentEditor(final int suggestRow, final int column,
      final int line, final short file) {
    if (Gerrit.isSignedIn()) {
      if (R_HEAD <= line) {
        final Patch.Key parentKey;
        final short side;
        switch (file) {
          case 0:
            if (idSideA == null) {
              parentKey = new Patch.Key(idSideB, patchKey.get());
              side = (short) 0;
            } else {
              parentKey = new Patch.Key(idSideA, patchKey.get());
              side = (short) 1;
            }
            break;
          case 1:
            parentKey = new Patch.Key(idSideB, patchKey.get());
            side = (short) 1;
            break;
          default:
            throw new RuntimeException("unexpected file id " + file);
        }

        final PatchLineComment newComment =
            new PatchLineComment(new PatchLineComment.Key(parentKey, null),
                line, Gerrit.getUserAccount().getId(), null);
        newComment.setSide(side);
        newComment.setMessage("");

        findOrCreateCommentEditor(suggestRow, column, newComment, true)
            .setFocus(true);
      }
    } else {
      Gerrit.doSignIn(History.getToken());
    }
  }

  protected void updateCursor(final PatchLineComment newComment) {
  }

  abstract void insertFileCommentRow(final int row);

  private CommentEditorPanel findOrCreateCommentEditor(final int suggestRow,
      final int column, final PatchLineComment newComment, final boolean create) {
    int row = suggestRow;
    int spans[] = new int[column + 1];
    FIND_ROW: while (row < table.getRowCount()) {
      int col = 0;
      for (int cell = 0; row < table.getRowCount()
          && cell < table.getCellCount(row); cell++) {
        while (col < column && 0 < spans[col]) {
          spans[col++]--;
        }
        spans[col] = table.getFlexCellFormatter().getRowSpan(row, cell);
        if (col == column) {
          final Widget w = table.getWidget(row, cell);
          if (w instanceof CommentEditorPanel
              && ((CommentEditorPanel) w).getComment().getKey().getParentKey()
                  .equals(newComment.getKey().getParentKey())) {
            // Don't insert two editors on the same position, it doesn't make
            // any sense to the user.
            //
            return ((CommentEditorPanel) w);

          } else if (w instanceof CommentPanel) {
            if (newComment != null && newComment.getParentUuid() != null) {
              // If we are a reply, we were given the exact row to insert
              // ourselves at. We should be before this panel so break.
              //
              break FIND_ROW;
            }
            row++;
            cell--;
          } else {
            break FIND_ROW;
          }
        }
      }
    }

    if (newComment == null || !create) {
      return null;
    }

    final CommentEditorPanel ed = new CommentEditorPanel(newComment);
    ed.addFocusHandler(this);
    ed.addBlurHandler(this);
    boolean isCommentRow = false;
    boolean needInsert = false;
    if (row < table.getRowCount()) {
      for (int cell = 0; cell < table.getCellCount(row); cell++) {
        final Widget w = table.getWidget(row, cell);
        if (w instanceof CommentEditorPanel || w instanceof CommentPanel) {
          if (column == cell) {
            needInsert = true;
          }
          isCommentRow = true;
        }
      }
    }
    if (needInsert || !isCommentRow) {
      if (newComment.getLine() == R_HEAD) {
        insertFileCommentRow(row);
      } else {
        insertRow(row);
      }
      styleCommentRow(row);
    }
    table.setWidget(row, column, ed);
    styleLastCommentCell(row, column);

    int span = 1;
    for (int r = row + 1; r < table.getRowCount(); r++) {
      boolean hasComment = false;
      for (int c = 0; c < table.getCellCount(r); c++) {
        final Widget w = table.getWidget(r, c);
        if (w instanceof CommentPanel || w instanceof CommentEditorPanel) {
          if (c != column) {
            hasComment = true;
            break;
          }
        }
      }
      if (hasComment) {
        table.removeCell(r, column);
        span++;
      } else {
        break;
      }
    }
    if (span > 1) {
      table.getFlexCellFormatter().setRowSpan(row, column, span);
    }

    for (int r = row - 1; r > 0; r--) {
      if (getRowItem(r) instanceof CommentList) {
        continue;
      } else if (getRowItem(r) != null) {
        movePointerTo(r);
        break;
      }
    }

    updateCursor(newComment);
    return ed;
  }

  protected void insertRow(final int row) {
    table.insertRow(row);
    table.getCellFormatter().setStyleName(row, 0,
        Gerrit.RESOURCES.css().iconCell());
  }

  @Override
  protected void onOpenRow(final int row) {
    final Object item = getRowItem(row);
    if (item instanceof CommentList) {
      for (final CommentPanel p : ((CommentList) item).panels) {
        p.setOpen(!p.isOpen());
      }
    }
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  private void destroyEditor(final int row, final int col) {
    table.clearCell(row, col);
    final int span = table.getFlexCellFormatter().getRowSpan(row, col);
    boolean removeRow = true;
    final int nCells = table.getCellCount(row);
    for (int cell = 0; cell < nCells; cell++) {
      if (table.getWidget(row, cell) != null) {
        removeRow = false;
        break;
      }
    }
    if (removeRow) {
      destroyCommentRow(row);
    } else {
      destroyComment(row, col, span);
    }
  }

  protected void destroyCommentRow(int row) {
    for (int r = row - 1; 0 <= r; r--) {
      boolean data = false;
      for (int c = 0; c < table.getCellCount(r); c++) {
        data |= table.getWidget(r, c) != null;
        final int s = table.getFlexCellFormatter().getRowSpan(r, c) - 1;
        if (r + s == row) {
          table.getFlexCellFormatter().setRowSpan(r, c, s);
        }
      }
      if (!data) {
        break;
      }
    }
    table.removeRow(row);
  }

  private void destroyComment(int row, int col, int span) {
    table.getFlexCellFormatter().setStyleName(//
        row, col, Gerrit.RESOURCES.css().diffText());

    if (span != 1) {
      table.getFlexCellFormatter().setRowSpan(row, col, 1);
      for (int r = row + 1; r < row + span; r++) {
        table.insertCell(r, col);

        table.getFlexCellFormatter().setStyleName(//
            r, col, Gerrit.RESOURCES.css().diffText());
      }
    }
  }

  protected void bindComment(final int row, final int col,
      final PatchLineComment line, final boolean isLast, boolean expandComment) {
    if (line.getStatus() == PatchLineComment.Status.DRAFT) {
      final CommentEditorPanel plc = new CommentEditorPanel(line);
      plc.addFocusHandler(this);
      plc.addBlurHandler(this);
      table.setWidget(row, col, plc);
      styleLastCommentCell(row, col);

    } else {
      final AccountInfo author = FormatUtil.asInfo(accountCache.get(line.getAuthor()));
      final PublishedCommentPanel panel =
          new PublishedCommentPanel(author, line);
      panel.setOpen(expandComment);
      panel.addFocusHandler(this);
      panel.addBlurHandler(this);
      table.setWidget(row, col, panel);
      styleLastCommentCell(row, col);

      CommentList l = (CommentList) getRowItem(row);
      if (l == null) {
        l = new CommentList();
        setRowItem(row, l);
      }
      l.comments.add(line);
      l.panels.add(panel);
    }

    styleCommentRow(row);
  }

  @Override
  public void onFocus(FocusEvent event) {
    // when the comment panel gets focused (actually when a button inside the
    // comment panel gets focused) we have to unregister the key binding for
    // ENTER that expands/collapses the comment panel, if we don't do this the
    // focused button in the comment panel cannot be triggered by pressing ENTER
    // since ENTER would then be already consumed by this key binding
    if (regOpenByEnter != null) {
      regOpenByEnter.removeHandler();
      regOpenByEnter = null;
    }
  }

  @Override
  public void onBlur(BlurEvent event) {
    // when the comment panel gets blurred (actually when a button inside the
    // comment panel gets blurred) we have to re-register the key binding for
    // ENTER that expands/collapses the comment panel
    if (keysOpenByEnter != null && regOpenByEnter == null) {
      regOpenByEnter = GlobalKey.add(this, keysOpenByEnter);
    }
  }

  private void styleCommentRow(final int row) {
    final CellFormatter fmt = table.getCellFormatter();
    final Element iconCell = fmt.getElement(row, 0);
    UIObject.setStyleName(DOM.getParent(iconCell), Gerrit.RESOURCES.css()
        .commentHolder(), true);
  }

  private void styleLastCommentCell(final int row, final int col) {
    final CellFormatter fmt = table.getCellFormatter();
    fmt.removeStyleName(row - 1, col, //
        Gerrit.RESOURCES.css().commentPanelLast());
    fmt.setStyleName(row, col, Gerrit.RESOURCES.css().commentHolder());
    fmt.addStyleName(row, col, Gerrit.RESOURCES.css().commentPanelLast());
    if (!fmt.getStyleName(row, col - 1).contains(Gerrit.RESOURCES.css().commentHolder())) {
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().commentHolderLeftmost());
    }
  }

  protected static class CommentList {
    final List<PatchLineComment> comments = new ArrayList<PatchLineComment>();
    final List<PublishedCommentPanel> panels =
        new ArrayList<PublishedCommentPanel>();
  }

  public static class NoOpKeyCommand extends NeedsSignInKeyCommand {
    public NoOpKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
    }
  }

  public class InsertCommentCommand extends NeedsSignInKeyCommand {
    public InsertCommentCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      for (int row = getCurrentRow(); 0 <= row; row--) {
        final Object item = getRowItem(row);
        if (item instanceof PatchLine) {
          onInsertComment((PatchLine) item);
          return;
        } else if (item instanceof CommentList) {
          continue;
        } else {
          return;
        }
      }
    }
  }

  public class PublishCommentsKeyCommand extends NeedsSignInKeyCommand {
    public PublishCommentsKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final PatchSet.Id id = patchKey.getParentKey();
      Gerrit.display(Dispatcher.toPublish(id));
    }
  }

  public class PrevChunkKeyCmd extends KeyCommand {
    public PrevChunkKeyCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      moveToPrevChunk(getCurrentRow());
    }
  }

  public class NextChunkKeyCmd extends KeyCommand {
    public NextChunkKeyCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      moveToNextChunk(getCurrentRow());
    }
  }

  public class PrevCommentCmd extends KeyCommand {
    public PrevCommentCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      moveToPrevComment(getCurrentRow());
    }
  }

  public class NextCommentCmd extends KeyCommand {
    public NextCommentCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ensurePointerVisible();
      moveToNextComment(getCurrentRow());
    }
  }

  private class PublishedCommentPanel extends CommentPanel implements
      ClickHandler {
    final PatchLineComment comment;
    final Button reply;
    final Button replyDone;

    PublishedCommentPanel(final AccountInfo author, final PatchLineComment c) {
      super(author, c.getWrittenOn(), c.getMessage());
      this.comment = c;

      reply = new Button(PatchUtil.C.buttonReply());
      reply.addClickHandler(this);
      addButton(reply);

      replyDone = new Button(PatchUtil.C.buttonReplyDone());
      replyDone.addClickHandler(this);
      addButton(replyDone);
    }

    @Override
    public void onClick(final ClickEvent event) {
      if (Gerrit.isSignedIn()) {
        if (reply == event.getSource()) {
          createReplyEditor();
        } else if (replyDone == event.getSource()) {
          cannedReply(PatchUtil.C.cannedReplyDone());
        }

      } else {
        Gerrit.doSignIn(History.getToken());
      }
    }

    private void createReplyEditor() {
      final PatchLineComment newComment = newComment();
      newComment.setMessage("");
      findOrCreateEditor(newComment, true).setFocus(true);
    }

    private void cannedReply(String message) {
      final PatchLineComment newComment = newComment();
      newComment.setMessage(message);
      CommentEditorPanel p = findOrCreateEditor(newComment, false);
      if (p == null) {
        enableButtons(false);
        PatchUtil.DETAIL_SVC.saveDraft(newComment,
            new GerritCallback<PatchLineComment>() {
              @Override
              public void onSuccess(final PatchLineComment result) {
                enableButtons(true);
                notifyDraftDelta(1);
                findOrCreateEditor(result, true).setOpen(false);
              }

              @Override
              public void onFailure(Throwable caught) {
                enableButtons(true);
                super.onFailure(caught);
              }
            });
      } else {
        if (!p.isOpen()) {
          p.setOpen(true);
        }
        p.setFocus(true);
      }
    }

    private CommentEditorPanel findOrCreateEditor(
        PatchLineComment newComment, boolean create) {
      int row = rowOf(getElement());
      int column = columnOf(getElement());
      return findOrCreateCommentEditor(row + 1, column, newComment, create);
    }

    private PatchLineComment newComment() {
      PatchLineComment newComment =
          new PatchLineComment(new PatchLineComment.Key(comment.getKey()
              .getParentKey(), null), comment.getLine(), Gerrit
              .getUserAccount().getId(), comment.getKey().get());
      newComment.setSide(comment.getSide());
      return newComment;
    }
  }
}
