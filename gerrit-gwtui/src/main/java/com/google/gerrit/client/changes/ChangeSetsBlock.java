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
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite that displays the patch sets of a change. This composite ensures
 * that keyboard navigation to each changed file in all patch sets is possible.
 */
public class ChangeSetsBlock extends Composite {

  private final Map<ChangeSet.Id, ChangeSetComplexDisclosurePanel> changeSetPanels =
      new HashMap<ChangeSet.Id, ChangeSetComplexDisclosurePanel>();

  private final TopicScreen parent;
  private final FlowPanel body;
  private HandlerRegistration regNavigation;

  private List<ChangeSetComplexDisclosurePanel> changeSetPanelsList;

  /**
   * the change set id of the change set for which is the keyboard navigation is
   * currently enabled
   */
  private ChangeSet.Id activeChangeSetId;

  /** the change set id of the current (latest) change set */
  private ChangeSet.Id currentChangeSetId;

  /** Change sets on this topic, in order. */
  private List<ChangeSet> changeSets;

  ChangeSetsBlock(final TopicScreen parent) {
    this.parent = parent;
    body = new FlowPanel();
    initWidget(body);
  }

  /** Adds UI elements for each change set of the given topic to this composite. */
  // TODO diffBaseId support
  public void display(final TopicDetail detail, final ChangeSet.Id diffBaseId) {
    clear();

    final ChangeSet currcs = detail.getCurrentChangeSet();
    currentChangeSetId = currcs.getId();
    changeSets = detail.getChangeSets();

    final List<ChangeSet.Id> topicChangeSets = new ArrayList<ChangeSet.Id>();

    for (final ChangeSet cs : changeSets) {
      topicChangeSets.add(cs.getId());
    }

    if (Gerrit.isSignedIn()) {
      final AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      if (p.isDisplayPatchSetsInReverseOrder()) {
        Collections.reverse(changeSets);
      }
    }

    changeSetPanelsList = new ArrayList<ChangeSetComplexDisclosurePanel>();

    for (final ChangeSet cs : changeSets) {
      final ChangeSetComplexDisclosurePanel p;
      if (cs == currcs) {
        p = new ChangeSetComplexDisclosurePanel(parent, detail, detail.getCurrentChangeSetDetail());
      } else {
        p = new ChangeSetComplexDisclosurePanel(parent, detail, cs);
      }

      add(p);
      changeSetPanelsList.add(p);
    }
  }

  private void clear() {
    setRegisterKeys(false);
    body.clear();
    changeSetPanels.clear();
  }

  public void refresh(final ChangeSet.Id diffBaseId) {
    if (changeSetPanelsList != null) {
      for (final ChangeSetComplexDisclosurePanel p : changeSetPanelsList) {
        p.refresh();
      }
    }
  }

  /**
   * Adds the given change set panel to this composite and ensures that handler
   * to activate / deactivate keyboard navigation for the change set panel are
   * registered.
   */

  private void add(final ChangeSetComplexDisclosurePanel changeSetPanel) {
    body.add(changeSetPanel);
    final ChangeSet.Id id = changeSetPanel.getChangeSet().getId();
    ActivationHandler activationHandler = new ActivationHandler(id);
    changeSetPanel.addOpenHandler(activationHandler);
    changeSetPanels.put(id, changeSetPanel);
  }

  public void setRegisterKeys(final boolean on) {
    if (on) {
      KeyCommandSet keysNavigation =
          new KeyCommandSet(Gerrit.C.sectionNavigation());
      keysNavigation.add(new PreviousChangeSetKeyCommand(0, 'p', Util.TC
          .previousChangeSet()));
      keysNavigation.add(new NextChangeSetKeyCommand(0, 'n', Util.TC
          .nextChangeSet()));
      regNavigation = GlobalKey.add(this, keysNavigation);
      if (activeChangeSetId != null) {
        activate(activeChangeSetId);
      } else {
        activate(currentChangeSetId);
      }
    } else {
      if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }
      deactivate();
    }
  }

  @Override
  protected void onUnload() {
    setRegisterKeys(false);
    super.onUnload();
  }

  /**
   * Activates keyboard navigation for the change set panel that displays the
   * change set with the given change set id.
   * The keyboard navigation for the previously active change set panel is
   * automatically deactivated.
   * This method also ensures that the current row is only highlighted in the
   * table of the active change set panel.
   */
  public void activate(final ChangeSet.Id changeSetId) {
    if (indexOf(changeSetId) != -1) {
      if (!changeSetId.equals(activeChangeSetId)) {
        deactivate();
        ChangeSetComplexDisclosurePanel changeSetPanel =
            changeSetPanels.get(changeSetId);
        changeSetPanel.setOpen(true);
        changeSetPanel.setActive(true);
        activeChangeSetId = changeSetId;
      }
    } else {
      Gerrit.display(PageLinks.toTopic(changeSetId.getParentKey()));
    }
  }

  /** Deactivates the keyboard navigation for the currently active change set panel. */
  private void deactivate() {
    if (activeChangeSetId != null) {
      ChangeSetComplexDisclosurePanel changeSetPanel =
          changeSetPanels.get(activeChangeSetId);
      changeSetPanel.setActive(false);
      activeChangeSetId = null;
    }
  }

  public ChangeSet getCurrentChangeSet() {
    int index = indexOf(currentChangeSetId);
    if (index != -1) {
      return changeSets.get(index);
    } else return null;
  }

  public ChangeSet.Id getCurrentChangeSetId() {
    return currentChangeSetId;
  }

  private int indexOf(ChangeSet.Id id) {
    for (int i = 0; i < changeSets.size(); i++) {
      if (changeSets.get(i).getId().equals(id)) {
        return i;
      }
    }
    return -1;
  }

  private class ActivationHandler implements OpenHandler<DisclosurePanel>,
      ClickHandler {

    private final ChangeSet.Id changeSetId;

    ActivationHandler(ChangeSet.Id changeSetId) {
      this.changeSetId = changeSetId;
    }

    @Override
    public void onOpen(OpenEvent<DisclosurePanel> event) {
      // when a change set panel is opened by the user
      // it should automatically become active
      activate(changeSetId);
    }

    @Override
    public void onClick(ClickEvent event) {
      // when a user clicks on a change table the corresponding
      // change set panel should automatically become active
      activate(changeSetId);
    }

  }

  public class PreviousChangeSetKeyCommand extends KeyCommand {
    public PreviousChangeSetKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      int index = indexOf(activeChangeSetId) - 1;
      if (0 <= index) {
        activate(changeSets.get(index).getId());
      }
    }
  }

  public class NextChangeSetKeyCommand extends KeyCommand {
    public NextChangeSetKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      int index = indexOf(activeChangeSetId) + 1;
      if (index < changeSets.size()) {
        activate(changeSets.get(index).getId());
      }
    }
  }
}
