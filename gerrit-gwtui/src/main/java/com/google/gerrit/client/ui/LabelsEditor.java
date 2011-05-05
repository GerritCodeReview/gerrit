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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

/** UI Component to edit change labels */
public class LabelsEditor extends Composite {
  private final HorizontalPanel addPanel;
  private final Button addLabel;
  private HintTextBox nameTxtBox;
  private SuggestBox nameTxt;
  private FlowPanel arbitraryLabelsPanel;
  private List<ChangeLabel.LabelKey> arbitraryLabels;
  private VerticalPanel labelsPanel;
  private final Change.Id changeId;
  private boolean submitOnSelection;
  private LabelSuggestOracle labelSuggestOracle;

  public LabelsEditor(final Change.Id changeId) {
    this.changeId = changeId;
    labelsPanel = new VerticalPanel();

    arbitraryLabelsPanel = new FlowPanel();
    labelsPanel.add(arbitraryLabelsPanel);

    addPanel = new HorizontalPanel();
    addLabel = new Button(Util.C.buttonAddLabel());
    addLabel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doAddNew();
      }
    });

    initWidget(labelsPanel);
  }

  public void display(final List<ChangeLabel.LabelKey> labels,
      final boolean canEdit) {
    this.arbitraryLabels = labels;
    for (ChangeLabel.LabelKey a : labels) {
      initLabel(a.get(), canEdit);
    }

    if (canEdit) {
      initAddLabel();
      labelsPanel.add(addPanel);
    }
  }

  public void initAddLabel() {
    nameTxtBox = new HintTextBox();
    labelSuggestOracle =
        new LabelSuggestOracle(changeId, new HashSet<ChangeLabel.LabelKey>(
            arbitraryLabels));

    nameTxt =
        new SuggestBox(new RPCSuggestOracle(labelSuggestOracle), nameTxtBox);

    nameTxtBox.setVisibleLength(40);
    nameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          if (nameTxt.isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            doAddNew();
          }
        }
      }
    });

    nameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          doAddNew();
        }
      }
    });

    final TextBoxBaseListener validator = new TextBoxBaseListener(nameTxtBox) {
      @Override
      public void onTextChange(String old, String val) {
        if (!nameTxtBox.isHintOn() && !"".equals(val)
            && !ChangeLabel.LabelKey.hasValidCharacters(val)) {
          nameTxtBox.setText(old);
        } else {
          addLabel.setEnabled(ChangeLabel.LabelKey.isValid(val));
        }
      }
    };

    nameTxtBox.addKeyPressHandler(validator);
    nameTxtBox.addMouseUpHandler(validator);

    addPanel.add(nameTxt);

    addPanel.add(addLabel);
  }

  public String getText() {
    return nameTxtBox.getText();
  }

  public void setEnabled(boolean enabled) {
    addLabel.setEnabled(enabled);
    nameTxtBox.setEnabled(enabled);
  }

  public void setText(String text) {
    nameTxtBox.setText(text);
  }

  private void doAddNew() {
    addLabel.setEnabled(false);
    final String labelText = nameTxtBox.getText();
    if (arbitraryLabels != null) {
      if (!isLabelAdded(labelText)) {
        Util.MANAGE_SVC.addLabel(new ChangeLabel(changeId, labelText),
            new GerritCallback<VoidResult>() {
              @Override
              public void onSuccess(VoidResult result) {
                addLabel.setEnabled(true);
                arbitraryLabels.add(new ChangeLabel.LabelKey(labelText));
                initLabel(labelText, true);
              }

              @Override
              public void onFailure(final Throwable caught) {
                addLabel.setEnabled(true);
                super.onFailure(caught);
              }
            });
      } else {
        Window.alert(Util.C.duplicateLabel());
      }
    }
    nameTxtBox.setText("");
  }

  private void initLabel(final String labelKey, final boolean canDelete) {
    final Grid g = new Grid(1, 2);
    g.setStyleName(Gerrit.RESOURCES.css().labelsPanel());

    g.getCellFormatter().setStyleName(0, 0,
        Gerrit.RESOURCES.css().labelValueCell());
    g.getCellFormatter().setStyleName(0, 1,
        Gerrit.RESOURCES.css().labelDeleteCell());

    g.setWidget(0, 0, new LabelLink(labelKey));

    final Image upImage = new Image(Gerrit.RESOURCES.deleteHover());
    final PushButton deleteLabelButton = new PushButton(upImage);

    deleteLabelButton.setVisible(canDelete);
    deleteLabelButton.setStylePrimaryName(Gerrit.RESOURCES.css()
        .deleteLabelButton());
    deleteLabelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        Util.MANAGE_SVC.deleteLabel(new ChangeLabel(changeId, labelKey),
            new GerritCallback<VoidResult>() {
              @Override
              public void onSuccess(VoidResult result) {
                arbitraryLabels.remove(labelKey);
                labelSuggestOracle
                    .updateLabelsToExclude(new ChangeLabel.LabelKey(labelKey));
                g.removeFromParent();
              }
            });
      }
    });
    g.setWidget(0, 1, deleteLabelButton);
    arbitraryLabelsPanel.add(g);
  }

  private boolean isLabelAdded(final String newLabel) {
    return arbitraryLabels.contains(new ChangeLabel.LabelKey(newLabel));
  }
}
