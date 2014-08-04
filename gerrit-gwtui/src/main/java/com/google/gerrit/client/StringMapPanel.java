package com.google.gerrit.client;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HasEnabled;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class StringMapPanel extends HorizontalPanel implements HasEnabled {
  private final String[] FILTER_LABELS =
    {"Filename", "Section", "Subsection", "Name"};
  private VerticalPanel filterPanel;
  private StringListPanel valuePanel;
  private ListBox configList;
  private NpTextBox[] filters;
  private NativeMap<NativeMap<NativeMap<NativeMap<JsArrayString>>>> map;

  public StringMapPanel(FocusWidget w,
      NativeMap<NativeMap<NativeMap<NativeMap<JsArrayString>>>> map) {
    this.map = map;
    this.setStyleName(Gerrit.RESOURCES.css().filterPanel());
    renderFilterPanel();
    renderValuePanel(w);
  }

  private void renderFilterPanel() {
    filterPanel = new VerticalPanel();

    Label header = new Label();
    header.setStyleName(Gerrit.RESOURCES.css().filterRow());
    header.setText("Filters");

    filterPanel.add(header);
    addConfigList();
    addFilterBoxes();

    add(filterPanel);
  }

  private void addConfigList() {
    HorizontalPanel listRow = new HorizontalPanel();
    listRow.setStyleName(Gerrit.RESOURCES.css().filterRow());
    Label label = new Label(FILTER_LABELS[0]);
    listRow.add(label);

    configList = new ListBox();
    configList.setStyleName(Gerrit.RESOURCES.css().filterInputType());
    configList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        updateValuePanel();
      }
    });
    Set<String> configs = map.keySet();
    for (String config : configs) {
      configList.addItem(config);
    }
    listRow.add(configList);
    filterPanel.add(listRow);
  }

  private void addFilterBoxes() {
    filters = new NpTextBox[3];
    for(int i = 0; i < filters.length; i++) {
      HorizontalPanel row = new HorizontalPanel();
      row.setStyleName(Gerrit.RESOURCES.css().filterRow());
      Label l = new Label(FILTER_LABELS[i + 1]);
      row.add(l);

      filters[i] = new NpTextBox();
      filters[i].setStyleName(Gerrit.RESOURCES.css().filterInputType());
      filters[i].setVisibleLength(15);
      filters[i].addKeyUpHandler(new KeyUpHandler() {
        @Override
        public void onKeyUp(KeyUpEvent event) {
          updateValuePanel();
        }
      });
      row.add(filters[i]);
      filterPanel.add(row);
    }
  }

  private void renderValuePanel(FocusWidget w) {
    List<String> header = new ArrayList<String>();
    header.add("Values");
    valuePanel = new StringListPanel(null, header , w, false);
    valuePanel.setEnabled(true);
    add(valuePanel);
  }

  private void updateValuePanel() {
    List<String> values = getValuesBasedOnFilters();
    if (values == null) {
      valuePanel.display(null);
    } else {
      List<List<String>> entries = new ArrayList<>();
      for (String v : values) {
        entries.add(Arrays.asList(v));
      }
      valuePanel.display(entries);
    }
  }

  private List<String> getValuesBasedOnFilters() {
    NativeMap<NativeMap<NativeMap<JsArrayString>>> sections =
        map.get(configList.getItemText(configList.getSelectedIndex()));
    if (sections.containsKey(filters[0].getValue())) {
      NativeMap<NativeMap<JsArrayString>> subsection =
          sections.get(filters[0].getValue());
      if (subsection.containsKey(filters[1].getValue())) {
        NativeMap<JsArrayString> name = subsection.get(filters[1].getValue());
        if (name.containsKey(filters[2].getValue())) {
          JsArrayString values = name.get(filters[2].getValue());
          return Natives.asList(values);
        }
      }
    }
    return null;
  }

  public String[] getFilterValues() {
    String[] values = new String[filters.length + 1];
    values[0] = configList.getItemText(configList.getSelectedIndex());
    for (int i = 0; i < filters.length; i++) {
      values[i + 1] = filters[i].getValue();
    }
    return values;
  }

  public List<String> getListValues() {
    return valuePanel.getValues(0);
  }

  @Override
  public boolean isEnabled() {
    return configList.isVisible();
  }

  @Override
  public void setEnabled(boolean enabled) {
    for (NpTextBox filter : filters) {
      filter.setEnabled(enabled);
    }
    configList.setVisible(enabled);
  }
}