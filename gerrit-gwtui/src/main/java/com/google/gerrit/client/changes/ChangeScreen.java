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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.ExpandAllCommand;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Change.Status;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class ChangeScreen extends Screen {
  private final Change.Id changeId;
  private final PatchSet.Id openPatchSetId;

  private Image starChange;
  private boolean starred;
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

    // If we have any diff stored, make sure they are applicable to the
    // current change, discard them otherwise.
    //
    if (currentChangeId != null && !currentChangeId.equals(toShow)) {
      diffBaseId = null;
    }
    currentChangeId = toShow;
  }

  public ChangeScreen(final PatchSet.Id toShow) {
    changeId = toShow.getParentKey();
    openPatchSetId = toShow;
  }

  public ChangeScreen(final ChangeInfo c) {
    this(c.getId());
  }

  @Override
  public void onSignOut() {
    super.onSignOut();
    if (starChange != null) {
      starChange.setVisible(false);
    }
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
    if (openPatchSetId != null) {
      patchSetsBlock.activate(openPatchSetId);
    }
  }

  public void refresh() {
    Util.DETAIL_SVC.changeDetail(changeId,
        new ScreenLoadCallback<ChangeDetail>(this) {
          @Override
          protected void preDisplay(final ChangeDetail r) {
            display(r);
          }

          @Override
          protected void postDisplay() {
            patchSetsBlock.setRegisterKeys(true);
          }
        });
  }

  private void setStarred(final boolean s) {
    if (s) {
      starChange.setResource(Gerrit.RESOURCES.starFilled());
    } else {
      starChange.setResource(Gerrit.RESOURCES.starOpen());
    }
    starred = s;
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
      keysAction.add(new StarKeyCommand(0, 's', Util.C.changeTableStar()));
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));

      starChange = new Image(Gerrit.RESOURCES.starOpen());
      starChange.setStyleName(Gerrit.RESOURCES.css().changeScreenStarIcon());
      starChange.setVisible(Gerrit.isSignedIn());
      starChange.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          toggleStar();
        }
      });
      insertTitleWidget(starChange);
    }

    descriptionBlock = new ChangeDescriptionBlock();
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
    patchesList.addItem(Util.C.baseDiffItem());

    patchesGrid = new Grid(1, 2);
    patchesGrid.setStyleName(Gerrit.RESOURCES.css().selectPatchSetOldVersion());
    patchesGrid.setText(0, 0, Util.C.oldVersionHistory());
    patchesGrid.setWidget(0, 1, patchesList);
    add(patchesGrid);

    patchSetsBlock = new PatchSetsBlock(this);
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
  }

  void update(final ChangeDetail detail) {
    display(detail);
    patchSetsBlock.setRegisterKeys(true);
  }

  private void display(final ChangeDetail detail) {
    displayTitle(detail.getChange().getKey(), detail.getChange().getSubject());

    if (starChange != null) {
      setStarred(detail.isStarred());
    }

    if (Status.MERGED == detail.getChange().getStatus()) {
      includedInPanel.setVisible(true);
      includedInPanel.addOpenHandler(includedInTable);
    } else {
      includedInPanel.setVisible(false);
    }

    dependencies.setAccountInfoCache(detail.getAccounts());
    approvals.setAccountInfoCache(detail.getAccounts());

    descriptionBlock.display(detail.getChange(), detail
        .getCurrentPatchSetDetail().getInfo(), detail.getAccounts());
    dependsOn.display(detail.getDependsOn());
    neededBy.display(detail.getNeededBy());
    approvals.display(detail);

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
    // show our dependency list.
    //
    boolean depsOpen = false;
    int outdated = 0;
    if (!detail.getChange().getStatus().isClosed()
        && detail.getDependsOn() != null) {
      for (final ChangeInfo ci : detail.getDependsOn()) {
        if (! ci.isLatest()) {
          depsOpen = true;
          outdated++;
        } else if (ci.getStatus() != Change.Status.MERGED) {
          depsOpen = true;
        }
      }
    }

    dependenciesPanel.setOpen(depsOpen);
    if (outdated > 0) {
      dependenciesPanel.getHeader().add(new InlineLabel(
        Util.M.outdatedHeader(outdated)));
    }
  }

  private void addComments(final ChangeDetail detail) {
    comments.clear();

    final AccountInfoCache accts = detail.getAccounts();
    final List<ChangeMessage> msgList = new ArrayList<ChangeMessage>();
    final Map<PatchSet.Id, PatchSet> patchsets = new HashMap<PatchSet.Id, PatchSet>();
    for (PatchSet ps : detail.getPatchSets()) {
      patchsets.put(ps.getId(), ps);
    }

    for (ChangeMessage msg : detail.getMessages()) {
      PatchSet.Id id = msg.getPatchSetId();
      if (id != null) {
        PatchSet ps = patchsets.get(id);
        if (ps == null || !ps.isDraft() ||
            (Gerrit.isSignedIn() &&
            detail.hasDraftAccess(Gerrit.getUserAccount().getId()))) {
          msgList.add(msg);
        }
      } else {
        msgList.add(msg);
      }
    }

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

  private void toggleStar() {
    final boolean prior = starred;
    setStarred(!prior);

    final ToggleStarRequest req = new ToggleStarRequest();
    req.toggle(changeId, starred);
    Util.LIST_SVC.toggleStars(req, new GerritCallback<VoidResult>() {
      public void onSuccess(final VoidResult result) {
      }

      @Override
      public void onFailure(final Throwable caught) {
        super.onFailure(caught);
        setStarred(prior);
      }
    });
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

  public class StarKeyCommand extends NeedsSignInKeyCommand {
    public StarKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      toggleStar();
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
