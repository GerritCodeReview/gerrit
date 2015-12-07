// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountResource.Star;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.query.change.QueryChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;

@Singleton
public class Stars
    implements ChildCollection<AccountResource, AccountResource.Star>,
    AcceptsCreate<AccountResource> {
  private static final Logger log = LoggerFactory.getLogger(Stars.class);

  private final Provider<CurrentUser> self;
  private final ChangesCollection changes;
  private final StarredChangesUtil starredChangesUtil;
  private final DynamicMap<RestView<AccountResource.Star>> views;
  private final Provider<Create> createProvider;

  @Inject
  Stars(Provider<CurrentUser> self,
      ChangesCollection changes,
      StarredChangesUtil starredChangesUtil,
      DynamicMap<RestView<AccountResource.Star>> views,
      Provider<Create> createProvider) {
    this.self = self;
    this.changes = changes;
    this.starredChangesUtil = starredChangesUtil;
    this.views = views;
    this.createProvider = createProvider;
  }

  @Override
  public Star parse(AccountResource parent, IdString id)
      throws ResourceNotFoundException, OrmException {
    IdentifiedUser user = parent.getUser();
    ChangeResource change = changes.parse(TopLevelResource.INSTANCE, id);
    Set<String> labels =
        starredChangesUtil.getLabels(user.getAccountId(), change.getId());
    if (!labels.isEmpty()) {
      return new AccountResource.Star(user, change, labels);
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<Star>> views() {
    return views;
  }

  @Override
  public RestView<AccountResource> list()
      throws ResourceNotFoundException {
    return new RestReadView<AccountResource>() {
      @Override
      public Object apply(AccountResource rsrc) throws BadRequestException,
          AuthException, OrmException {
        if (self.get() != rsrc.getUser()) {
          throw new AuthException("not allowed to list starred changes");
        }
        QueryChanges query = changes.list();
        query.addQuery("has:stars");
        return query.apply(TopLevelResource.INSTANCE);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public RestModifyView<AccountResource, StarsInput> create(AccountResource parent,
      IdString id) throws RestApiException {
    try {
      return createProvider.get()
          .setChange(changes.parse(TopLevelResource.INSTANCE, id));
    } catch (ResourceNotFoundException e) {
      throw new UnprocessableEntityException(
          String.format("change %s not found", id.get()));
    } catch (OrmException e) {
      log.error("cannot resolve change", e);
      throw new UnprocessableEntityException("internal server error");
    }
  }

  @Singleton
  public static class Create implements RestModifyView<AccountResource, StarsInput> {
    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;
    private ChangeResource change;

    @Inject
    Create(Provider<CurrentUser> self,
        StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    public Create setChange(ChangeResource change) {
      this.change = change;
      return this;
    }

    @Override
    public SortedSet<String> apply(AccountResource rsrc, StarsInput in)
        throws AuthException, OrmException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed to star change");
      }
      return starredChangesUtil.star(self.get().getAccountId(), change.getId(),
          in.add, in.remove);
    }
  }

  @Singleton
  public static class Get implements
      RestReadView<AccountResource.Star> {
    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;

    @Inject
    Get(Provider<CurrentUser> self,
        StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    @Override
    public SortedSet<String> apply(AccountResource.Star rsrc) throws AuthException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed to get stars");
      }
      return starredChangesUtil.getLabels(self.get().getAccountId(),
          rsrc.getChange().getId());
    }
  }

  @Singleton
  public static class Post implements
      RestModifyView<AccountResource.Star, StarsInput> {
    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;

    @Inject
    Post(Provider<CurrentUser> self,
        StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    @Override
    public Collection<String> apply(AccountResource.Star rsrc, StarsInput in)
        throws AuthException, OrmException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed to update stars");
      }
      return starredChangesUtil.star(self.get().getAccountId(),
          rsrc.getChange().getId(), in.add, in.remove);
    }
  }
}
