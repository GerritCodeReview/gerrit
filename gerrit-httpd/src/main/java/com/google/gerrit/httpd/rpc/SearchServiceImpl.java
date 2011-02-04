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
import com.google.gerrit.common.data.SearchService;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Search;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OwnerControl;
import com.google.gerrit.server.OwnerUtil;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.SearchControl;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.Set;

public class SearchServiceImpl extends OwnerBaseServiceImplementation implements
    SearchService {
  private final Provider<IdentifiedUser> identifiedUser;
  private final SearchControl.Factory searchControlFactory;

  @Inject
  SearchServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> identifiedUser,
      final OwnerControl.Factory ownerControlFactory,
      final OwnerUtil ownerUtil,
      final SearchControl.Factory searchControlFactory) {
    super(schema, identifiedUser, ownerControlFactory, ownerUtil);
    this.identifiedUser = identifiedUser;
    this.searchControlFactory = searchControlFactory;
  }

  @Override
  public void createSearch(final Search search,
      final AsyncCallback<String> callback) {
    run(callback, new Action<String>() {
      public String run(final ReviewDb db) throws OrmException, Failure {
        if (! isLoggedIn()) {
          throw new Failure(new Exception("You must be logged in to save a search"));
        }
        if (! "".equals(search.getName())) {
          return insert(db, search);
        }

        for (int n = 0; true; n++) {
          try {
            search.getKey().set("Untitled" + (n == 0 ? "" : n));
            return insert(db, search);
          } catch (OrmDuplicateKeyException e) {}
        }
      }
    });
  }

  private String insert(final ReviewDb db, final Search search)
      throws OrmException, Failure {
    Owner.Id id = search.getKey().getOwnerId();
    OwnerInfo owner = getOwnerInfo(id);
    search.setOwnerId(owner.getId());
    if (!search.isValid()) {
      throw new Failure(new Exception("Invalid search key"));
    }
    SearchControl c = validateSearchControlFor(search.getKey(),
      searchControlFactory);
    if (!c.canEdit()) {
      throw new Failure(new Exception("Permission denied inserting search"));
    }
    db.searches().insert(Collections.singleton(search));

    if (owner.getType() == Owner.Type.SITE) {
      return search.getName();
    }
    return owner.getOwnerForLink() + "," + search.getName();
  }

  @Override
  public void changeSearch(final Search.Key key, final Search search,
      final AsyncCallback<String> callback) {
    run(callback, new Action<String>() {
      public String run(final ReviewDb db) throws OrmException, Failure {
        if (! isLoggedIn()) {
          throw new Failure(new Exception("You must be logged in to edit a search"));
        }
        Owner.Id id = search.getKey().getOwnerId();
        OwnerInfo owner = getOwnerInfo(id);
        search.setOwnerId(owner.getId());
        SearchControl c = validateSearchControlFor(search.getKey(),
          searchControlFactory);
        if (!c.canEdit()) {
          throw new Failure(new Exception("Permission denied editing search"));
        }

        if (key.equals(search.getKey())) {
          final Search orig = db.searches().get(search.getKey());
          orig.setDescription(search.getDescription());
          orig.setQuery(search.getQuery());
          db.searches().update(Collections.singleton(orig));
        } else {
          if (!search.isValid()) {
            throw new Failure(new Exception("Invalid changed search key"));
          }
          db.searches().insert(Collections.singleton(search));
          delete(db, key);
        }
        if (owner.getType() == Owner.Type.SITE) {
          return search.getName();
        }
        return owner.getOwnerForLink() + "," + search.getName();
      }
    });
  }

  @Override
  public void deleteSearches(final Set<Search.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        if (! isLoggedIn()) {
          throw new Failure(new Exception("You must be logged in to delete a search"));
        }
        for (Search.Key k : keys) {
          k.setOwnerId(getOwnerInfo(k.getOwnerId()).getId());
          delete(db, k);
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private void delete(final ReviewDb db, final Search.Key key)
    throws OrmException, Failure {
    SearchControl c = validateSearchControlFor(key, searchControlFactory);
    if (!c.canEdit()) {
      throw new Failure(new Exception("Permission denied deleting search"));
    }
    final Search search = db.searches().get(key);
    db.searches().delete(Collections.singleton(search));
  }

  public static SearchControl validateSearchControlFor(Search.Key key,
      SearchControl.Factory factory) throws Failure {
    try {
      return factory.validateFor(key);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (NoSuchGroupException e) {
      throw new Failure(e);
    } catch (NoSuchProjectException e) {
      throw new Failure(e);
    }
  }
}
