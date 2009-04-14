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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.data.GitwebLink;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.ExpandAllCommand;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.RefreshListener;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;
import java.util.List;


public class ChangeScreen extends Screen {
  private Change.Id changeId;
  private ChangeInfo changeInfo;
  private boolean refreshOnSignIn;

  private Image starChange;
  private boolean starred;
  private ChangeDescriptionBlock descriptionBlock;
  private DisclosurePanel dependenciesPanel;
  private ChangeTable dependencies;
  private ChangeTable.Section dependsOn;
  private ChangeTable.Section neededBy;

  private DisclosurePanel approvalsPanel;
  private ApprovalTable approvals;

  private FlowPanel patchSetPanels;

  private DisclosurePanel messagesPanel;
  private Panel messagesContent;

  public ChangeScreen(final Change.Id toShow) {
    changeId = toShow;
  }

  public ChangeScreen(final ChangeInfo c) {
    this(c.getId());
    changeInfo = c;
    starred = c.isStarred();
  }

  @Override
  public void onSignIn() {
    super.onSignIn();
    if (refreshOnSignIn) {
      refresh();
    }
    if (starChange != null) {
      starChange.setVisible(true);
    }
  }

  @Override
  public void onSignOut() {
    super.onSignOut();
    if (starChange != null) {
      starChange.setVisible(false);
    }
  }

  @Override
  public void onLoad() {
    if (descriptionBlock == null) {
      initUI();
    }

    displayTitle(changeInfo != null ? changeInfo.getSubject() : null);
    super.onLoad();

    refresh();
  }

  public void refresh() {
    Util.DETAIL_SVC.changeDetail(changeId,
        new ScreenLoadCallback<ChangeDetail>(this) {
          @Override
          protected void preDisplay(final ChangeDetail r) {
            setStarred(r.isStarred());
            display(r);
          }
        });
  }

  private void setStarred(final boolean s) {
    if (s) {
      Gerrit.ICONS.starFilled().applyTo(starChange);
    } else {
      Gerrit.ICONS.starOpen().applyTo(starChange);
    }
    starred = s;
  }

  private void initUI() {
    addStyleName("gerrit-ChangeScreen");

    starChange = Gerrit.ICONS.starOpen().createImage();
    starChange.setStyleName("gerrit-ChangeScreen-StarIcon");
    starChange.setVisible(Gerrit.isSignedIn());
    starChange.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
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
    });
    insertTitleWidget(starChange);

    descriptionBlock = new ChangeDescriptionBlock();
    add(descriptionBlock);

    dependencies = new ChangeTable();
    dependsOn = new ChangeTable.Section(Util.C.changeScreenDependsOn());
    neededBy = new ChangeTable.Section(Util.C.changeScreenNeededBy());
    dependencies.addSection(dependsOn);
    dependencies.addSection(neededBy);

    dependenciesPanel = new DisclosurePanel(Util.C.changeScreenDependencies());
    dependenciesPanel.setContent(dependencies);
    dependenciesPanel.setWidth("95%");
    add(dependenciesPanel);

    approvals = new ApprovalTable();
    approvalsPanel = new DisclosurePanel(Util.C.changeScreenApprovals());
    approvalsPanel.setContent(wrap(approvals));
    dependenciesPanel.setWidth("95%");
    add(approvalsPanel);

    patchSetPanels = new FlowPanel();
    add(patchSetPanels);

    messagesContent = new FlowPanel();
    messagesContent.setStyleName("gerrit-ChangeMessages");
    messagesPanel = new DisclosurePanel(Util.C.changeScreenMessages());
    messagesPanel.setContent(messagesContent);
    add(messagesPanel);
  }

  private void displayTitle(final String subject) {
    final StringBuilder titleBuf = new StringBuilder();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      if (subject != null) {
        titleBuf.append(subject);
        titleBuf.append(" :");
      }
      titleBuf.append(Util.M.changeScreenTitleId(changeId.get()));
    } else {
      titleBuf.append(Util.M.changeScreenTitleId(changeId.get()));
      if (subject != null) {
        titleBuf.append(": ");
        titleBuf.append(subject);
      }
    }
    setTitleText(titleBuf.toString());
  }

  private void display(final ChangeDetail detail) {
    displayTitle(detail.getChange().getSubject());

    refreshOnSignIn = !detail.getChange().getStatus().isClosed();
    dependencies.setAccountInfoCache(detail.getAccounts());
    approvals.setAccountInfoCache(detail.getAccounts());

    descriptionBlock.display(detail.getChange(), detail
        .getCurrentPatchSetDetail().getInfo(), detail.getAccounts());
    dependsOn.display(detail.getDependsOn());
    neededBy.display(detail.getNeededBy());
    approvals.display(detail.getChange(), detail.getMissingApprovals(), detail
        .getApprovals());

    addPatchSets(detail);
    addMessages(detail);

    // If any dependency change is still open, show our dependency list.
    //
    boolean depsOpen = false;
    if (!detail.getChange().getStatus().isClosed()
        && detail.getDependsOn() != null) {
      for (final ChangeInfo ci : detail.getDependsOn()) {
        if (ci.getStatus() != Change.Status.MERGED) {
          depsOpen = true;
          break;
        }
      }
    }

    dependenciesPanel.setOpen(depsOpen);
    approvalsPanel.setOpen(true);
  }

  private void addPatchSets(final ChangeDetail detail) {
    patchSetPanels.clear();

    final PatchSet currps = detail.getCurrentPatchSet();
    final GitwebLink gw = Common.getGerritConfig().getGitwebLink();
    for (final PatchSet ps : detail.getPatchSets()) {
      final ComplexDisclosurePanel panel =
          new ComplexDisclosurePanel(Util.M.patchSetHeader(ps.getPatchSetId()),
              ps == currps);
      final PatchSetPanel psp = new PatchSetPanel(detail, ps);
      panel.setContent(psp);

      final InlineLabel revtxt = new InlineLabel(ps.getRevision().get());
      revtxt.addStyleName("gerrit-PatchSetRevision");
      panel.getHeader().add(revtxt);
      if (gw != null) {
        final Anchor revlink =
            new Anchor("(gitweb)", false, gw.toRevision(detail.getChange()
                .getDest().getParentKey(), ps));
        revlink.addStyleName("gerrit-PatchSetLink");
        panel.getHeader().add(revlink);
      }

      if (ps == currps) {
        psp.ensureLoaded(detail.getCurrentPatchSetDetail());
        psp.addRefreshListener(new RefreshListener() {
          public void onSuggestRefresh() {
            refresh();
          }
        });
      } else {
        panel.addEventHandler(psp);
      }
      add(panel);
      patchSetPanels.add(panel);
    }
  }

  private void addMessages(final ChangeDetail detail) {
    messagesContent.clear();

    final AccountInfoCache accts = detail.getAccounts();
    final List<ChangeMessage> msgList = detail.getMessages();
    if (msgList.size() > 1) {
      messagesContent.add(messagesMenuBar());
    }

    final long AGE = 7 * 24 * 60 * 60 * 1000L;
    final Timestamp aged = new Timestamp(System.currentTimeMillis() - AGE);

    for (int i = 0; i < msgList.size(); i++) {
      final ChangeMessage msg = msgList.get(i);
      final MessagePanel mp = new MessagePanel(msg);
      final String panelHeader;
      final ComplexDisclosurePanel panel;

      if (msg.getAuthor() != null) {
        panelHeader = FormatUtil.nameEmail(accts.get(msg.getAuthor()));
      } else {
        panelHeader = Util.C.messageNoAuthor();
      }

      if (i == msgList.size() - 1) {
        mp.isRecent = true;
      } else {
        // TODO Instead of opening messages by strict age, do it by "unread"?
        mp.isRecent = msg.getWrittenOn().after(aged);
      }

      panel = new ComplexDisclosurePanel(panelHeader, mp.isRecent);
      panel.getHeader().add(
          new InlineLabel(Util.M.messageWrittenOn(FormatUtil.mediumFormat(msg
              .getWrittenOn()))));
      panel.setContent(mp);
      messagesContent.add(panel);
    }

    if (msgList.size() > 1) {
      messagesContent.add(messagesMenuBar());
    }
    messagesPanel.setOpen(msgList.size() > 0);
    messagesPanel.setVisible(msgList.size() > 0);
  }

  private LinkMenuBar messagesMenuBar() {
    final Panel c = messagesContent;
    final LinkMenuBar m = new LinkMenuBar();
    m.addItem(Util.C.messageExpandRecent(), new ExpandAllCommand(c, true) {
      @Override
      protected void expand(final ComplexDisclosurePanel w) {
        final MessagePanel mp = (MessagePanel) w.getContent();
        w.setOpen(mp.isRecent);
      }
    });
    m.addItem(Util.C.messageExpandAll(), new ExpandAllCommand(c, true));
    m.addItem(Util.C.messageCollapseAll(), new ExpandAllCommand(c, false));
    m.lastInGroup();
    return m;
  }

  private static FlowPanel wrap(final Widget w) {
    final FlowPanel p = new FlowPanel();
    p.add(w);
    return p;
  }
}
