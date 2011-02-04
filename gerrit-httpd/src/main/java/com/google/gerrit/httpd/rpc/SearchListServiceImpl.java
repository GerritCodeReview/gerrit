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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.OwnerInfo;
import com.google.gerrit.common.data.SearchList;
import com.google.gerrit.common.data.SearchListService;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Search;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OwnerControl;
import com.google.gerrit.server.OwnerUtil;
import com.google.gerrit.server.query.SearchControl;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;

public class SearchListServiceImpl extends OwnerBaseServiceImplementation implements
    SearchListService {

  private final Provider<IdentifiedUser> currentUser;
  private final SearchControl.Factory searchControlFactory;

  @Inject
  SearchListServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final SearchControl.Factory searchControlFactory,
      final OwnerControl.Factory ownerControlFactory,
      final OwnerUtil ownerUtil) {
    super(schema, currentUser, ownerControlFactory, ownerUtil);
    this.currentUser = currentUser;
    this.searchControlFactory = searchControlFactory;
  }

  @Override
  public void getSearches(final Owner.Id id,
      final AsyncCallback<List<Search>> callback) {
    run(callback, new Action<List<Search>>() {
      public List<Search> run(final ReviewDb db) throws OrmException, Failure {
        return visibleFor(db, getOwnerInfo(id).getId());
      }
    });
  }

  @Override
  public void getSearchList(final Owner.Id id,
      final AsyncCallback<SearchList> callback) {
    run(callback, new Action<SearchList>() {
      public SearchList run(final ReviewDb db) throws OrmException, Failure {
        SearchList list = new SearchList(new OwnerInfo(id.getType()));

        if (! "".equals(id.get())) {
          IdentifiedUser user = null;
          if (isLoggedIn()) {
            user = currentUser.get();
          }
          list.setOwnerInfo(getOwnerInfo(id));
        } else if (id.getType() == Owner.Type.USER && isLoggedIn()) {
          list.setOwnerInfo(new OwnerInfo(currentUser.get().getAccount()));
        } else if (id.getType() != Owner.Type.SITE) {
          return list;
        }

        try {
          list.setEditable(validateOwnerControlFor(list.getOwnerInfo().getId())
            .canEdit());
        } catch (Exception e) {
          throw new Failure(e);
        }
        list.setSearches(visibleFor(db, list.getOwnerInfo().getId()));
        return list;
      }
    });
  }

  private List<Search> visibleFor(final ReviewDb db, final Owner.Id id)
      throws OrmException, Failure {
    validateOwnerControlFor(id);
    List<Search> searches = db.searches().byType(id.getType().getCode(),
      id.get()).toList();
    List<Search> visible = new ArrayList<Search>(searches.size());
    for(Search s : searches) {
      try {
        SearchControl c = searchControlFactory.validateFor(s.getKey());
        if (c.isVisible()) {
          visible.add(s);
        }
      } catch (Exception e) {}
    }
    return visible;
  }
}
