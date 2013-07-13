// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

class ReplyBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, ReplyBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface Styles extends CssResource {
    String label_name();
    String label_value();
  }

  private final Change.Id changeId;
  private final String revision;
  private ReviewInput in = ReviewInput.create();

  @UiField Styles style;
  @UiField NpTextArea message;
  @UiField Grid labels;
  @UiField Button send;

  ReplyBox(
      Change.Id changeId,
      String revision,
      NativeMap<JsArrayString> permitted,
      Map<String, Short> currentLabels) {
    this.changeId = changeId;
    this.revision = revision;
    initWidget(uiBinder.createAndBindUi(this));

    List<String> names = new ArrayList<String>(permitted.keySet());
    if (names.isEmpty()) {
      labels.setVisible(false);
    } else {
      renderLabels(permitted, currentLabels, names);
    }
  }

  @Override
  protected void onLoad() {
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        message.setFocus(true);
      }});
  }

  @UiHandler("send")
  void onSend(ClickEvent e) {
    in.message(message.getText().trim());
    ChangeApi.revision(changeId.get(), revision)
      .view("review")
      .post(in, new GerritCallback<ReviewInput>() {
        @Override
        public void onSuccess(ReviewInput result) {
          Gerrit.display(PageLinks.toChange2(changeId));
        }
      });
    hide();
  }

  private void hide() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide();
        break;
      }
    }
  }

  private void renderLabels(
      NativeMap<JsArrayString> permitted,
      Map<String, Short> currentLabels,
      List<String> names) {
    Collections.sort(names);

    TreeSet<Short> values = new TreeSet<Short>();
    for (String id : names) {
      values.addAll(parseValues(permitted.get(id)));
    }
    List<Short> columns = new ArrayList<Short>(values);

    labels.resize(1 + names.size(), 1 + values.size());
    for (int c = 0; c < columns.size(); c++) {
      labels.setText(0, 1 + c, LabelValue.formatValue(columns.get(c)));
      labels.getCellFormatter().setStyleName(0, 1 + c, style.label_value());
    }

    List<String> checkboxes = new ArrayList<String>(names.size());
    int row = 1;
    for (String id : names) {
      Set<Short> vals = parseValues(permitted.get(id));
      if (isCheckBox(vals)) {
        checkboxes.add(id);
      } else {
        renderRadio(row++, id, columns, vals, currentLabels.get(id));
      }
    }
    for (String id : checkboxes) {
      renderCheckBox(row++, id, currentLabels.get(id));
    }
  }

  private void renderRadio(int row, final String id,
      List<Short> columns,
      Set<Short> values, Short current) {
    labels.setText(row, 0, id);
    labels.getCellFormatter().setStyleName(row, 0, style.label_name());

    for (int i = 0; i < columns.size(); i++) {
      final Short v = columns.get(i);
      if (values.contains(v)) {
        RadioButton b = new RadioButton(id);
        if ((current != null && v == current) || (current == null && v == 0)) {
          b.setValue(true);
        }
        b.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            in.label(id, v);
          }
        });
        labels.setWidget(row, 1 + i, b);
      }
    }
  }

  private void renderCheckBox(int row, final String id, Short current) {
    final CheckBox b = new CheckBox();
    b.setText(id);
    if (current != null && current == 1) {
      b.setValue(true);
    }
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        in.label(id, b.getValue() ? (short) 1 : (short) 0);
      }
    });
    b.setStyleName(style.label_name());
    labels.setWidget(row, 0, b);
  }

  private static boolean isCheckBox(Set<Short> values) {
    return values.size() == 2
        && values.contains((short) 0)
        && values.contains((short) 1);
  }

  private static TreeSet<Short> parseValues(JsArrayString v) {
    TreeSet<Short> values = new TreeSet<Short>();
    for (int i = 0; i < v.length(); i++) {
      values.add(parseValue(v.get(i)));
    }
    return values;
  }

  private static short parseValue(String formatted) {
    if (formatted.startsWith("+")) {
      formatted = formatted.substring(1);
    } else if (formatted.startsWith(" ")) {
      formatted = formatted.trim();
    }
    return Short.parseShort(formatted);
  }
}
