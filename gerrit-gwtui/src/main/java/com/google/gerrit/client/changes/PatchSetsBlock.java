// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.PatchSet;
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
public class PatchSetsBlock extends Composite {
  private final Map<PatchSet.Id, PatchSetComplexDisclosurePanel> patchSetPanels =
      new HashMap<PatchSet.Id, PatchSetComplexDisclosurePanel>();

  private final FlowPanel body;
  private HandlerRegistration regNavigation;

  private List<PatchSetComplexDisclosurePanel> patchSetPanelsList;

  /**
   * the patch set id of the patch set for which is the keyboard navigation is
   * currently enabled
   */
  private PatchSet.Id activePatchSetId;

  /** the patch set id of the current (latest) patch set */
  private PatchSet.Id currentPatchSetId;

  /** Patch sets on this change, in order. */
  private List<PatchSet> patchSets;

  PatchSetsBlock() {
    body = new FlowPanel();
    initWidget(body);
  }

  /** Adds UI elements for each patch set of the given change to this composite. */
  public void display(final ChangeDetail detail, final PatchSet.Id diffBaseId) {
    clear();

    final PatchSet currps = detail.getCurrentPatchSet();
    currentPatchSetId = currps.getId();
    patchSets = detail.getPatchSets();

    if (Gerrit.isSignedIn()) {
      final AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      if (p.isReversePatchSetOrder()) {
        Collections.reverse(patchSets);
      }
    }

    patchSetPanelsList = new ArrayList<PatchSetComplexDisclosurePanel>();

    for (final PatchSet ps : patchSets) {
      final PatchSetComplexDisclosurePanel p =
          new PatchSetComplexDisclosurePanel(ps, ps == currps,
              detail.getPatchSetsWithDraftComments().contains(ps.getId()));
      if (diffBaseId != null) {
        p.setDiffBaseId(diffBaseId);
        if (ps == currps) {
          p.refresh();
        }
      }
      add(p);
      patchSetPanelsList.add(p);
    }
  }

  private void clear() {
    setRegisterKeys(false);
    body.clear();
    patchSetPanels.clear();
  }

  public void refresh(final PatchSet.Id diffBaseId) {
    if (patchSetPanelsList != null) {
      for (final PatchSetComplexDisclosurePanel p : patchSetPanelsList) {
        p.setDiffBaseId(diffBaseId);
        if (p.isOpen()) {
          p.refresh();
        }
      }
    }
  }

  /**
   * Adds the given patch set panel to this composite and ensures that handler
   * to activate / deactivate keyboard navigation for the patch set panel are
   * registered.
   */
  private void add(final PatchSetComplexDisclosurePanel patchSetPanel) {
    body.add(patchSetPanel);

    final PatchSet.Id id = patchSetPanel.getPatchSet().getId();
    ActivationHandler activationHandler = new ActivationHandler(id);
    patchSetPanel.addOpenHandler(activationHandler);
    patchSetPanel.addClickHandler(activationHandler);
    patchSetPanels.put(id, patchSetPanel);
  }

  public void setRegisterKeys(final boolean on) {
    if (on) {
      KeyCommandSet keysNavigation =
          new KeyCommandSet(Gerrit.C.sectionNavigation());
      keysNavigation.add(new PreviousPatchSetKeyCommand(0, 'p', Util.C
          .previousPatchSet()));
      keysNavigation.add(new NextPatchSetKeyCommand(0, 'n', Util.C
          .nextPatchSet()));
      regNavigation = GlobalKey.add(this, keysNavigation);
      if (activePatchSetId != null) {
        activate(activePatchSetId);
      } else {
        activate(currentPatchSetId);
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
   * Activates keyboard navigation for the patch set panel that displays the
   * patch set with the given patch set id.
   * The keyboard navigation for the previously active patch set panel is
   * automatically deactivated.
   * This method also ensures that the current row is only highlighted in the
   * table of the active patch set panel.
   */
  public void activate(final PatchSet.Id patchSetId) {
    if (indexOf(patchSetId) != -1) {
      if (!patchSetId.equals(activePatchSetId)) {
        deactivate();
        PatchSetComplexDisclosurePanel patchSetPanel =
            patchSetPanels.get(patchSetId);
        patchSetPanel.setActive(true);
        patchSetPanel.setOpen(true);
        activePatchSetId = patchSetId;
      }
    } else {
      Gerrit.display(PageLinks.toChange(patchSetId.getParentKey()));
    }
  }

  /** Deactivates the keyboard navigation for the currently active patch set panel. */
  private void deactivate() {
    if (activePatchSetId != null) {
      PatchSetComplexDisclosurePanel patchSetPanel =
          patchSetPanels.get(activePatchSetId);
      patchSetPanel.setActive(false);
      activePatchSetId = null;
    }
  }

  public PatchSet getCurrentPatchSet() {
    PatchSetComplexDisclosurePanel patchSetPanel =
        patchSetPanels.get(currentPatchSetId);
    if (patchSetPanel != null) {
      return patchSetPanel.getPatchSet();
    } else {
      return null;
    }
  }

  private int indexOf(PatchSet.Id id) {
    for (int i = 0; i < patchSets.size(); i++) {
      if (patchSets.get(i).getId().equals(id)) {
        return i;
      }
    }
    return -1;
  }

  private class ActivationHandler implements OpenHandler<DisclosurePanel>,
      ClickHandler {

    private final PatchSet.Id patchSetId;

    ActivationHandler(PatchSet.Id patchSetId) {
      this.patchSetId = patchSetId;
    }

    @Override
    public void onOpen(OpenEvent<DisclosurePanel> event) {
      // when a patch set panel is opened by the user
      // it should automatically become active
      PatchSetComplexDisclosurePanel patchSetPanel =
          patchSetPanels.get(patchSetId);
      patchSetPanel.refresh();
      activate(patchSetId);
    }

    @Override
    public void onClick(ClickEvent event) {
      // when a user clicks on a patch table the corresponding
      // patch set panel should automatically become active
      activate(patchSetId);
    }

  }

  public class PreviousPatchSetKeyCommand extends KeyCommand {
    public PreviousPatchSetKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      int index = indexOf(activePatchSetId) - 1;
      if (0 <= index) {
        activate(patchSets.get(index).getId());
      }
    }
  }

  public class NextPatchSetKeyCommand extends KeyCommand {
    public NextPatchSetKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      int index = indexOf(activePatchSetId) + 1;
      if (index < patchSets.size()) {
        activate(patchSets.get(index).getId());
      }
    }
  }
}
