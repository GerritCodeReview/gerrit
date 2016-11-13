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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.query.change.QueryChanges;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StarredChanges
    implements ChildCollection<AccountResource, AccountResource.StarredChange>,
        AcceptsCreate<AccountResource> {
  private static final Logger log = LoggerFactory.getLogger(StarredChanges.class);

  private final ChangesCollection changes;
  private final DynamicMap<RestView<AccountResource.StarredChange>> views;
  private final Provider<Create> createProvider;
  private final StarredChangesUtil starredChangesUtil;

  @Inject
  StarredChanges(
      ChangesCollection changes,
      DynamicMap<RestView<AccountResource.StarredChange>> views,
      Provider<Create> createProvider,
      StarredChangesUtil starredChangesUtil) {
    this.changes = changes;
    this.views = views;
    this.createProvider = createProvider;
    this.starredChangesUtil = starredChangesUtil;
  }

  @Override
  public AccountResource.StarredChange parse(AccountResource parent, IdString id)
      throws ResourceNotFoundException, OrmException {
    IdentifiedUser user = parent.getUser();
    ChangeResource change = changes.parse(TopLevelResource.INSTANCE, id);
    if (starredChangesUtil
        .getLabels(user.getAccountId(), change.getId())
        .contains(StarredChangesUtil.DEFAULT_LABEL)) {
      return new AccountResource.StarredChange(user, change);
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<AccountResource.StarredChange>> views() {
    return views;
  }

  @Override
  public RestView<AccountResource> list() throws ResourceNotFoundException {
    return new RestReadView<AccountResource>() {
      @Override
      public Object apply(AccountResource self)
          throws BadRequestException, AuthException, OrmException {
        QueryChanges query = changes.list();
        query.addQuery("starredby:" + self.getUser().getAccountId().get());
        return query.apply(TopLevelResource.INSTANCE);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public RestModifyView<AccountResource, EmptyInput> create(AccountResource parent, IdString id)
      throws UnprocessableEntityException {
    try {
      return createProvider.get().setChange(changes.parse(TopLevelResource.INSTANCE, id));
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
    private final StarredChangesUtil starredChangesUtil;
    private ChangeResource change;

    @Inject
    Create(Provider<CurrentUser> self, StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    public Create setChange(ChangeResource change) {
      this.change = change;
      return this;
    }

    @Override
    public Response<?> apply(AccountResource rsrc, EmptyInput in)
        throws AuthException, OrmException, IOException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed to add starred change");
      }
      try {
        starredChangesUtil.star(
            self.get().getAccountId(),
            change.getProject(),
            change.getId(),
            StarredChangesUtil.DEFAULT_LABELS,
            null);
      } catch (OrmDuplicateKeyException e) {
        return Response.none();
      }
      return Response.none();
    }
  }

  @Singleton
  static class Put implements RestModifyView<AccountResource.StarredChange, EmptyInput> {
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
  public static class Delete implements RestModifyView<AccountResource.StarredChange, EmptyInput> {
    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;

    @Inject
    Delete(Provider<CurrentUser> self, StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    @Override
    public Response<?> apply(AccountResource.StarredChange rsrc, EmptyInput in)
        throws AuthException, OrmException, IOException {
      if (self.get() != rsrc.getUser()) {
        throw new AuthException("not allowed remove starred change");
      }
      starredChangesUtil.star(
          self.get().getAccountId(),
          rsrc.getChange().getProject(),
          rsrc.getChange().getId(),
          null,
          StarredChangesUtil.DEFAULT_LABELS);
      return Response.none();
    }
  }

  public static class EmptyInput {}
}
