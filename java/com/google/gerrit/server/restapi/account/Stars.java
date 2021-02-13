// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.IllegalLabelException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountResource.Star;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Implements adding label stars to changes.
 *
 * <p>This handles {@code POST} and {@code GET} for {@code
 * /accounts/<account-identifier>/stars.changes/<change ID>}.
 */
@Singleton
public class Stars implements ChildCollection<AccountResource, Star> {

  private final ChangesCollection changes;
  private final ListStarredChanges listStarredChanges;
  private final StarredChangesUtil starredChangesUtil;
  private final DynamicMap<RestView<Star>> views;

  @Inject
  Stars(
      ChangesCollection changes,
      ListStarredChanges listStarredChanges,
      StarredChangesUtil starredChangesUtil,
      DynamicMap<RestView<Star>> views) {
    this.changes = changes;
    this.listStarredChanges = listStarredChanges;
    this.starredChangesUtil = starredChangesUtil;
    this.views = views;
  }

  @Override
  public Star parse(AccountResource parent, IdString id)
      throws RestApiException, PermissionBackendException, IOException {
    IdentifiedUser user = parent.getUser();
    // This enforces visibility of the change.
    ChangeResource change = changes.parse(TopLevelResource.INSTANCE, id);
    Set<String> labels = starredChangesUtil.getLabels(user.getAccountId(), change.getId());
    return new Star(user, change, labels);
  }

  @Override
  public DynamicMap<RestView<Star>> views() {
    return views;
  }

  @Override
  public ListStarredChanges list() {
    return listStarredChanges;
  }

  @Singleton
  public static class ListStarredChanges implements RestReadView<AccountResource> {

    private final Provider<CurrentUser> self;
    private final ChangesCollection changes;

    @Inject
    ListStarredChanges(Provider<CurrentUser> self, ChangesCollection changes) {
      this.self = self;
      this.changes = changes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Response<List<ChangeInfo>> apply(AccountResource rsrc)
        throws RestApiException, PermissionBackendException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed to list stars of another account");
      }

      // The type of the value in the response that is returned by QueryChanges depends on the
      // number of queries that is provided as input. If a single query is provided as input the
      // value type is {@code List<ChangeInfo>}, if multiple queries are provided as input the value
      // type is {@code List<List<ChangeInfo>>) (one {@code List<ChangeInfo>} as result to each
      // query). Since in this case we provide exactly one query ("has:stars") as input we know that
      // the value always has the type {@code List<ChangeInfo>} and hence we can safely cast the
      // value to this type.
      QueryChanges query = changes.list();
      query.addQuery("has:stars");
      Response<?> response = query.apply(TopLevelResource.INSTANCE);
      List<ChangeInfo> value = (List<ChangeInfo>) response.value();
      return Response.ok(value);
    }
  }

  @Singleton
  public static class Get implements RestReadView<Star> {

    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;

    @Inject
    Get(Provider<CurrentUser> self, StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    @Override
    public Response<SortedSet<String>> apply(Star rsrc) throws AuthException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed to get stars of another account");
      }
      return Response.ok(
          starredChangesUtil.getLabels(self.get().getAccountId(), rsrc.getChange().getId()));
    }
  }

  @Singleton
  public static class Post implements RestModifyView<Star, StarsInput> {

    private final Provider<CurrentUser> self;
    private final StarredChangesUtil starredChangesUtil;

    @Inject
    Post(Provider<CurrentUser> self, StarredChangesUtil starredChangesUtil) {
      this.self = self;
      this.starredChangesUtil = starredChangesUtil;
    }

    @Override
    public Response<Collection<String>> apply(Star rsrc, StarsInput in)
        throws AuthException, BadRequestException {
      if (!self.get().hasSameAccountId(rsrc.getUser())) {
        throw new AuthException("not allowed to update stars of another account");
      }
      try {
        return Response.ok(
            starredChangesUtil.star(
                self.get().getAccountId(),
                rsrc.getChange().getProject(),
                rsrc.getChange().getId(),
                in.add,
                in.remove));
      } catch (IllegalLabelException e) {
        throw new BadRequestException(e.getMessage());
      }
    }
  }
}
