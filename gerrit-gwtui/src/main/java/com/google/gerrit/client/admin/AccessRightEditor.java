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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SuggestBox;

public class AccessRightEditor extends Composite
    implements HasValueChangeHandlers<ProjectDetail> {
  private Project.NameKey projectKey;
  private ListBox catBox;
  private HintTextBox nameTxt;
  private SuggestBox nameSug;
  private HintTextBox referenceTxt;
  private ListBox topBox;
  private ListBox botBox;
  private Button addBut;
  private Button clearBut;

  public AccessRightEditor(final Project.NameKey key) {
    projectKey = key;

    initWidgets();
    initCategories();

    final Grid grid = new Grid(5, 2);
    grid.setText(0, 0, Util.C.columnApprovalCategory() + ":");
    grid.setWidget(0, 1, catBox);

    grid.setText(1, 0, Util.C.columnGroupName() + ":");
    grid.setWidget(1, 1, nameSug);

    grid.setText(2, 0, Util.C.columnRefName() + ":");
    grid.setWidget(2, 1, referenceTxt);

    grid.setText(3, 0, Util.C.columnRightRange() + ":");
    grid.setWidget(3, 1, topBox);

    grid.setText(4, 0, "");
    grid.setWidget(4, 1, botBox);

    FlowPanel fp = new FlowPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());

    fp.add(grid);
    fp.add(addBut);
    fp.add(clearBut);
    initWidget(fp);
  }

  protected void initWidgets() {
    catBox = new ListBox();
    catBox.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        updateCategorySelection();
      }
    });

    nameTxt = new HintTextBox();
    nameSug = new SuggestBox(new RPCSuggestOracle(
        new AccountGroupSuggestOracle()), nameTxt);
    nameTxt.setVisibleLength(50);
    nameTxt.setHintText(Util.C.defaultAccountGroupName());

    referenceTxt = new HintTextBox();
    referenceTxt.setVisibleLength(50);
    referenceTxt.setText("");
    referenceTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doAddNewRight();
        }
      }
    });

    topBox = new ListBox();
    botBox = new ListBox();

    addBut = new Button(Util.C.buttonAddProjectRight());
    addBut.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewRight();
      }
    });

    clearBut = new Button(Util.C.buttonClearProjectRight());
    clearBut.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        clear();
      }
    });
  }

  protected void initCategories() {
    for (final ApprovalType at : Gerrit.getConfig().getApprovalTypes()
        .getApprovalTypes()) {
      final ApprovalCategory c = at.getCategory();
      catBox.addItem(c.getName(), c.getId().get());
    }
    for (final ApprovalType at : Gerrit.getConfig().getApprovalTypes()
        .getActionTypes()) {
      final ApprovalCategory c = at.getCategory();
      if (Gerrit.getConfig().getWildProject().equals(projectKey)
          && !c.getId().canBeOnWildProject()) {
        // Giving out control of the WILD_PROJECT to other groups beyond
        // Administrators is dangerous. Having control over WILD_PROJECT
        // is about the same as having Administrator access as users are
        // able to affect grants in all projects on the system.
        //
        continue;
      }
      catBox.addItem(c.getName(), c.getId().get());
    }

    if (catBox.getItemCount() > 0) {
      catBox.setSelectedIndex(0);
      updateCategorySelection();
    }
  }

  public void enableForm(final boolean on) {
    final boolean canAdd = on && catBox.getItemCount() > 0;
    addBut.setEnabled(canAdd);
    clearBut.setEnabled(canAdd);
    nameTxt.setEnabled(canAdd);
    referenceTxt.setEnabled(canAdd);
    catBox.setEnabled(canAdd);
    topBox.setEnabled(canAdd);
    botBox.setEnabled(canAdd);
  }

  public void clear() {
    setCat(null);
    setName("");
    setReference("");
  }

  public void load(final RefRight right, final AccountGroup group) {
    final ApprovalType atype =
       Gerrit.getConfig().getApprovalTypes().getApprovalType(
          right.getApprovalCategoryId());

    setCat(atype != null ? atype.getCategory().getName()
                         : right.getApprovalCategoryId().get() );

    setName(group.getName());
    setReference(right.getRefPatternForDisplay());

    setRange(atype.getCategory().isRange() ? atype.getValue(right.getMinValue())
             : null, atype.getValue(right.getMaxValue()) );
  }

  protected void doAddNewRight() {
    final ApprovalType at = getApprovalType();
    ApprovalCategoryValue min = getMin(at);
    ApprovalCategoryValue max = getMax(at);

    if (at == null || min == null || max == null) {
      return;
    }

    final String groupName = nameSug.getText();
    if ("".equals(groupName)
        || Util.C.defaultAccountGroupName().equals(groupName)) {
      return;
    }

    final String refPattern = referenceTxt.getText();

    addBut.setEnabled(false);
    Util.PROJECT_SVC.addRight(projectKey, at.getCategory().getId(),
        groupName, refPattern, min.getValue(), max.getValue(),
        new GerritCallback<ProjectDetail>() {
          public void onSuccess(final ProjectDetail result) {
            addBut.setEnabled(true);
            nameSug.setText("");
            referenceTxt.setText("");
            ValueChangeEvent.fire(AccessRightEditor.this, result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addBut.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  protected void updateCategorySelection() {
    final ApprovalType at = getApprovalType();

    if (at == null || at.getValues().isEmpty()) {
      topBox.setEnabled(false);
      botBox.setEnabled(false);
      referenceTxt.setEnabled(false);
      addBut.setEnabled(false);
      clearBut.setEnabled(false);
      return;
    }

    updateRanges(at);
  }

  protected void updateRanges(final ApprovalType at) {
    ApprovalCategoryValue min = null, max = null, last = null;

    topBox.clear();
    botBox.clear();

    for(final ApprovalCategoryValue v : at.getValues()) {
      final int nval = v.getValue();
      final String vStr = String.valueOf(nval);

      String nStr = vStr + ": " + v.getName();
      if (nval > 0) {
        nStr = "+" + nStr;
      }

      topBox.addItem(nStr, vStr);
      botBox.addItem(nStr, vStr);

      if (min == null || nval < 0) {
        min = v;
      } else if (max == null && nval > 0) {
        max = v;
      }
      last = v;
    }

    if (max == null) {
      max = last;
    }

    if (ApprovalCategory.READ.equals(at.getCategory().getId())) {
      // Special case; for READ the most logical range is just
      // +1 READ, so assume that as the default for both.
      min = max;
    }

    if (! at.getCategory().isRange()) {
      max = null;
    }

    setRange(min, max);
  }

  protected void setCat(final String cat) {
    if (cat == null) {
      catBox.setSelectedIndex(0);
    } else {
      setSelectedText(catBox, cat);
    }
    updateCategorySelection();
  }

  protected void setName(final String name) {
    nameTxt.setText(name);
  }

  protected void setReference(final String ref) {
    referenceTxt.setText(ref);
  }

  protected void setRange(final ApprovalCategoryValue min,
                          final ApprovalCategoryValue max) {
    if (min == null || max == null) {
      botBox.setVisible(false);
      if (max != null) {
        setSelectedValue(topBox, "" + max.getValue());
        return;
      }
    } else {
      botBox.setVisible(true);
      setSelectedValue(botBox, "" + max.getValue());
    }
    setSelectedValue(topBox, "" + min.getValue());
  }

  private ApprovalType getApprovalType() {
    int idx = catBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    }
    return Gerrit.getConfig().getApprovalTypes().getApprovalType(
             new ApprovalCategory.Id(catBox.getValue(idx)));
  }

  public ApprovalCategoryValue getMin(ApprovalType at) {
    final ApprovalCategoryValue top = getTop(at);
    final ApprovalCategoryValue bot = getBot(at);
    if (bot == null) {
      for (final ApprovalCategoryValue v : at.getValues()) {
        if (0 <= v.getValue() && v.getValue() <= top.getValue()) {
          return v;
        }
      }
      return at.getMin();
    }

    if (top.getValue() > bot.getValue()) {
      return bot;
    }
    return top;
  }

  public ApprovalCategoryValue getMax(ApprovalType at) {
    final ApprovalCategoryValue top = getTop(at);
    final ApprovalCategoryValue bot = getBot(at);
    if (bot == null || bot.getValue() < top.getValue()) {
      return top;
    }
    return bot;
  }

  protected ApprovalCategoryValue getTop(ApprovalType at) {
    int idx = topBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    }
    return at.getValue(Short.parseShort(topBox.getValue(idx)));
  }

  protected ApprovalCategoryValue getBot(ApprovalType at) {
    int idx = botBox.getSelectedIndex();
    if (idx < 0 || ! botBox.isVisible()) {
      return null;
    }
    return at.getValue(Short.parseShort(botBox.getValue(idx)));
  }

  public HandlerRegistration addValueChangeHandler(
      final ValueChangeHandler<ProjectDetail> handler) {
    return addHandler(handler, ValueChangeEvent.getType());
  }

  public static boolean setSelectedText(ListBox box, String text) {
    if (text == null) {
      return false;
    }
    for (int i =0 ; i < box.getItemCount(); i++) {
      if (text.equals(box.getItemText(i))) {
        box.setSelectedIndex(i);
        return true;
      }
    }
    return false;
  }

  public static boolean setSelectedValue(ListBox box, String value) {
    if (value == null) {
      return false;
    }
    for (int i =0 ; i < box.getItemCount(); i++) {
      if (value.equals(box.getValue(i))) {
        box.setSelectedIndex(i);
        return true;
      }
    }
    return false;
  }
}
