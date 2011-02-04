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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.data.OwnerInfo;
import com.google.gerrit.common.data.SearchList;
import com.google.gerrit.reviewdb.Search;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;

import java.util.List;

public class SearchScreen extends Screen {
  protected SearchList list;
  protected SearchTable table;
  protected Search.Key key;

  protected Button delete;
  protected Button insert;

  protected ScreenLoadCallback<List<Search>> cbSearches =
      new ScreenLoadCallback<List<Search>>(this) {
        @Override
        protected void preDisplay(final List<Search> searches) {
          displaySearches(searches);
        }

        @Override
        protected void postDisplay() {
        }
      };

  protected ScreenLoadCallback<SearchList> cbList =
      new ScreenLoadCallback<SearchList>(this) {
        @Override
        protected void preDisplay(final SearchList list) {
          display(list);
        }

        @Override
        protected void postDisplay() {
        }
      };

  protected GerritCallback cbDeletes = new GerritCallback() {
      public final void onSuccess(Object obj) {
        loadSearches();
      }
    };

  public SearchScreen(final Search.Key key) {
    super();
    this.key = key;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    delete = new Button(Util.C.buttonDeleteSearches());
    delete.addClickHandler(new ClickHandler() {
        public void onClick(ClickEvent event) {
          onDelete();
        }
      });
    delete.setVisible(false);
    delete.setEnabled(false);

    insert = new Button(Util.C.buttonInsertNewSearch());
    insert.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        insert.setVisible(false);
        table.showEditor(-1);
      }
    });
    insert.setVisible(false);

    table = new SearchTable() {
      @Override
      public void onSearchChecked(boolean checked) {
        delete.setEnabled(checked);
      }

      @Override
      protected SearchEditor createSearchEditor() {
        return new Editor(null) {
          @Override
          public void setSearch(Search s) {
            if (Gerrit.isSignedIn()) {
            super.setSearch(s);
              if (list == null) {
                insert.setVisible(false);
              } else {
                insert.setVisible(s == null && list.isEditable());
              }
            }
          }

          @Override
          public void onSaveSuccess(Search updated, String linkName) {
            if (updated.getType() == key.getType()) {
              if (updated.getOwner().equals(key.getOwner())) {
                // stay on this screen
                loadSearches();
                super.onSaveSuccess(updated, linkName);
                return;
              }
            }
            History.newItem(Dispatcher.toSearches(updated.getType(),
              linkName));
          }
        };
      }
    };

    add(table);
    add(delete);
    add(insert);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    refresh();
  }

  public void refresh() {
    loadSearchList();
  }

  public void loadSearchList() {
    Util.SEARCH_LIST_SVC.getSearchList(key.getOwnerId(), cbList);
  }

  public void loadSearches() {
    Util.SEARCH_LIST_SVC.getSearches(key.getOwnerId(), cbSearches);
  }

  private void displaySearches(List<Search> searches) {
    list.setSearches(searches);
    table.refresh(key);
  }

  protected void display(final SearchList list) {
    this.list = list;
    OwnerInfo owner = list.getOwnerInfo();
    key = new Search.Key(owner.getId(), key.get());

    String token = Dispatcher.toSearches(owner.getType(),
      owner.getOwnerForLink(), key.get());
    setToken(token);

    displayTitle();

    table.setSearchList(list, key);
    table.setSavePointerId(token);
    table.finishDisplay();
    table.hideEditor();

    insert.setVisible(list.isEditable());
    delete.setVisible(list.isEditable());

    display();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  public void onDelete() {
    delete.setEnabled(false);
    Util.SEARCH_SVC.deleteSearches(table.getCheckedSearchKeys(), cbDeletes);
  }

  protected void displayTitle() {
    String title = getSearchTitle();
    setPageTitle(title);
    setWindowTitle(title);
  }

  protected String getSearchTitle() {
    String owner = list.getOwnerInfo().getOwnerForDisplay();
    if ("".equals(owner)) {
      switch(key.getType()) {
        case USER:    return Util.C.titleSearchUser();
        case GROUP:   return Util.C.titleSearchGroup();
        case PROJECT: return Util.C.titleSearchProject();
        case SITE:    return Util.C.titleSearchSite();
      }
    } else {
      switch(key.getType()) {
        case USER:    return Util.M.titleSearchUser(owner);
        case GROUP:   return Util.M.titleSearchGroup(owner);
        case PROJECT: return Util.M.titleSearchProject(owner);
        case SITE:    return Util.C.titleSearchSite();
      }
    }
    return "";
  }
}