// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.client.changes.ChangeTable.ApprovalViewType;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ChangeSetDetail;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.ArrayList;
import java.util.List;

class ChangeSetComplexDisclosurePanel extends CommonComplexDisclosurePanel {
  private static final int R_AUTHOR = 0;
  private static final int R_DOWNLOAD = 1;
  private static final int R_CNT = 2;

  private final TopicScreen topicScreen;
  private final TopicDetail topicDetail;
  private final ChangeSet changeSet;
  private final FlowPanel body;

  private Panel actionsPanel;
  private ChangeTable changeTable;
  protected Hyperlink prev;
  protected Hyperlink next;

  private ChangeSet.Id diffBaseId;

  /**
   * Creates a closed complex disclosure panel for a change set.
   * The change set details are loaded when the complex disclosure panel is opened.
   */
  ChangeSetComplexDisclosurePanel(final TopicScreen parent, final TopicDetail detail,
      final ChangeSet cs) {
    this(parent, detail, cs, false);
    addOpenHandler(this);
  }

  /**
   * Creates an open complex disclosure panel for a patch set.
   */
  ChangeSetComplexDisclosurePanel(final TopicScreen parent, final TopicDetail detail,
      final ChangeSetDetail csd) {
    this(parent, detail, csd.getChangeSet(), true);
    ensureLoaded(csd);
  }

  private ChangeSetComplexDisclosurePanel(final TopicScreen parent, final TopicDetail detail,
      final ChangeSet cs, boolean isOpen) {
    super(Util.TM.changeSetHeader(cs.getChangeSetId()), isOpen);
    topicScreen = parent;
    topicDetail = detail;
    changeSet = cs;
    body = new FlowPanel();
    setContent(body);

    // TODO Gitweb support
  }

  public void setDiffBaseId(ChangeSet.Id diffBaseId) {
    this.diffBaseId = diffBaseId;
  }

  /**
   * Display the table showing the Author and Download links,
   * followed by the action buttons.
   */
  public void ensureLoaded(final ChangeSetDetail detail) {
    infoTable = new Grid(R_CNT, 2);
    infoTable.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    infoTable.addStyleName(Gerrit.RESOURCES.css().patchSetInfoBlock());

    initRow(R_AUTHOR, Util.TC.changeSetInfoAuthor());
    initRow(R_DOWNLOAD, Util.TC.changeSetInfoDownload());

    final CellFormatter itfmt = infoTable.getCellFormatter();
    itfmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(R_CNT - 1, 0, Gerrit.RESOURCES.css().bottomheader());
    itfmt.addStyleName(R_AUTHOR, 1, Gerrit.RESOURCES.css().useridentity());
    itfmt.addStyleName(R_DOWNLOAD, 1, Gerrit.RESOURCES.css()
        .downloadLinkListCell());

    final ChangeSetInfo info = detail.getInfo();
    displayUserIdentity(R_AUTHOR, info.getAuthor());
    displayDownload(topicDetail.getTopic().getProject(), topicDetail.isAllowsAnonymous(),
        changeSet.getRefName(), topicDetail.getTopic().getTopicId(),
        changeSet.getChangeSetId(), R_DOWNLOAD);

    body.add(infoTable);

    displayChangeTable(detail);
    body.add(changeTable);

    if (!changeSet.getId().equals(diffBaseId)) {
      actionsPanel = new FlowPanel();
      actionsPanel.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
      body.add(actionsPanel);
      if (Gerrit.isSignedIn()) {
        populateReviewAction();
        if (topicDetail.isCurrentChangeSet(detail)) {
          populateActions(detail);
        }
        // TODO populateDiffAllActions(detail);
        // The diff will have to access the ChangeSets in order to
        // the PatchSet.Id of the most recent Change, and also the
        // older change in the diffBaseId
      }
    }
  }

  private void displayChangeTable(ChangeSetDetail detail) {
    prev = new Hyperlink(Util.TC.changeSetChangeListPrev(), true, "");
    prev.setVisible(false);

    next = new Hyperlink(Util.TC.changeSetChangeListNext(), true, "");
    next.setVisible(false);

    List<Change> currChanges = detail.getChanges();
    List<ChangeInfo> cil = new ArrayList<ChangeInfo>();

    for (Change ch : currChanges) cil.add(new ChangeInfo(ch));

    changeTable = new ChangeTable(true);

    changeTable.setAccountInfoCache(topicDetail.getAccounts());
    ChangeTable.Section section = new ChangeTable.Section(null, ApprovalViewType.STRONGEST, null);
    if (!cil.isEmpty()) {
      changeTable.addSection(section);
      section.display(cil);
      changeTable.finishDisplay();
    }
    changeTable.setRegisterKeys(true);
  }

  private void populateActions(final ChangeSetDetail detail) {
    final boolean isOpen = topicDetail.getTopic().getStatus().isOpen();

    if (isOpen && topicDetail.canSubmit()) {
      final Button b =
          new Button(Util.TM
              .submitChangeSet(detail.getChangeSet().getChangeSetId()));
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.T_MANAGE_SVC.submit(changeSet.getId(),
              new GerritCallback<TopicDetail>() {
                public void onSuccess(TopicDetail result) {
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

    if (topicDetail.canRevert()) {
      final Button b = new Button(Util.TC.buttonRevertTopicBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<TopicDetail>(changeSet.getId(), createCommentedCallback(b),
              Util.TC.revertTopicTitle(), Util.TC.headingRevertMessage(),
              Util.TC.buttonRevertTopicSend(), Util.TC.buttonRevertTopicCancel(),
              Gerrit.RESOURCES.css().revertChangeDialog(), Gerrit.RESOURCES.css().revertMessage(),
              Util.TM.revertTopicDefaultMessage(topicDetail.getTopic().getTopic(),
                  topicDetail.getTopic().getId().get(), changeSet.getId().get())) {
                public void onSend() {
                  Util.T_MANAGE_SVC.revertTopic(getChangeSetId() , getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (topicDetail.canAbandon()) {
      final Button b = new Button(Util.TC.buttonAbandonTopicBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<TopicDetail>(changeSet.getId(), createCommentedCallback(b),
              Util.TC.abandonTopicTitle(), Util.TC.headingAbandonMessage(),
              Util.TC.buttonAbandonTopicSend(), Util.TC.buttonAbandonTopicCancel(),
              Gerrit.RESOURCES.css().abandonChangeDialog(), Gerrit.RESOURCES.css().abandonMessage()) {
                public void onSend() {
                  Util.T_MANAGE_SVC.abandonTopic(getChangeSetId() , getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }

    if (topicDetail.canRestore()) {
      final Button b = new Button(Util.TC.buttonRestoreTopicBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          new CommentedChangeActionDialog<TopicDetail>(changeSet.getId(), createCommentedCallback(b),
              Util.TC.restoreTopicTitle(), Util.TC.headingRestoreMessage(),
              Util.TC.buttonRestoreTopicSend(), Util.TC.buttonRestoreTopicCancel(),
              Gerrit.RESOURCES.css().abandonChangeDialog(), Gerrit.RESOURCES.css().abandonMessage()) {
                public void onSend() {
                  Util.T_MANAGE_SVC.restoreTopic(getChangeSetId(), getMessageText(), createCallback());
                }
              }.center();
        }
      });
      actionsPanel.add(b);
    }
  }

  private void populateReviewAction() {
    final Button b = new Button(Util.TC.buttonReview());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        Gerrit.display(Dispatcher.toPublish(changeSet.getId()));
      }
    });
    actionsPanel.add(b);
  }

  public void refresh() {
    Util.T_DETAIL_SVC.changeSetDetail(changeSet.getId(),
        new GerritCallback<ChangeSetDetail>() {
          @Override
          public void onSuccess(ChangeSetDetail result) {

            if (changeSet.getId().equals(diffBaseId)) {
              changeTable.setVisible(false);
              actionsPanel.setVisible(false);
            } else {

              if (changeTable != null) {
                changeTable.removeFromParent();
              }
              displayChangeTable(result);
              body.add(changeTable);
            }
          }
        });
  }

  @Override
  public void onOpen(final OpenEvent<DisclosurePanel> event) {
    if (infoTable == null) {
      Util.T_DETAIL_SVC.changeSetDetail(changeSet.getId(),
          new GerritCallback<ChangeSetDetail>() {
            public void onSuccess(final ChangeSetDetail result) {
              ensureLoaded(result);
            }
          });
    }
  }

  private void onSubmitResult(final TopicDetail result) {
    if (result.getTopic().getStatus() == Status.NEW) {
      // The submit failed. Try to locate the message and display
      // it to the user, it should be the last one created by Gerrit.
      //
      TopicMessage msg = null;
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
    topicScreen.update(result);
  }

  public ChangeSet getChangeSet() {
    return changeSet;
  }

  /** Activates / Deactivates the key navigation and the highlighting of the current row for the change table */
  public void setActive(boolean active) {
    if (changeTable != null) {
      changeTable.setRegisterKeys(active);
    }
  }

  private AsyncCallback<TopicDetail> createCommentedCallback(final Button b) {
    return new AsyncCallback<TopicDetail>() {
      public void onSuccess(TopicDetail result) {
        topicScreen.update(result);
      }

      public void onFailure(Throwable caught) {
        b.setEnabled(true);
      }
    };
  }
}
