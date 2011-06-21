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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.client.ui.ExpandAllCommand;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.data.TopicInfo;
import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.sql.Timestamp;
import java.util.List;


public class TopicScreen extends Screen {
  private final Topic.Id topicId;
  private final ChangeSet.Id openChangeSetId;

  private TopicDescriptionBlock descriptionBlock;
  private ApprovalTable approvals;

  private IncludedInTable includedInTable;
  private DisclosurePanel includedInPanel;
  private DisclosurePanel dependenciesPanel;
  private ChangeTable dependencies;
  private ChangeTable.Section dependsOn;
  private ChangeTable.Section neededBy;

  private ChangeSetsBlock changeSetsBlock;

  private Panel comments;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;

  private ListBox changesList;

  /**
   * The topic id for which the old version history is valid.
   */
  private static Topic.Id currentTopicId;

  /**
   * Which change set id is the diff base.
   */
  private static ChangeSet.Id diffBaseId;

  public TopicScreen(final Topic.Id toShow) {
    topicId = toShow;
    openChangeSetId = null;

    // If we have any diff stored, make sure they are applicable to the
    // current change, discard them otherwise.
    //
    if (currentTopicId != null && !currentTopicId.equals(toShow)) {
      diffBaseId = null;
    }
    currentTopicId = toShow;
  }

  public TopicScreen(final ChangeSet.Id toShow) {
    topicId = toShow.getParentKey();
    openChangeSetId = toShow;
  }

  public TopicScreen(final TopicInfo t) {
    this(t.getId());
  }

  @Override
  public void onSignOut() {
    super.onSignOut();
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    refresh();
  }

  @Override
  protected void onUnload() {
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
    regNavigation = GlobalKey.add(this, keysNavigation);
    regAction = GlobalKey.add(this, keysAction);
    if (openChangeSetId != null) {
      changeSetsBlock.activate(openChangeSetId);
    }
  }

  public void refresh() {
    Util.T_DETAIL_SVC.topicDetail(topicId,
        new ScreenLoadCallback<TopicDetail>(this) {
          @Override
          protected void preDisplay(final TopicDetail r) {
            display(r);
          }

          @Override
          protected void postDisplay() {
            changeSetsBlock.setRegisterKeys(true);
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    addStyleName(Gerrit.RESOURCES.css().changeScreen());

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysNavigation.add(new UpToListKeyCommand(0, 'u', Util.C.upToChangeList()));
    keysNavigation.add(new ExpandCollapseDependencySectionKeyCommand(0, 'd', Util.C.expandCollapseDependencies()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));
    }

    descriptionBlock = new TopicDescriptionBlock();
    add(descriptionBlock);

    approvals = new ApprovalTable();
    add(approvals);

    includedInPanel = new DisclosurePanel(Util.C.changeScreenIncludedIn());
    includedInTable = new IncludedInTable(topicId);

    includedInPanel.setContent(includedInTable);
    add(includedInPanel);

    dependencies = new ChangeTable() {
      {
        table.setWidth("auto");
      }
    };
    dependsOn = new ChangeTable.Section(Util.C.changeScreenDependsOn());
    neededBy = new ChangeTable.Section(Util.C.changeScreenNeededBy());
    dependencies.addSection(dependsOn);
    dependencies.addSection(neededBy);

    dependenciesPanel = new DisclosurePanel(Util.C.changeScreenDependencies());
    dependenciesPanel.setContent(dependencies);
    add(dependenciesPanel);

    changesList = new ListBox();
    changesList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        final int index = changesList.getSelectedIndex();
        final String selectedChangeSet = changesList.getValue(index);
        if (index == 0) {
          diffBaseId = null;
        } else {
          diffBaseId = ChangeSet.Id.parse(selectedChangeSet);
        }
        if (changeSetsBlock != null) {
          changeSetsBlock.refresh(diffBaseId);
        }
      }
    });
    changesList.addItem(Util.C.baseDiffItem());

    changeSetsBlock = new ChangeSetsBlock(this);
    add(changeSetsBlock);

    comments = new FlowPanel();
    comments.setStyleName(Gerrit.RESOURCES.css().changeComments());
    add(comments);
  }

  private void displayTitle(final Topic.Key topicId, final String subject) {
    final StringBuilder titleBuf = new StringBuilder();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      if (subject != null) {
        titleBuf.append(subject);
        titleBuf.append(" :");
      }
      titleBuf.append(Util.TM.topicScreenTitleId(topicId.abbreviate()));
    } else {
      titleBuf.append(Util.TM.topicScreenTitleId(topicId.abbreviate()));
      if (subject != null) {
        titleBuf.append(": ");
        titleBuf.append(subject);
      }
    }
    setPageTitle(titleBuf.toString());
  }

  void update(final TopicDetail detail) {
    display(detail);
    changeSetsBlock.setRegisterKeys(true);
  }

  private void display(final TopicDetail detail) {
    displayTitle(detail.getTopic().getKey(), detail.getTopic().getSubject());

    if (Status.MERGED == detail.getTopic().getStatus()) {
      includedInPanel.setVisible(true);
      includedInPanel.addOpenHandler(includedInTable);
    } else {
      includedInPanel.setVisible(false);
    }

    dependencies.setAccountInfoCache(detail.getAccounts());
    approvals.setAccountInfoCache(detail.getAccounts());

    descriptionBlock.display(detail.getTopic(),
        detail.getCurrentChangeSetDetail().getInfo(),detail.getAccounts());

    dependsOn.display(detail.getDependsOn());
    neededBy.display(detail.getNeededBy());

    approvals.display(detail);

    for (ChangeSet cId : detail.getChangeSets()) {
      if (changesList != null) {
        changesList.addItem(Util.TM.changeSetHeader(cId.getChangeSetId()), cId
            .getId().toString());
      }
    }

    if (diffBaseId != null && changesList != null) {
      changesList.setSelectedIndex(diffBaseId.get());
    }

    changeSetsBlock.display(detail, diffBaseId);
    addComments(detail);

    // If any dependency change is still open, show our dependency list.
    //
    boolean depsOpen = false;
    if (!detail.getTopic().getStatus().isClosed()
        && detail.getDependsOn() != null) {
      for (final ChangeInfo ti : detail.getDependsOn()) {
        if (ti.getStatus() != Change.Status.MERGED) {
          depsOpen = true;
          break;
        }
      }
    }

    dependenciesPanel.setOpen(depsOpen);
  }

  private void addComments(final TopicDetail detail) {
    comments.clear();

    final AccountInfoCache accts = detail.getAccounts();
    final List<TopicMessage> msgList = detail.getMessages();

    HorizontalPanel title = new HorizontalPanel();
    title.setWidth("100%");
    title.add(new Label(Util.C.changeScreenComments()));
    if (msgList.size() > 1) {
      title.add(messagesMenuBar());
    }
    title.setStyleName(Gerrit.RESOURCES.css().blockHeader());
    comments.add(title);

    final long AGE = 7 * 24 * 60 * 60 * 1000L;
    final Timestamp aged = new Timestamp(System.currentTimeMillis() - AGE);

    for (int i = 0; i < msgList.size(); i++) {
      final TopicMessage msg = msgList.get(i);

      final AccountInfo author;
      if (msg.getAuthor() != null) {
        author = accts.get(msg.getAuthor());
      } else {
        final Account gerrit = new Account(null);
        gerrit.setFullName(Util.C.messageNoAuthor());
        author = new AccountInfo(gerrit);
      }

      boolean isRecent;
      if (i == msgList.size() - 1) {
        isRecent = true;
      } else {
        // TODO Instead of opening messages by strict age, do it by "unread"?
        isRecent = msg.getWrittenOn().after(aged);
      }

      final CommentPanel cp =
          new CommentPanel(author, msg.getWrittenOn(), msg.getMessage());
      cp.setRecent(isRecent);
      cp.addStyleName(Gerrit.RESOURCES.css().commentPanelBorder());
      if (i == msgList.size() - 1) {
        cp.addStyleName(Gerrit.RESOURCES.css().commentPanelLast());
        cp.setOpen(true);
      }
      comments.add(cp);
    }

    comments.setVisible(msgList.size() > 0);
  }

  private LinkMenuBar messagesMenuBar() {
    final Panel c = comments;
    final LinkMenuBar menuBar = new LinkMenuBar();
    menuBar.addItem(Util.C.messageExpandRecent(), new ExpandAllCommand(c, true) {
      @Override
      protected void expand(final CommentPanel w) {
        w.setOpen(w.isRecent());
      }
    });
    menuBar.addItem(Util.C.messageExpandAll(), new ExpandAllCommand(c, true));
    menuBar.addItem(Util.C.messageCollapseAll(), new ExpandAllCommand(c, false));
    menuBar.addStyleName(Gerrit.RESOURCES.css().commentPanelMenuBar());
    return menuBar;
  }

  public class UpToListKeyCommand extends KeyCommand {
    public UpToListKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      Gerrit.displayLastChangeList();
    }
  }

  public class ExpandCollapseDependencySectionKeyCommand extends KeyCommand {
    public ExpandCollapseDependencySectionKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      dependenciesPanel.setOpen(!dependenciesPanel.isOpen());
    }
  }

  public class PublishCommentsKeyCommand extends NeedsSignInKeyCommand {
    public PublishCommentsKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      ChangeSet.Id currentChangeSetId = changeSetsBlock.getCurrentChangeSetId();
      Gerrit.display("change,publish," + currentChangeSetId.toString(),
          new PublishTopicCommentScreen(currentChangeSetId));
    }
  }
}
