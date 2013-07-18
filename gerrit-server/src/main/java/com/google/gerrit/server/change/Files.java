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

package com.google.gerrit.server.change;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.FileInfoJson.FileInfo;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.Map;
import java.util.concurrent.TimeUnit;

class Files implements ChildCollection<RevisionResource, FileResource> {
  private final DynamicMap<RestView<FileResource>> views;
  private final Provider<List> list;

  @Inject
  Files(DynamicMap<RestView<FileResource>> views, Provider<List> list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<FileResource>> views() {
    return views;
  }

  @Override
  public RestView<RevisionResource> list() throws AuthException {
    return list.get();
  }

  @Override
  public FileResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException, OrmException, AuthException {
    return new FileResource(rev, id.get());
  }

  private static final class List implements RestReadView<RevisionResource> {
    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--reviewed")
    boolean reviewed;

    private final Provider<ReviewDb> db;
    private final Provider<CurrentUser> self;
    private final FileInfoJson fileInfoJson;
    private final Provider<Revisions> revisions;

    @Inject
    List(Provider<ReviewDb> db,
        Provider<CurrentUser> self,
        FileInfoJson fileInfoJson,
        Provider<Revisions> revisions) {
      this.db = db;
      this.self = self;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
    }

    @Override
    public Object apply(RevisionResource resource)
        throws ResourceNotFoundException, OrmException,
        PatchListNotAvailableException, BadRequestException, AuthException {
      if (base != null && reviewed) {
        throw new BadRequestException("cannot combine base and reviewed");
      } else if (reviewed) {
        return reviewed(resource);
      }

      PatchSet basePatchSet = null;
      if (base != null) {
        RevisionResource baseResource = revisions.get().parse(
            resource.getChangeResource(), IdString.fromDecoded(base));
        basePatchSet = baseResource.getPatchSet();
      }
      Response<Map<String, FileInfo>> r = Response.ok(fileInfoJson.toFileInfoMap(
          resource.getChange(),
          resource.getPatchSet(),
          basePatchSet));
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }

    private Object reviewed(RevisionResource resource)
        throws AuthException, OrmException {
      CurrentUser user = self.get();
      if (!(user instanceof IdentifiedUser)) {
        throw new AuthException("Authentication required");
      }

      java.util.List<String> r = Lists.newArrayList();
      for (AccountPatchReview w : db.get().accountPatchReviews()
          .byReviewer(
              ((IdentifiedUser) user).getAccountId(),
              resource.getPatchSet().getId())) {
        r.add(w.getKey().getPatchKey().getFileName());
      }
      return r;
    }
  }
}
