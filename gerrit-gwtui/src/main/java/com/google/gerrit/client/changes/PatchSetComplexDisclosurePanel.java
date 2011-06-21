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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.GitwebLink;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class PatchSetComplexDisclosurePanel extends CommonComplexDisclosurePanel {
  private static final int R_AUTHOR = 0;
  private static final int R_COMMITTER = 1;
  private static final int R_PARENTS = 2;
  private static final int R_DOWNLOAD = 3;
  private static final int R_CNT = 4;

  private final ChangeScreen changeScreen;
  private final ChangeDetail changeDetail;
  private final PatchSet patchSet;
  private final FlowPanel body;

  private Panel actionsPanel;
  private PatchTable patchTable;
  private final Set<ClickHandler> registeredClickHandler =  new HashSet<ClickHandler>();

  private PatchSet.Id diffBaseId;

  /**
   * Creates a closed complex disclosure panel for a patch set.
   * The patch set details are loaded when the complex disclosure panel is opened.
   */
  PatchSetComplexDisclosurePanel(final ChangeScreen parent, final ChangeDetail detail,
      final PatchSet ps) {
    this(parent, detail, ps, false);
    addOpenHandler(this);
  }

  /**
   * Creates an open complex disclosure panel for a patch set.
   */
  PatchSetComplexDisclosurePanel(final ChangeScreen parent, final ChangeDetail detail,
      final PatchSetDetail psd) {
    this(parent, detail, psd.getPatchSet(), true);
    ensureLoaded(psd);
  }

  private PatchSetComplexDisclosurePanel(final ChangeScreen parent, final ChangeDetail detail,
      final PatchSet ps, boolean isOpen) {
    super(Util.M.patchSetHeader(ps.getPatchSetId()), isOpen);
    changeScreen = parent;
    changeDetail = detail;
    patchSet = ps;
    body = new FlowPanel();
    setContent(body);

    final GitwebLink gw = Gerrit.getConfig().getGitwebLink();

    final InlineLabel revtxt = new InlineLabel(ps.getRevision().get() + " ");
    revtxt.addStyleName(Gerrit.RESOURCES.css().patchSetRevision());
    getHeader().add(revtxt);
    if (gw != null) {
      final Anchor revlink =
          new Anchor("(gitweb)", false, gw.toRevision(detail.getChange()
              .getProject(), ps));
      revlink.addStyleName(Gerrit.RESOURCES.css().patchSetLink());
      getHeader().add(revlink);
    }
  }

  public void setDiffBaseId(PatchSet.Id diffBaseId) {
    this.diffBaseId = diffBaseId;
  }

  /**
   * Display the table showing the Author, Committer and Download links,
   * followed by the action buttons.
   */
  public void ensureLoaded(final PatchSetDetail detail) {
    infoTable = new Grid(R_CNT, 2);
    infoTable.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    infoTable.addStyleName(Gerrit.RESOURCES.css().patchSetInfoBlock());

    initRow(R_AUTHOR, Util.C.patchSetInfoAuthor());
    initRow(R_COMMITTER, Util.C.patchSetInfoCommitter());
    initRow(R_PARENTS, Util.C.patchSetInfoParents());
    initRow(R_DOWNLOAD, Util.C.patchSetInfoDownload());

    final CellFormatter itfmt = infoTable.getCellFormatter();
    itfmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(R_CNT - 1, 0, Gerrit.RESOURCES.css().bottomheader());
    itfmt.addStyleName(R_AUTHOR, 1, Gerrit.RESOURCES.css().useridentity());
    itfmt.addStyleName(R_COMMITTER, 1, Gerrit.RESOURCES.css().useridentity());
    itfmt.addStyleName(R_DOWNLOAD, 1, Gerrit.RESOURCES.css()
        .downloadLinkListCell());

    final PatchSetInfo info = detail.getInfo();
    displayUserIdentity(R_AUTHOR, info.getAuthor());
    displayUserIdentity(R_COMMITTER, info.getCommitter());
    displayParents(info.getParents());
    displayDownload(changeDetail.getChange().getProject(),
        changeDetail.isAllowsAnonymous(),patchSet.getRefName(),
        changeDetail.getChange().getChangeId(), patchSet.getPatchSetId(),
        R_DOWNLOAD);

    body.add(infoTable);

    if (!patchSet.getId().equals(diffBaseId)) {
      patchTable = new PatchTable();
      patchTable.setSavePointerId("PatchTable " + patchSet.getId());
      patchTable.setPatchSetIdToCompareWith(diffBaseId);
      patchTable.display(detail);

      actionsPanel = new FlowPanel();
      actionsPanel.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
      body.add(actionsPanel);
      if (Gerrit.isSignedIn()) {
        populateReviewAction();
        if (changeDetail.isCurrentPatchSet(detail)) {
          populateActions(detail);
        }
      }
      populateDiffAllActions(detail);
      body.add(patchTable);

      for(ClickHandler clickHandler : registeredClickHandler) {
        patchTable.addClickHandler(clickHandler);
      }
    }
  }

  private void displayParents(final List<PatchSetInfo.ParentInfo> parents) {
    if (parents.size() == 0) {
      infoTable.setWidget(R_PARENTS, 1, new InlineLabel(Util.C.initialCommit()));
      return;
    }
    final Grid parentsTable = new Grid(parents.size(), 2);

    parentsTable.setStyleName(Gerrit.RESOURCES.css().parentsTable());
    parentsTable.addStyleName(Gerrit.RESOURCES.css().noborder());
    final CellFormatter ptfmt = parentsTable.getCellFormatter();
    int row = 0;
    for (PatchSetInfo.ParentInfo parent : parents) {
      parentsTable.setWidget(row, 0, new InlineLabel(parent.id.get()));
      ptfmt.addStyleName(row, 0, Gerrit.RESOURCES.css().noborder());
      ptfmt.addStyleName(row, 0, Gerrit.RESOURCES.css().monospace());
      parentsTable.setWidget(row, 1, new InlineLabel(parent.shortMessage));
      ptfmt.addStyleName(row, 1, Gerrit.RESOURCES.css().noborder());
      row++;
    }
    infoTable.setWidget(R_PARENTS, 1, parentsTable);
  }

  private void populateActions(final PatchSetDetail detail) {
    final boolean isOpen = changeDetail.getChange().getStatus().isOpen();

    if (isOpen && changeDetail.canSubmit()) {
      final Button b =
          new Button(Util.M
              .submitPatchSet(detail.getPatchSet().getPatchSetId()));
      if (Gerrit.getConfig().testChangeMerge()) {
        b.setEnabled(changeDetail.getChange().isMergeable());
      }

      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.MANAGE_SVC.submit(patchSet.getId(),
              new GerritCallback<ChangeDetail>() {
                public void onSuccess(ChangeDetail result) {
                  onSubmitResult(result);
                }

                @Override
                public void onFailure(Throwable caught) {
                  b.setEnabled(true);
                  super.onFailure(caught);
                }
              });
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canRevert()) {
      final Button b = new Button(Util.C.buttonRevertChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<ChangeDetail>(patchSet.getId(), createCommentedCallback(b),
              Util.C.revertChangeTitle(), Util.C.headingRevertMessage(),
              Util.C.buttonRevertChangeSend(), Util.C.buttonRevertChangeCancel(),
              Gerrit.RESOURCES.css().revertChangeDialog(), Gerrit.RESOURCES.css().revertMessage(),
              Util.M.revertChangeDefaultMessage(detail.getInfo().getSubject(), detail.getPatchSet().getRevision().get())) {
                public void onSend() {
                  Util.MANAGE_SVC.revertChange(getPatchSetId() , getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canAbandon()) {
      final Button b = new Button(Util.C.buttonAbandonChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<ChangeDetail>(patchSet.getId(), createCommentedCallback(b),
              Util.C.abandonChangeTitle(), Util.C.headingAbandonMessage(),
              Util.C.buttonAbandonChangeSend(), Util.C.buttonAbandonChangeCancel(),
              Gerrit.RESOURCES.css().abandonChangeDialog(), Gerrit.RESOURCES.css().abandonMessage()) {
                public void onSend() {
                  Util.MANAGE_SVC.abandonChange(getPatchSetId() , getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canRestore()) {
      final Button b = new Button(Util.C.buttonRestoreChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<ChangeDetail>(patchSet.getId(), createCommentedCallback(b),
              Util.C.restoreChangeTitle(), Util.C.headingRestoreMessage(),
              Util.C.buttonRestoreChangeSend(), Util.C.buttonRestoreChangeCancel(),
              Gerrit.RESOURCES.css().abandonChangeDialog(), Gerrit.RESOURCES.css().abandonMessage()) {
                public void onSend() {
                  Util.MANAGE_SVC.restoreChange(getPatchSetId(), getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }
  }

  private void populateDiffAllActions(final PatchSetDetail detail) {
    final Button diffAllSideBySide = new Button(Util.C.buttonDiffAllSideBySide());
    diffAllSideBySide.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        for (Patch p : detail.getPatches()) {
          Window.open(Window.Location.getPath() + "#"
              + Dispatcher.toPatchSideBySide(p.getKey()), "_blank", null);
        }
      }
    });
    actionsPanel.add(diffAllSideBySide);

    final Button diffAllUnified = new Button(Util.C.buttonDiffAllUnified());
    diffAllUnified.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        for (Patch p : detail.getPatches()) {
          Window.open(Window.Location.getPath() + "#"
              + Dispatcher.toPatchUnified(p.getKey()), "_blank", null);
        }
      }
    });
    actionsPanel.add(diffAllUnified);
  }

  private void populateReviewAction() {
    final Button b = new Button(Util.C.buttonReview());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        Gerrit.display(Dispatcher.toPublish(patchSet.getId()));
      }
    });
    actionsPanel.add(b);
  }

  public void refresh() {
    AccountDiffPreference diffPrefs;
    if (patchTable == null) {
      diffPrefs = new ListenableAccountDiffPreference().get();
    } else {
      diffPrefs = patchTable.getPreferences().get();
    }

    Util.DETAIL_SVC.patchSetDetail2(diffBaseId, patchSet.getId(), diffPrefs,
        new GerritCallback<PatchSetDetail>() {
          @Override
          public void onSuccess(PatchSetDetail result) {

            if (patchSet.getId().equals(diffBaseId)) {
              patchTable.setVisible(false);
              actionsPanel.setVisible(false);
            } else {

              if (patchTable != null) {
                patchTable.removeFromParent();
              }
              patchTable = new PatchTable();
              patchTable.setPatchSetIdToCompareWith(diffBaseId);
              patchTable.display(result);
              body.add(patchTable);

              for (ClickHandler clickHandler : registeredClickHandler) {
                patchTable.addClickHandler(clickHandler);
              }
            }
          }
        });
  }

  @Override
  public void onOpen(final OpenEvent<DisclosurePanel> event) {
    if (infoTable == null) {
      AccountDiffPreference diffPrefs;
      if (diffBaseId == null) {
        diffPrefs = null;
      } else {
        diffPrefs = new ListenableAccountDiffPreference().get();
      }

      Util.DETAIL_SVC.patchSetDetail2(diffBaseId, patchSet.getId(), diffPrefs,
          new GerritCallback<PatchSetDetail>() {
            public void onSuccess(final PatchSetDetail result) {
              ensureLoaded(result);
              patchTable.setRegisterKeys(true);
            }
          });
    }
  }

  private void onSubmitResult(final ChangeDetail result) {
    if (result.getChange().getStatus() == Change.Status.NEW) {
      // The submit failed. Try to locate the message and display
      // it to the user, it should be the last one created by Gerrit.
      //
      ChangeMessage msg = null;
      if (result.getMessages() != null && result.getMessages().size() > 0) {
        for (int i = result.getMessages().size() - 1; i >= 0; i--) {
          if (result.getMessages().get(i).getAuthor() == null) {
            msg = result.getMessages().get(i);
            break;
          }
        }
      }

      if (msg != null) {
        new SubmitFailureDialog(result, msg).center();
      }
    }
    changeScreen.update(result);
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  /**
   * Adds a click handler to the patch table.
   * If the patch table is not yet initialized it is guaranteed that the click handler
   * is added to the patch table after initialization.
   */
  public void addClickHandler(final ClickHandler clickHandler) {
    registeredClickHandler.add(clickHandler);
    if (patchTable != null) {
      patchTable.addClickHandler(clickHandler);
    }
  }

  /** Activates / Deactivates the key navigation and the highlighting of the current row for the patch table */
  public void setActive(boolean active) {
    if (patchTable != null) {
      patchTable.setActive(active);
    }
  }

  private AsyncCallback<ChangeDetail> createCommentedCallback(final Button b) {
    return new AsyncCallback<ChangeDetail>() {
      public void onSuccess(ChangeDetail result) {
        changeScreen.update(result);
      }

      public void onFailure(Throwable caught) {
        b.setEnabled(true);
      }
    };
  }
}
