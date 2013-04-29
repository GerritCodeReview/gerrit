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
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.ExpandAllCommand;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.sql.Timestamp;
import java.util.List;


public class ChangeScreen extends Screen
    implements ValueChangeHandler<ChangeDetail> {
  private final Change.Id changeId;
  private final PatchSet.Id openPatchSetId;
  private ChangeDetailCache detailCache;
  private com.google.gerrit.client.changes.ChangeInfo changeInfo;

  private ChangeDescriptionBlock descriptionBlock;
  private ApprovalTable approvals;

  private IncludedInTable includedInTable;
  private DisclosurePanel includedInPanel;
  private ComplexDisclosurePanel dependenciesPanel;
  private ChangeTable dependencies;
  private ChangeTable.Section dependsOn;
  private ChangeTable.Section neededBy;

  private PatchSetsBlock patchSetsBlock;

  private Panel comments;

  private KeyCommandSet keysNavigation;
  private KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;

  private Grid patchesGrid;
  private ListBox patchesList;

  /**
   * The change id for which the old version history is valid.
   */
  private static Change.Id currentChangeId;

  /**
   * Which patch set id is the diff base.
   */
  private static PatchSet.Id diffBaseId;

  public ChangeScreen(final Change.Id toShow) {
    changeId = toShow;
    openPatchSetId = null;
  }

  public ChangeScreen(final PatchSet.Id toShow) {
    changeId = toShow.getParentKey();
    openPatchSetId = toShow;
  }

  public ChangeScreen(final ChangeInfo c) {
    this(c.getId());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    detailCache.refresh();
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
    if (openPatchSetId != null) {
      patchSetsBlock.activate(openPatchSetId);
    }
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    ChangeCache cache = ChangeCache.get(changeId);

    detailCache = cache.getChangeDetailCache();
    detailCache.addValueChangeHandler(this);

    addStyleName(Gerrit.RESOURCES.css().changeScreen());
    addStyleName(Gerrit.RESOURCES.css().screenNoHeader());

    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysNavigation.add(new UpToListKeyCommand(0, 'u', Util.C.upToChangeList()));
    keysNavigation.add(new ExpandCollapseDependencySectionKeyCommand(0, 'd', Util.C.expandCollapseDependencies()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));
    }

    descriptionBlock = new ChangeDescriptionBlock(keysAction);
    add(descriptionBlock);

    approvals = new ApprovalTable();
    add(approvals);

    includedInPanel = new DisclosurePanel(Util.C.changeScreenIncludedIn());
    includedInTable = new IncludedInTable(changeId);

    includedInPanel.setContent(includedInTable);
    add(includedInPanel);

    dependencies = new ChangeTable() {
      {
        table.setWidth("auto");
      }
    };
    dependsOn = new ChangeTable.Section(Util.C.changeScreenDependsOn());
    dependsOn.setChangeRowFormatter(new ChangeTable.ChangeRowFormatter() {
      @Override
      public String getRowStyle(ChangeInfo c) {
        if (! c.isLatest() || Change.Status.ABANDONED.equals(c.getStatus())) {
          return Gerrit.RESOURCES.css().outdated();
        }
        return null;
      }

      @Override
      public String getDisplayText(final ChangeInfo c, final String displayText) {
        if (! c.isLatest()) {
          return displayText + " [OUTDATED]";
        }
        return displayText;
      }
    });
    neededBy = new ChangeTable.Section(Util.C.changeScreenNeededBy());
    dependencies.addSection(dependsOn);
    dependencies.addSection(neededBy);

    dependenciesPanel = new ComplexDisclosurePanel(
        Util.C.changeScreenDependencies(), false);
    dependenciesPanel.setContent(dependencies);
    add(dependenciesPanel);

    patchesList = new ListBox();
    patchesList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        final int index = patchesList.getSelectedIndex();
        final String selectedPatchSet = patchesList.getValue(index);
        if (index == 0) {
          diffBaseId = null;
        } else {
          diffBaseId = PatchSet.Id.parse(selectedPatchSet);
        }
        if (patchSetsBlock != null) {
          patchSetsBlock.refresh(diffBaseId);
        }
      }
    });

    patchesGrid = new Grid(1, 2);
    patchesGrid.setStyleName(Gerrit.RESOURCES.css().selectPatchSetOldVersion());
    patchesGrid.setText(0, 0, Util.C.referenceVersion());
    patchesGrid.setWidget(0, 1, patchesList);
    add(patchesGrid);

    patchSetsBlock = new PatchSetsBlock();
    add(patchSetsBlock);

    comments = new FlowPanel();
    comments.setStyleName(Gerrit.RESOURCES.css().changeComments());
    add(comments);
  }

  private void displayTitle(final Change.Key changeId, final String subject) {
    final StringBuilder titleBuf = new StringBuilder();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      if (subject != null) {
        titleBuf.append(subject);
        titleBuf.append(" :");
      }
      titleBuf.append(Util.M.changeScreenTitleId(changeId.abbreviate()));
    } else {
      titleBuf.append(Util.M.changeScreenTitleId(changeId.abbreviate()));
      if (subject != null) {
        titleBuf.append(": ");
        titleBuf.append(subject);
      }
    }
    setPageTitle(titleBuf.toString());
    setHeaderVisible(false);
  }

  @Override
  public void onValueChange(final ValueChangeEvent<ChangeDetail> event) {
    if (isAttached()) {
      // Until this screen is fully migrated to the new API, this call must be
      // sequential, because we can't start an async get at the source of every
      // call that might trigger a value change.
      ChangeApi.detail(event.getValue().getChange().getId().get(),
          new GerritCallback<com.google.gerrit.client.changes.ChangeInfo>() {
            @Override
            public void onSuccess(
                com.google.gerrit.client.changes.ChangeInfo result) {
              changeInfo = result;
              display(event.getValue());
            }
          });
    }
  }

  private void display(final ChangeDetail detail) {
    displayTitle(detail.getChange().getKey(), detail.getChange().getSubject());
    discardDiffBaseIfNotApplicable(detail.getChange().getId());

    if (Status.MERGED == detail.getChange().getStatus()) {
      includedInPanel.setVisible(true);
      includedInPanel.addOpenHandler(includedInTable);
    } else {
      includedInPanel.setVisible(false);
    }

    dependencies.setAccountInfoCache(detail.getAccounts());

    descriptionBlock.display(detail.getChange(),
        detail.isStarred(),
        detail.canEditCommitMessage(),
        detail.getCurrentPatchSetDetail().getInfo(),
        detail.getAccounts(), detail.getSubmitTypeRecord());
    dependsOn.display(detail.getDependsOn());
    neededBy.display(detail.getNeededBy());
    approvals.display(changeInfo);

    patchesList.clear();
    if (detail.getCurrentPatchSetDetail().getInfo().getParents().size() > 1) {
      patchesList.addItem(Util.C.autoMerge());
    } else {
      patchesList.addItem(Util.C.baseDiffItem());
    }
    for (PatchSet pId : detail.getPatchSets()) {
      if (patchesList != null) {
        patchesList.addItem(Util.M.patchSetHeader(pId.getPatchSetId()), pId
            .getId().toString());
      }
    }

    if (diffBaseId != null && patchesList != null) {
      patchesList.setSelectedIndex(diffBaseId.get());
    }

    patchSetsBlock.display(detail, diffBaseId);
    addComments(detail);

    // If any dependency change is still open, or is outdated,
    // or the change is needed by a change that is new or submitted,
    // show our dependency list.
    //
    boolean depsOpen = false;
    int outdated = 0;
    if (!detail.getChange().getStatus().isClosed()) {
      final List<ChangeInfo> dependsOn = detail.getDependsOn();
      if (dependsOn != null) {
        for (final ChangeInfo ci : dependsOn) {
          if (!ci.isLatest()) {
            depsOpen = true;
            outdated++;
          } else if (ci.getStatus() != Change.Status.MERGED) {
            depsOpen = true;
          }
        }
      }
    }
    final List<ChangeInfo> neededBy = detail.getNeededBy();
    if (neededBy != null) {
      for (final ChangeInfo ci : neededBy) {
        if ((ci.getStatus() == Change.Status.NEW) ||
            (ci.getStatus() == Change.Status.SUBMITTED) ||
            (ci.getStatus() == Change.Status.DRAFT)) {
          depsOpen = true;
        }
      }
    }

    dependenciesPanel.setOpen(depsOpen);

    dependenciesPanel.getHeader().clear();
    if (outdated > 0) {
      dependenciesPanel.getHeader().add(new InlineLabel(
        Util.M.outdatedHeader(outdated)));
    }

    if (!isCurrentView()) {
      display();
    }
    patchSetsBlock.setRegisterKeys(true);
  }

  private static void discardDiffBaseIfNotApplicable(final Change.Id toShow) {
    if (currentChangeId != null && !currentChangeId.equals(toShow)) {
      diffBaseId = null;
    }
    currentChangeId = toShow;
  }

  private void addComments(final ChangeDetail detail) {
    comments.clear();

    final AccountInfoCache accts = detail.getAccounts();
    final List<ChangeMessage> msgList = detail.getMessages();

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
      final ChangeMessage msg = msgList.get(i);

      AccountInfo author;
      if (msg.getAuthor() != null) {
        author = FormatUtil.asInfo(accts.get(msg.getAuthor()));
      } else {
        author = AccountInfo.create(0, Util.C.messageNoAuthor(), null);
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

    final Button b = new Button(Util.C.changeScreenAddComment());
    b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
            PatchSet.Id currentPatchSetId = patchSetsBlock.getCurrentPatchSet().getId();
            Gerrit.display(Dispatcher.toPublish(currentPatchSetId));
        }
    });
    comments.add(b);
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
      PatchSet.Id currentPatchSetId = patchSetsBlock.getCurrentPatchSet().getId();
      Gerrit.display(Dispatcher.toPublish(currentPatchSetId));
    }
  }
}
