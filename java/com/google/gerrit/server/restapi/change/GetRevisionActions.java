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

package com.google.gerrit.server.restapi.change;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ActionJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

@Singleton
public class GetRevisionActions implements ETagView<RevisionResource> {
  private final ActionJson delegate;
  private final Config config;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final ChangeResource.Factory changeResourceFactory;

  @Inject
  GetRevisionActions(
      ActionJson delegate,
      Provider<ReviewDb> dbProvider,
      Provider<MergeSuperSet> mergeSuperSet,
      ChangeResource.Factory changeResourceFactory,
      @GerritServerConfig Config config) {
    this.delegate = delegate;
    this.dbProvider = dbProvider;
    this.mergeSuperSet = mergeSuperSet;
    this.changeResourceFactory = changeResourceFactory;
    this.config = config;
  }

  @Override
  public Response<Map<String, ActionInfo>> apply(RevisionResource rsrc) throws OrmException {
    return Response.withMustRevalidate(delegate.format(rsrc));
  }

  @Override
  public String getETag(RevisionResource rsrc) {
    Hasher h = Hashing.murmur3_128().newHasher();
    CurrentUser user = rsrc.getUser();
    try {
      rsrc.getChangeResource().prepareETag(h, user);
      h.putBoolean(MergeSuperSet.wholeTopicEnabled(config));
      ReviewDb db = dbProvider.get();
      ChangeSet cs = mergeSuperSet.get().completeChangeSet(db, rsrc.getChange(), user);
      for (ChangeData cd : cs.changes()) {
        changeResourceFactory.create(cd.notes(), user).prepareETag(h, user);
      }
      h.putBoolean(cs.furtherHiddenChanges());
    } catch (IOException | OrmException | PermissionBackendException e) {
      throw new OrmRuntimeException(e);
    }
    return h.hash().toString();
  }
}
