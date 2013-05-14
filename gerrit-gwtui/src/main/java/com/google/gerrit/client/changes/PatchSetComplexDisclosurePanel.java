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
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.download.DownloadPanel;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.AccountLinkPanel;
import com.google.gerrit.client.ui.ActionDialog;
import com.google.gerrit.client.ui.CherryPickDialog;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class PatchSetComplexDisclosurePanel extends ComplexDisclosurePanel
    implements OpenHandler<DisclosurePanel> {
  private static final int R_AUTHOR = 0;
  private static final int R_COMMITTER = 1;
  private static final int R_PARENTS = 2;
  private static final int R_DOWNLOAD = 3;
  private static final int R_CNT = 4;

  private final ChangeDetailCache detailCache;
  private final ChangeDetail changeDetail;
  private final PatchSet patchSet;
  private final FlowPanel body;

  private Grid infoTable;
  private Panel actionsPanel;
  private PatchTable patchTable;
  private final Set<ClickHandler> registeredClickHandler =  new HashSet<ClickHandler>();

  private PatchSet.Id diffBaseId;

  /**
   * Creates a closed complex disclosure panel for a patch set.
   * The patch set details are loaded when the complex disclosure panel is opened.
   */
  public PatchSetComplexDisclosurePanel(final PatchSet ps, boolean isOpen,
      boolean hasDraftComments) {
    super(Util.M.patchSetHeader(ps.getPatchSetId()), isOpen);
    detailCache = ChangeCache.get(ps.getId().getParentKey()).getChangeDetailCache();
    changeDetail = detailCache.get();
    patchSet = ps;

    body = new FlowPanel();
    setContent(body);

    if (hasDraftComments) {
      final Image draftComments = new Image(Gerrit.RESOURCES.draftComments());
      draftComments.setTitle(Util.C.patchSetWithDraftCommentsToolTip());
      getHeader().add(draftComments);
    }

    final GitwebLink gw = Gerrit.getGitwebLink();
    final InlineLabel revtxt = new InlineLabel(ps.getRevision().get() + " ");
    revtxt.addStyleName(Gerrit.RESOURCES.css().patchSetRevision());
    getHeader().add(revtxt);
    if (gw != null && gw.canLink(ps)) {
      final Anchor revlink =
          new Anchor(gw.getLinkName(), false, gw.toRevision(changeDetail.getChange()
              .getProject(), ps));
      revlink.addStyleName(Gerrit.RESOURCES.css().patchSetLink());
      getHeader().add(revlink);
    }

    if (ps.isDraft()) {
      final InlineLabel draftLabel = new InlineLabel(Util.C.draftPatchSetLabel());
      draftLabel.addStyleName(Gerrit.RESOURCES.css().patchSetRevision());
      getHeader().add(draftLabel);
    }

    if (isOpen) {
      ensureLoaded(changeDetail.getCurrentPatchSetDetail());
    } else {
      addOpenHandler(this);
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
    loadInfoTable(detail);
    loadActionPanel(detail);
    loadPatchTable(detail);
  }

  public void loadInfoTable(final PatchSetDetail detail) {
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
    displayDownload();

    body.add(infoTable);
  }

  public void loadActionPanel(final PatchSetDetail detail) {
    if (!patchSet.getId().equals(diffBaseId)) {
      actionsPanel = new FlowPanel();
      actionsPanel.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
      actionsPanel.setVisible(true);
      if (Gerrit.isSignedIn()) {
        if (changeDetail.canEdit()) {
          populateReviewAction();
          if (changeDetail.isCurrentPatchSet(detail)) {
            populateActions(detail);
          }
          populateCommands(detail);
        }
        if (detail.getPatchSet().isDraft()) {
          if (changeDetail.canPublish()) {
            populatePublishAction();
          }
          if (changeDetail.canDeleteDraft()
              && changeDetail.getPatchSets().size() > 1) {
            populateDeleteDraftPatchSetAction();
          }
        }
      }
      body.add(actionsPanel);
    }
  }

  public void loadPatchTable(final PatchSetDetail detail) {
    if (!patchSet.getId().equals(diffBaseId)) {
      patchTable = new PatchTable();
      patchTable.setSavePointerId("PatchTable " + patchSet.getId());
      patchTable.display(diffBaseId, detail);
      for (ClickHandler clickHandler : registeredClickHandler) {
        patchTable.addClickHandler(clickHandler);
      }
      patchTable.setRegisterKeys(true);
      setActive(true);
      body.add(patchTable);
    }
  }

  public class ChangeDownloadPanel extends DownloadPanel {
    public ChangeDownloadPanel(String project, String ref, boolean allowAnonymous) {
      super(project, ref, allowAnonymous);
    }

    @Override
    public void populateDownloadCommandLinks() {
      // This site prefers usage of the 'repo' tool, so suggest
      // that for easy fetch.
      //
      if (allowedSchemes.contains(DownloadScheme.REPO_DOWNLOAD)) {
        commands.add(cmdLinkfactory.new RepoCommandLink(projectName,
            changeDetail.getChange().getChangeId() + "/"
            + patchSet.getPatchSetId()));
      }

      if (!urls.isEmpty()) {
        if (allowedCommands.contains(DownloadCommand.CHECKOUT)
            || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
          commands.add(cmdLinkfactory.new CheckoutCommandLink());
        }
        if (allowedCommands.contains(DownloadCommand.PULL)
            || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
          commands.add(cmdLinkfactory.new PullCommandLink());
        }
        if (allowedCommands.contains(DownloadCommand.CHERRY_PICK)
            || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
          commands.add(cmdLinkfactory.new CherryPickCommandLink());
        }
        if (allowedCommands.contains(DownloadCommand.FORMAT_PATCH)
            || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
          commands.add(cmdLinkfactory.new FormatPatchCommandLink());
        }
      }
    }
  }

  private void displayDownload() {
    ChangeDownloadPanel dp = new ChangeDownloadPanel(
      changeDetail.getChange().getProject().get(),
      patchSet.getRefName(),
      changeDetail.isAllowsAnonymous());

    infoTable.setWidget(R_DOWNLOAD, 1, dp);
  }

  private void displayUserIdentity(final int row, final UserIdentity who) {
    if (who == null) {
      infoTable.clearCell(row, 1);
      return;
    }

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().patchSetUserIdentity());
    if (who.getName() != null) {
      if (who.getAccount() != null) {
        fp.add(new AccountLinkPanel(who));
      } else {
        final InlineLabel lbl = new InlineLabel(who.getName());
        lbl.setStyleName(Gerrit.RESOURCES.css().accountName());
        fp.add(lbl);
      }
    }
    if (who.getEmail() != null) {
      fp.add(new InlineLabel("<" + who.getEmail() + ">"));
    }
    if (who.getDate() != null) {
      fp.add(new InlineLabel(FormatUtil.mediumFormat(who.getDate())));
    }
    infoTable.setWidget(row, 1, fp);
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
      parentsTable.setWidget(row, 1,
          new InlineLabel(Util.cropSubject(parent.shortMessage)));
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
          ChangeApi.submit(
              patchSet.getId().getParentKey().get(),
              patchSet.getRevision().get(),
              new GerritCallback<SubmitInfo>() {
                  public void onSuccess(SubmitInfo result) {
                    redisplay();
                  }

                  public void onFailure(Throwable err) {
                    if (SubmitFailureDialog.isConflict(err)) {
                      new SubmitFailureDialog(err.getMessage()).center();
                      redisplay();
                    } else {
                      b.setEnabled(true);
                      super.onFailure(err);
                    }
                  }

                  private void redisplay() {
                    Gerrit.display(
                        PageLinks.toChange(patchSet.getId().getParentKey()),
                        new ChangeScreen(patchSet.getId().getParentKey()));
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
          new ActionDialog(b, true, Util.C.revertChangeTitle(),
              Util.C.headingRevertMessage()) {
            {
              sendButton.setText(Util.C.buttonRevertChangeSend());
              message.setText(Util.M.revertChangeDefaultMessage(
                  detail.getInfo().getSubject(),
                  detail.getPatchSet().getRevision().get())
              );
            }

            @Override
            public void onSend() {
              ChangeApi.revert(changeDetail.getChange().getChangeId(),
                  getMessageText(), new GerritCallback<ChangeInfo>() {
                    @Override
                    public void onSuccess(ChangeInfo result) {
                      sent = true;
                      Gerrit.display(PageLinks.toChange(new Change.Id(result
                          ._number())));
                      hide();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                      enableButtons(true);
                      super.onFailure(caught);
                    }
                  });
            }
          }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canCherryPick()) {
      final Button b = new Button(Util.C.buttonCherryPickChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CherryPickDialog(b, changeDetail.getChange().getProject()) {
            {
              sendButton.setText(Util.C.buttonCherryPickChangeSend());
              message.setText(Util.M.cherryPickedChangeDefaultMessage(
                  detail.getInfo().getMessage().trim(),
                  detail.getPatchSet().getRevision().get()));
            }

            @Override
            public void onSend() {
              ChangeApi.cherrypick(changeDetail.getChange().getChangeId(),
                  patchSet.getRevision().get(),
                  getDestinationBranch(),
                  getMessageText(),
                  new GerritCallback<ChangeInfo>() {
                    @Override
                    public void onSuccess(ChangeInfo result) {
                      sent = true;
                      Gerrit.display(PageLinks.toChange(new Change.Id(result
                          ._number())));
                      hide();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                      enableButtons(true);
                      super.onFailure(caught);
                    }
                  });
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
          new ActionDialog(b, false, Util.C.abandonChangeTitle(),
              Util.C.headingAbandonMessage()) {
            {
              sendButton.setText(Util.C.buttonAbandonChangeSend());
            }

            @Override
            public void onSend() {
              // TODO: once the other users of ActionDialog have converted to
              // REST APIs, we can use createCallback() rather than providing
              // them directly.
              ChangeApi.abandon(changeDetail.getChange().getChangeId(),
                  getMessageText(), new GerritCallback<ChangeInfo>() {
                    @Override
                    public void onSuccess(ChangeInfo result) {
                      sent = true;
                      Gerrit.display(PageLinks.toChange(new Change.Id(result
                          ._number())));
                      hide();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                      enableButtons(true);
                      super.onFailure(caught);
                    }
                  });
            }
          }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.getChange().getStatus() == Change.Status.DRAFT
        && changeDetail.canDeleteDraft()) {
      final Button b = new Button(Util.C.buttonDeleteDraftChange());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.MANAGE_SVC.deleteDraftChange(patchSet.getId(),
              new GerritCallback<VoidResult>() {
                public void onSuccess(VoidResult result) {
                  Gerrit.display(PageLinks.MINE);
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

    if (changeDetail.canRestore()) {
      final Button b = new Button(Util.C.buttonRestoreChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new ActionDialog(b, false, Util.C.restoreChangeTitle(),
              Util.C.headingRestoreMessage()) {
            {
              sendButton.setText(Util.C.buttonRestoreChangeSend());
            }

            @Override
            public void onSend() {
              ChangeApi.restore(changeDetail.getChange().getChangeId(),
                  getMessageText(), new GerritCallback<ChangeInfo>() {
                    @Override
                    public void onSuccess(ChangeInfo result) {
                      sent = true;
                      Gerrit.display(PageLinks.toChange(new Change.Id(result
                          ._number())));
                      hide();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                      enableButtons(true);
                      super.onFailure(caught);
                    }
                  });
            }
          }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canRebase()) {
      final Button b = new Button(Util.C.buttonRebaseChange());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.MANAGE_SVC.rebaseChange(patchSet.getId(),
              new ChangeDetailCache.GerritWidgetCallback(b));
        }
      });
      actionsPanel.add(b);
    }
  }

  private void populateCommands(final PatchSetDetail detail) {
    for (final UiCommandDetail cmd : detail.getCommands()) {
      final Button b = new Button(cmd.label);
      b.setEnabled(cmd.enabled);
      b.setTitle(cmd.title);
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          AsyncCallback<NativeString> cb =
              new AsyncCallback<NativeString>() {
                @Override
                public void onFailure(Throwable caught) {
                  b.setEnabled(true);
                  new ErrorDialog(caught).center();
                }

                @Override
                public void onSuccess(NativeString msg) {
                  b.setEnabled(true);
                  if (!msg.toString().isEmpty()) {
                    Window.alert(msg.asString());
                  }
                }
              };
          RestApi api = ChangeApi.revision(patchSet.getId()).view(cmd.id);
          if ("PUT".equalsIgnoreCase(cmd.method)) {
            api.put(JavaScriptObject.createObject(), cb);
          } else if ("DELETE".equalsIgnoreCase(cmd.method)) {
            api.delete(cb);
          } else {
            api.post(JavaScriptObject.createObject(), cb);
          }
        }
      });
      actionsPanel.add(b);
    }
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

  private void populatePublishAction() {
    final Button b = new Button(Util.C.buttonPublishPatchSet());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        b.setEnabled(false);
        Util.MANAGE_SVC.publish(patchSet.getId(),
            new ChangeDetailCache.GerritWidgetCallback(b));
      }
    });
    actionsPanel.add(b);
  }

  private void populateDeleteDraftPatchSetAction() {
    final Button b = new Button(Util.C.buttonDeleteDraftPatchSet());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        b.setEnabled(false);
        PatchUtil.DETAIL_SVC.deleteDraftPatchSet(patchSet.getId(),
            new ChangeDetailCache.GerritWidgetCallback(b) {
              public void onSuccess(final ChangeDetail result) {
                if (result != null) {
                  detailCache.set(result);
                } else {
                  Gerrit.display(PageLinks.MINE);
                }
              }
            });
      }
    });
    actionsPanel.add(b);
  }

  public void refresh() {
    if (patchSet.getId().equals(diffBaseId)) {
      if (patchTable != null) {
        patchTable.setVisible(false);
      }
      if (actionsPanel != null) {
        actionsPanel.setVisible(false);
      }
    } else {
      if (patchTable != null) {
        if (patchTable.getBase() == null && diffBaseId == null
            || patchTable.getBase() != null
            && patchTable.getBase().equals(diffBaseId)) {
          actionsPanel.setVisible(true);
          patchTable.setVisible(true);
          return;
        }
      }

      AccountDiffPreference diffPrefs;
      if (patchTable == null) {
        diffPrefs = new ListenableAccountDiffPreference().get();
      } else {
        diffPrefs = patchTable.getPreferences().get();
        patchTable.setVisible(false);
      }

      Util.DETAIL_SVC.patchSetDetail2(diffBaseId, patchSet.getId(), diffPrefs,
          new GerritCallback<PatchSetDetail>() {
            @Override
            public void onSuccess(PatchSetDetail result) {
              if (actionsPanel != null) {
                actionsPanel.setVisible(true);
              } else {
                loadActionPanel(result);
              }
              loadPatchTable(result);
            }
          });
    }
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
              loadInfoTable(result);
              loadActionPanel(result);
            }
          });
    }
  }

  private void initRow(final int row, final String name) {
    infoTable.setText(row, 0, name);
    infoTable.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
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
}
