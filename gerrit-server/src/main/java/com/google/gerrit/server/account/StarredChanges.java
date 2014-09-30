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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.query.change.QueryChanges;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Singleton
public class StarredChanges implements
    ChildCollection<AccountResource, AccountResource.StarredChange>,
    AcceptsCreate<AccountResource> {
  private static final Logger log = LoggerFactory.getLogger(StarredChanges.class);

  private final ChangesCollection changes;
  private final DynamicMap<RestView<AccountResource.StarredChange>> views;
  private final Provider<Create> createProvider;

  @Inject
  StarredChanges(ChangesCollection changes,
      DynamicMap<RestView<AccountResource.StarredChange>> views,
      Provider<Create> createProvider) {
    this.changes = changes;
    this.views = views;
    this.createProvider = createProvider;
  }

  @Override
  public AccountResource.StarredChange parse(AccountResource parent, IdString id)
      throws ResourceNotFoundException, OrmException {
    IdentifiedUser user = parent.getUser();
    try {
      user.asyncStarredChanges();

      ChangeResource change = changes.parse(TopLevelResource.INSTANCE, id);
      if (user.getStarredChanges().contains(change.getChange().getId())) {
        return new AccountResource.StarredChange(user, change);
      }
      throw new ResourceNotFoundException(id);
    } finally {
      user.abortStarredChanges();
    }
  }

  @Override
  public DynamicMap<RestView<AccountResource.StarredChange>> views() {
    return views;
  }

  @Override
  public RestView<AccountResource> list() throws ResourceNotFoundException {
    return new RestReadView<AccountResource>() {
      @Override
      public Object apply(AccountResource self) throws BadRequestException,
          AuthException, OrmException {
        QueryChanges query = changes.list();
        query.addQuery("starredby:" + self.getUser().getAccountId().get());
        return query.apply(TopLevelResource.INSTANCE);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public RestModifyView<AccountResource, EmptyInput> create(
      AccountResource parent, IdString id) throws UnprocessableEntityException{
    try {
      return createProvider.get()
          .setChange(changes.parse(TopLevelResource.INSTANCE, id));
    } catch (ResourceNotFoundException e) {
      throw new UnprocessableEntityException(String.format("change %s not found", id.get()));
    } catch (OrmException e) {
      log.error("cannot resolve change", e);
      throw new UnprocessableEntityException("internal server error");
    }
  }

  @Singleton
  public static class Create implements RestModifyView<AccountResource, EmptyInput> {
    private final Provider<CurrentUser> self;
    private final Provider<ReviewDb> dbProvider;
    private ChangeResource change;

    @Inject
    Create(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider) {
      this.self = self;
      this.dbProvider = dbProvider;
    }

    public Create setChange(ChangeResource change) {
      this.change = change;
      return this;
    }

    @Override
    public Response<?> apply(AccountResource rsrc, EmptyInput in)
        throws AuthException, OrmException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed to add starred change");
      }
      try {
        dbProvider.get().starredChanges().insert(Collections.singleton(
            new StarredChange(new StarredChange.Key(
                rsrc.getUser().getAccountId(),
                change.getChange().getId()))));
      } catch (OrmDuplicateKeyException e) {
        return Response.none();
      }
      return Response.none();
    }
  }

  @Singleton
  static class Put implements
      RestModifyView<AccountResource.StarredChange, EmptyInput> {
    private final Provider<CurrentUser> self;

    @Inject
    Put(Provider<CurrentUser> self) {
      this.self = self;
    }

    @Override
    public Response<?> apply(AccountResource.StarredChange rsrc, EmptyInput in)
        throws AuthException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed update starred changes");
      }
      return Response.none();
    }
  }

  @Singleton
  public static class Delete implements
      RestModifyView<AccountResource.StarredChange, EmptyInput> {
    private final Provider<CurrentUser> self;
    private final Provider<ReviewDb> dbProvider;

    @Inject
    Delete(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider) {
      this.self = self;
      this.dbProvider = dbProvider;
    }

    @Override
    public Response<?> apply(AccountResource.StarredChange rsrc,
        EmptyInput in) throws AuthException, OrmException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed remove starred change");
      }
      dbProvider.get().starredChanges().delete(Collections.singleton(
          new StarredChange(new StarredChange.Key(
              rsrc.getUser().getAccountId(),
              rsrc.getChange().getId()))));
      return Response.none();
    }
  }

  public static class EmptyInput {
  }
}
