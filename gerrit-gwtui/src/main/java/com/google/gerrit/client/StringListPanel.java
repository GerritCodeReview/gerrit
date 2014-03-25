// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.ArrayList;
import java.util.List;

public class StringListPanel extends FlowPanel {
  private final StringListTable t;
  private final Button deleteButton;
  private final HorizontalPanel titlePanel;
  private Image info;
  private FocusWidget widget;

  public StringListPanel(String title, List<String> fieldNames, FocusWidget w) {
    widget = w;
    titlePanel = new HorizontalPanel();
    SmallHeading titleLabel = new SmallHeading(title);
    titlePanel.add(titleLabel);
    add(titlePanel);

    t = new StringListTable(fieldNames);
    add(t);

    deleteButton = new Button(Gerrit.C.stringListPanelDelete());
    deleteButton.setEnabled(false);
    add(deleteButton);
    deleteButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        widget.setEnabled(true);
        t.deleteChecked();
      }
    });
  }

  public void display(List<List<String>> values) {
    t.display(values);
  }

  public void setInfo(String msg) {
    if (info == null) {
      info = new Image(Gerrit.RESOURCES.info());
      titlePanel.add(info);
    }
    info.setTitle(msg);
  }

  public List<List<String>> getValues() {
    return t.getValues();
  }

  private class StringListTable extends NavigationTable<List<String>> {
    private final List<NpTextBox> inputs;

    StringListTable(List<String> names) {
      Button addButton =
          new Button(new ImageResourceRenderer().render(Gerrit.RESOURCES.listAdd()));
      addButton.setTitle(Gerrit.C.stringListPanelAdd());
      OnEditEnabler e = new OnEditEnabler(addButton);
      inputs = new ArrayList<>();

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().iconHeader());
      for (int i = 0; i < names.size(); i++) {
        fmt.addStyleName(0, i + 1, Gerrit.RESOURCES.css().dataHeader());
        table.setText(0, i + 1, names.get(i));

        NpTextBox input = new NpTextBox();
        input.setVisibleLength(35);
        input.addKeyPressHandler(new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              widget.setEnabled(true);
              add();
            }
          }
        });
        inputs.add(input);
        fmt.addStyleName(1, i + 1, Gerrit.RESOURCES.css().dataHeader());
        table.setWidget(1, i + 1, input);
        e.listenTo(input);
      }
      addButton.setEnabled(false);

      addButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          widget.setEnabled(true);
          add();
        }
      });
      table.setWidget(1, 0, addButton);
    }

    void display(List<List<String>> values) {
      for (int row = 2; row < table.getRowCount(); row++) {
        table.removeRow(row--);
      }
      int row = 2;
      for (List<String> v : values) {
        populate(row, v);
        row++;
      }
    }

    List<List<String>> getValues() {
      List<List<String>> values = new ArrayList<>();
      for (int row = 2; row < table.getRowCount(); row++) {
        List<String> v = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
          v.add(table.getText(row, i + 1));
        }
        values.add(v);
      }
      return values;
    }

    private void populate(int row, List<String> values) {
      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().iconCell());
      CheckBox checkBox = new CheckBox();
      checkBox.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          enableDelete();
        }
      });
      table.setWidget(row, 0, checkBox);
      for (int i = 0; i < values.size(); i++) {
        fmt.addStyleName(row, i + 1, Gerrit.RESOURCES.css().dataCell());
        table.setText(row, i + 1, values.get(i));
      }
    }

    void add() {
      List<String> values = new ArrayList<>();
      for (NpTextBox input : inputs) {
        String v = input.getValue().trim();
        if (!v.isEmpty()) {
          input.setValue("");
          values.add(v);
        }
      }
      t.insert(values);
    }

    void insert(List<String> v) {
      int insertPos = table.getRowCount();
      for (int row = 1; row < table.getRowCount(); row++) {
        int compareResult = v.get(0).compareTo(table.getText(row, 1));
        if (compareResult < 0)  {
          insertPos = row;
          break;
        } else if (compareResult == 0) {
          return;
        }
      }
      table.insertRow(insertPos);
      populate(insertPos, v);
    }

    void enableDelete() {
      for (int row = 2; row < table.getRowCount(); row++) {
        if (((CheckBox) table.getWidget(row, 0)).getValue()) {
          deleteButton.setEnabled(true);
          return;
        }
      }
      deleteButton.setEnabled(false);
    }

    void deleteChecked() {
      deleteButton.setEnabled(false);
      for (int row = 2; row < table.getRowCount(); row++) {
        if (((CheckBox) table.getWidget(row, 0)).getValue()) {
          table.removeRow(row--);
        }
      }
    }

    @Override
    protected void onOpenRow(int row) {
    }

    @Override
    protected Object getRowItemKey(List<String> item) {
      return item.get(0);
    }
  }
}
