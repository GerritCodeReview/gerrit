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
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.PatchSet;
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

import java.util.HashMap;

/**
 * Composite that displays the patch sets of a change. This composite ensures
 * that keyboard navigation to each changed file in all patch sets is possible.
 */
public class PatchSetsBlock extends Composite {

  private final HashMap<Integer, PatchSetComplexDisclosurePanel> patchSetPanels =
      new HashMap<Integer, PatchSetComplexDisclosurePanel>();

  private final ChangeScreen parent;
  private final FlowPanel body;
  private HandlerRegistration regNavigation;

  /**
   * the patch set id of the patch set for which is the keyboard navigation is
   * currently enabled
   */
  private int activePatchSetId = -1;

  /** the patch set id of the current (latest) patch set */
  private int currentPatchSetId = -1;

  PatchSetsBlock(final ChangeScreen parent) {
    this.parent = parent;
    body = new FlowPanel();
    initWidget(body);
  }

  /** Adds UI elements for each patch set of the given change to this composite. */
  public void display(final ChangeDetail detail) {
    clear();

    final PatchSet currps = detail.getCurrentPatchSet();
    currentPatchSetId = currps.getPatchSetId();
    for (final PatchSet ps : detail.getPatchSets()) {
      if (ps == currps) {
        add(new PatchSetComplexDisclosurePanel(parent, detail, detail
            .getCurrentPatchSetDetail()));
      } else {
        add(new PatchSetComplexDisclosurePanel(parent, detail, ps));
      }
    }
  }

  private void clear() {
    body.clear();
    patchSetPanels.clear();
    setRegisterKeys(false);
  }

  /**
   * Adds the given patch set panel to this composite and ensures that handler
   * to activate / deactivate keyboard navigation for the patch set panel are
   * registered.
   */
  private void add(final PatchSetComplexDisclosurePanel patchSetPanel) {
    body.add(patchSetPanel);

    int patchSetId = patchSetPanel.getPatchSet().getPatchSetId();
    ActivationHandler activationHandler = new ActivationHandler(patchSetId);
    patchSetPanel.addOpenHandler(activationHandler);
    patchSetPanel.addClickHandler(activationHandler);
    patchSetPanels.put(patchSetId, patchSetPanel);
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
      if (activePatchSetId != -1) {
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
  private void activate(final int patchSetId) {
    if (activePatchSetId != patchSetId) {
      deactivate();
      PatchSetComplexDisclosurePanel patchSetPanel =
          patchSetPanels.get(patchSetId);
      patchSetPanel.setOpen(true);
      patchSetPanel.setActive(true);
      activePatchSetId = patchSetId;
    }
  }

  /** Deactivates the keyboard navigation for the currently active patch set panel. */
  private void deactivate() {
    if (activePatchSetId != -1) {
      PatchSetComplexDisclosurePanel patchSetPanel =
          patchSetPanels.get(activePatchSetId);
      if (patchSetPanel != null) {
        patchSetPanel.setActive(false);
      }
      activePatchSetId = -1;
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

  private class ActivationHandler implements OpenHandler<DisclosurePanel>,
      ClickHandler {

    private final int patchSetId;

    ActivationHandler(int patchSetId) {
      this.patchSetId = patchSetId;
    }

    @Override
    public void onOpen(OpenEvent<DisclosurePanel> event) {
      // when a patch set panel is opened by the user
      // it should automatically become active
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
      if (activePatchSetId > 1) {
        activate(activePatchSetId - 1);
      }
    }
  }

  public class NextPatchSetKeyCommand extends KeyCommand {
    public NextPatchSetKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      if (activePatchSetId > 0 && activePatchSetId < body.getWidgetCount()) {
        activate(activePatchSetId + 1);
      }
    }
  }
}
