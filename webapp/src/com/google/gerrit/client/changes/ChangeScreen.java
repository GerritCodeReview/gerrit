// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.client.ui.ExpandAllCommand;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

import java.sql.Timestamp;
import java.util.List;


public class ChangeScreen extends Screen {
  private Change.Id changeId;
  private ChangeInfo changeInfo;

  private ChangeInfoBlock infoBlock;
  private DisclosurePanel descriptionPanel;
  private Label description;

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
  }

  @Override
  public Object getScreenCacheToken() {
    return getClass();
  }

  @Override
  public Screen recycleThis(final Screen newScreen) {
    final ChangeScreen s = (ChangeScreen) newScreen;
    changeId = s.changeId;
    changeInfo = s.changeInfo;
    return this;
  }

  @Override
  public void onLoad() {
    if (descriptionPanel == null) {
      initUI();
    }

    displayTitle(changeInfo != null ? changeInfo.getSubject() : null);
    super.onLoad();

    Util.DETAIL_SVC.changeDetail(changeId,
        new ScreenLoadCallback<ChangeDetail>() {
          public void onSuccess(final ChangeDetail r) {
            // TODO Actually we want to cancel the RPC if detached.
            if (isAttached()) {
              display(r);
            }
          }
        });
  }

  private void initUI() {
    addStyleName("gerrit-ChangeScreen");

    infoBlock = new ChangeInfoBlock();

    description = newDescriptionLabel();

    descriptionPanel = new DisclosurePanel(Util.C.changeScreenDescription());
    {
      final Label glue = new Label();
      final HorizontalPanel hp = new HorizontalPanel();
      hp.add(description);
      hp.add(glue);
      hp.add(infoBlock);
      hp.setCellWidth(glue, "100%");
      add(hp);
      descriptionPanel.setContent(hp);
      descriptionPanel.setWidth("100%");
      add(descriptionPanel);
    }

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
    if (changeInfo == null) {
      // We couldn't set the title correctly when we loaded the page
      // into the browser, update it now that we have the full detail.
      //
      displayTitle(detail.getChange().getSubject());
    }

    dependencies.setAccountInfoCache(detail.getAccounts());
    approvals.setAccountInfoCache(detail.getAccounts());

    infoBlock.display(detail);
    description.setText(detail.getDescription());
    dependsOn.display(detail.getDependsOn());
    neededBy.display(detail.getNeededBy());
    approvals.display(detail.getApprovals());

    addPatchSets(detail);
    addMessages(detail);

    descriptionPanel.setOpen(true);
    approvalsPanel.setOpen(true);
  }

  private void addPatchSets(final ChangeDetail detail) {
    patchSetPanels.clear();

    final PatchSet currps = detail.getCurrentPatchSet();
    final GitwebLink gw = Gerrit.getGerritConfig().getGitwebLink();
    for (final PatchSet ps : detail.getPatchSets()) {
      final ComplexDisclosurePanel panel =
          new ComplexDisclosurePanel(Util.M.patchSetHeader(ps.getId()),
              ps == currps);
      final PatchSetPanel psp = new PatchSetPanel(detail, ps);
      panel.setContent(psp);

      if (gw != null) {
        final Anchor revlink =
            new Anchor(ps.getRevision(), false, gw.toRevision(detail
                .getChange().getDest().getParentKey(), ps));
        revlink.addStyleName("gerrit-PatchSetLink");
        panel.getHeader().add(revlink);
      }

      if (ps == currps) {
        psp.ensureLoaded(detail.getCurrentPatchSetDetail());
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

  private static Label newDescriptionLabel() {
    final Label d = new Label();
    d.setStyleName("gerrit-ChangeScreen-Description");
    return d;
  }
}
