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

package com.google.gerrit.client.searches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.client.ui.SwitchingSuggestOracle;
import com.google.gerrit.client.ui.TextBoxBaseListener;
import com.google.gerrit.common.data.SearchList;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.Search;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class SearchEditor extends Composite {
  private HintTextBox name;
  private NpTextArea description;
  private HintTextBox query;
  private NpTextBox owner;
  private SuggestBox ownerSug;
  private SwitchingSuggestOracle oracle;
  private ListBox type;

  private Button save;
  private Button insert;
  private Button cancel;

  private OnEditEnabler saveEnabler;
  private OnEditEnabler insertEnabler;

  private final int nrows = 5;
  private Grid grid = new Grid(nrows, 2);

  private SearchList list;
  private Search search;
  private int r_owner;

  public SearchEditor(Search search) {
    createWidgets();

    setupSaveEnablers();

    name.setVisibleLength(45);
    description.setWidth("99%");
    query.setWidth("99%");
    owner.setVisibleLength(45);

    int r = 0, c = 0;
    grid.setText(r++, c, Util.C.columnSearchEditName());
    grid.setText(r++, c, Util.C.columnSearchEditDescription());
    grid.setText(r++, c, Util.C.columnSearchEditQuery());
    grid.setText(r++, c, Util.C.columnSearchEditType());
    grid.setText(r++, c, "");

    r = 0; c++;
    grid.setWidget(r++, c, name);
    grid.setWidget(r++, c, description);
    grid.setWidget(r++, c, query);
    grid.setWidget(r++, c, type);
    r_owner = r;
    grid.setWidget(r++, c, new Label(""));
    grid.setStyleName(Gerrit.RESOURCES.css().searchEditor());


    final FlowPanel buttons = new FlowPanel();
    buttons.add(save);
    buttons.add(insert);
    buttons.add(cancel);


    final FlowPanel fp = new FlowPanel();
    fp.add(grid);
    fp.add(buttons);
    setWidget(fp);

    setSearch(search);
  }

  public void createWidgets() {
    save = new Button(Util.C.buttonSaveSearch());
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onSave(false);
      }
    });

    insert = new Button(Util.C.buttonInsertSearch());
    insert.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onSave(true);
      }
    });

    cancel = new Button(Util.C.buttonCancelSearch());
    cancel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onCancel();
      }
    });

    name = new HintTextBox();
    name.setHintText(Util.C.defaultSearchName());

    TextBoxBaseListener validator = new TextBoxBaseListener(name) {
      @Override
      public void onTextChange(String old, String val) {
        if (! name.isHintOn() && ! Search.Key.isValidName(val)
            && ! "".equals(val)) { // Need to let users delete all chars
          name.setText(old);
        }
      }
    };

    description = new NpTextArea();
    query = new HintTextBox();
    type = new ListBox();
    for (Owner.Type t : Owner.Type.values()) {
      type.addItem(t.getId(), t.getId());
    }
    type.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        onTypeChange();
      }
    });
    owner = new NpTextBox();

    SuggestOracle[] oracles = new SuggestOracle[2];
    oracles[0] = new AccountGroupSuggestOracle();
    oracles[1] = new ProjectNameSuggestOracle();
    oracle = new SwitchingSuggestOracle(oracles);
    ownerSug = new SuggestBox(new RPCSuggestOracle(oracle), owner);

    saveEnabler = new OnEditEnabler(save);
    insertEnabler = new OnEditEnabler(insert);
 }

  public void onTypeChange() {
    displayOwner();
    displayButtons();
  }

  public void setSearch(Search s) {
    search = s;
    if (s == null) {
      return;
    }

    name.setText(s.getName());
    description.setText(s.getDescription());
    query.setText(s.getQuery());
    displayType(s.getType() != null ? s.getType() : Owner.Type.USER);

    owner.setText(getOwnerForDisplay());
    displayOwner();

    save.setEnabled(false);
    displayButtons();
  };

  protected Owner.Type getType() {
    return Owner.Type.forId(type.getValue(type.getSelectedIndex()));
  }

  public void setSearchList(SearchList list) {
    this.list = list;
    displayType(list.getType());
    setSearch(null);
  }

  protected String getOwnerForDisplay() {
    if (list != null) {
      return list.getOwnerInfo().getOwnerForDisplay();
    }
    return "";
  }

  public void setupSaveEnablers() {
    saveEnabler.listenTo(name);
    saveEnabler.listenTo(description);
    saveEnabler.listenTo(query);
    saveEnabler.listenTo(owner);

    insertEnabler.listenTo(name);
    insertEnabler.listenTo(owner);
  }

  protected void displayOwner() {
    switch(getType()) {
      case USER:
      case SITE:
          grid.setText(r_owner, 0, "");
          grid.setText(r_owner, 1, "");
        break;
      case GROUP:
          grid.setText(r_owner, 0, Util.C.columnSearchEditGroup());
          grid.setWidget(r_owner, 1, ownerSug);
          oracle.setIndex(0);
        break;
      case PROJECT:
          grid.setText(r_owner, 0, Util.C.columnSearchEditProject());
          grid.setWidget(r_owner, 1, ownerSug);
          oracle.setIndex(1);
        break;
    }

    if (search.getType() != getType()) {
      if (search != null && getOwnerForDisplay().equals(owner.getText())) {
        owner.setText("");
      }
    } else if ("".equals(owner.getText())) {
      owner.setText(getOwnerForDisplay());
    }
  }

  protected void displayButtons() {
    cancel.setEnabled(true);
    if (search.getKey() == null || ! search.getKey().isValid()) {
      save.setVisible(false);
      insert.setEnabled(true);
    } else if (list != null && ! list.isEditable()) {
      save.setVisible(false);
      insert.setEnabled(false);
      // How could the insertEnabler be disabled here until the type changes?
    } else {
      save.setVisible(search.getType() == getType());
      if (search.getType() == getType()) {
        insert.setEnabled("".equals(search.getName()));
      } else {
        insert.setEnabled(true);
      }
    }
  }

  protected void displayType(Owner.Type t) {
    int i = 0;
    for (Owner.Type st : Owner.Type.values()) {
      if (st.equals(t)) {
        type.setSelectedIndex(i);
      }
      i++;
    }
  }

  public void onSave(boolean insert) {
    save.setEnabled(false);
    this.insert.setEnabled(false);
    cancel.setEnabled(false);

    String id = owner.getText();
    if (getType() == Owner.Type.USER || getType() == Owner.Type.SITE) {
      id = "";
    } else if (getOwnerForDisplay().equals(id)) {
      // When the owner doesn't change, keep the same id on "updated"
      // search so that others may detect changes based on the id.
      id = search.getOwner();
    }

    final Search.Key key = new Search.Key(getType(), id, name.getText());
    final Search updated = new Search(key);

    updated.setDescription(description.getText());
    updated.setQuery(query.getText());

    final GerritCallback cb = new GerritCallback<String>() {
      @Override
      public void onFailure(Throwable caught) {
        save.setEnabled(true);
        cancel.setEnabled(true);
        super.onFailure(caught);
      }

      @Override
      public final void onSuccess(String linkName) {
        onSaveSuccess(updated, linkName);
      }
    };

    if (insert || search.getKey() == null || ! search.getKey().isValid()) {
      Util.SEARCH_SVC.createSearch(updated, cb);
    } else {
      Util.SEARCH_SVC.changeSearch(search.getKey(), updated, cb);
    }
  }

  public void onSaveSuccess(Search updated, String linkName) {
    setSearch(null);
  }

  public void onCancel() {
    setSearch(null);
  }
}
