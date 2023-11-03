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

package com.google.gerrit.server.restapi.account;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesReader;
import com.google.gerrit.server.StarredChangesWriter;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class StarredChanges
    implements ChildCollection<AccountResource, AccountResource.StarredChange> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangesCollection changes;
  private final DynamicMap<RestView<AccountResource.StarredChange>> views;
  private final StarredChangesReader starredChangesReader;

  @Inject
  StarredChanges(
      ChangesCollection changes,
      DynamicMap<RestView<AccountResource.StarredChange>> views,
      StarredChangesReader starredChangesReader) {
    this.changes = changes;
    this.views = views;
    this.starredChangesReader = starredChangesReader;
  }

  @Override
  public AccountResource.StarredChange parse(AccountResource parent, IdString id)
      throws RestApiException, PermissionBackendException, IOException {
    IdentifiedUser user = parent.getUser();
    ChangeResource change = changes.parse(TopLevelResource.INSTANCE, id);
    if (starredChangesReader.isStarred(user.getAccountId(), change.getId())) {
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
    return (RestReadView<AccountResource>)
        self -> {
          QueryChanges query = changes.list();
          query.addQuery("has:star");
          return query.apply(TopLevelResource.INSTANCE);
        };
  }

  @Singleton
  public static class Create
      implements RestCollectionCreateView<AccountResource, AccountResource.StarredChange, Input> {
    private final Provider<CurrentUser> self;
    private final ChangesCollection changes;
    private final StarredChangesWriter starredChangesWriter;

    @Inject
    Create(
        Provider<CurrentUser> self,
        ChangesCollection changes,
        StarredChangesWriter starredChangesWriter) {
      this.self = self;
      this.changes = changes;
      this.starredChangesWriter = starredChangesWriter;
    }

    @Override
    public Response<?> apply(AccountResource rsrc, IdString id, Input in)
        throws RestApiException, IOException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed to add starred change");
      }

      ChangeResource change;
      try {
        change = changes.parse(TopLevelResource.INSTANCE, id);
      } catch (ResourceNotFoundException e) {
        throw new UnprocessableEntityException(String.format("change %s not found", id.get()), e);
      } catch (StorageException | PermissionBackendException | IOException e) {
        logger.atSevere().withCause(e).log("cannot resolve change");
        throw new UnprocessableEntityException("internal server error", e);
      }

      try {
        starredChangesWriter.star(self.get().getAccountId(), change.getId());
      } catch (DuplicateKeyException e) {
        return Response.none();
      }
      return Response.none();
    }
  }

  @Singleton
  public static class Put implements RestModifyView<AccountResource.StarredChange, Input> {
    private final Provider<CurrentUser> self;

    @Inject
    Put(Provider<CurrentUser> self) {
      this.self = self;
    }

    @Override
    public Response<?> apply(AccountResource.StarredChange rsrc, Input in) throws AuthException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed update starred changes");
      }
      return Response.none();
    }
  }

  @Singleton
  public static class Delete implements RestModifyView<AccountResource.StarredChange, Input> {
    private final Provider<CurrentUser> self;
    private final StarredChangesWriter starredChangesWriter;

    @Inject
    Delete(Provider<CurrentUser> self, StarredChangesWriter starredChangesWriter) {
      this.self = self;
      this.starredChangesWriter = starredChangesWriter;
    }

    @Override
    public Response<?> apply(AccountResource.StarredChange rsrc, Input in)
        throws AuthException, IOException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed remove starred change");
      }
      starredChangesWriter.unstar(self.get().getAccountId(), rsrc.getChange().getId());
      return Response.none();
    }
  }
}
