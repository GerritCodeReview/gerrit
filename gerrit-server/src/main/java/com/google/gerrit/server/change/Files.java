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
import com.google.common.collect.Sets;
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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.FileInfoJson.FileInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

class Files implements ChildCollection<RevisionResource, FileResource> {
  private final DynamicMap<RestView<FileResource>> views;
  private final Provider<ListFiles> list;

  @Inject
  Files(DynamicMap<RestView<FileResource>> views, Provider<ListFiles> list) {
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

  private static final class ListFiles implements RestReadView<RevisionResource> {
    private static final Logger log = LoggerFactory.getLogger(ListFiles.class);

    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--reviewed")
    boolean reviewed;

    private final Provider<ReviewDb> db;
    private final Provider<CurrentUser> self;
    private final FileInfoJson fileInfoJson;
    private final Provider<Revisions> revisions;
    private final GitRepositoryManager gitManager;
    private final PatchListCache patchListCache;

    @Inject
    ListFiles(Provider<ReviewDb> db,
        Provider<CurrentUser> self,
        FileInfoJson fileInfoJson,
        Provider<Revisions> revisions,
        GitRepositoryManager gitManager,
        PatchListCache patchListCache) {
      this.db = db;
      this.self = self;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
      this.gitManager = gitManager;
      this.patchListCache = patchListCache;
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
      if (!(user.isIdentifiedUser())) {
        throw new AuthException("Authentication required");
      }

      Account.Id userId = ((IdentifiedUser) user).getAccountId();
      List<String> r = scan(userId, resource.getPatchSet().getId());

      if (r.isEmpty() && 1 < resource.getPatchSet().getPatchSetId()) {
        for (Integer id : reverseSortPatchSets(resource)) {
          PatchSet.Id old = new PatchSet.Id(resource.getChange().getId(), id);
          List<String> o = scan(userId, old);
          if (!o.isEmpty()) {
            try {
              r = copy(Sets.newHashSet(o), old, resource, userId);
            } catch (IOException e) {
              log.warn("Cannot copy patch review flags", e);
            } catch (PatchListNotAvailableException e) {
              log.warn("Cannot copy patch review flags", e);
            }
            break;
          }
        }
      }

      return r;
    }

    private List<String> scan(Account.Id userId, PatchSet.Id psId)
        throws OrmException {
      List<String> r = Lists.newArrayList();
      for (AccountPatchReview w : db.get().accountPatchReviews()
          .byReviewer(userId, psId)) {
        r.add(w.getKey().getPatchKey().getFileName());
      }
      return r;
    }

    private List<Integer> reverseSortPatchSets(
        RevisionResource resource) throws OrmException {
      SortedSet<Integer> ids = Sets.newTreeSet();
      for (PatchSet p : db.get().patchSets()
          .byChange(resource.getChange().getId())) {
        if (p.getPatchSetId() < resource.getPatchSet().getPatchSetId()) {
          ids.add(p.getPatchSetId());
        }
      }

      List<Integer> r = Lists.newArrayList(ids);
      Collections.reverse(r);
      return r;
    }

    private List<String> copy(Set<String> paths, PatchSet.Id old,
        RevisionResource resource, Account.Id userId) throws IOException,
        PatchListNotAvailableException, OrmException {
      Repository git =
          gitManager.openRepository(resource.getChange().getProject());
      try {
        ObjectReader reader = git.newObjectReader();
        try {
          PatchList oldList = patchListCache.get(
              resource.getChange(),
              db.get().patchSets().get(old));

          PatchList curList = patchListCache.get(
              resource.getChange(),
              resource.getPatchSet());

          int sz = paths.size();
          List<AccountPatchReview> inserts = Lists.newArrayListWithCapacity(sz);
          List<String> pathList = Lists.newArrayListWithCapacity(sz);

          RevWalk rw = new RevWalk(reader);
          RevTree o = rw.parseCommit(oldList.getNewId()).getTree();
          RevTree c = rw.parseCommit(curList.getNewId()).getTree();
          for (PatchListEntry p : curList.getPatches()) {
            String path = p.getNewName();
            if (!Patch.COMMIT_MSG.equals(path) && paths.contains(path)) {
              TreeWalk tw = TreeWalk.forPath(reader, path, o, c);
              if (tw != null
                  && tw.getRawMode(0) != 0
                  && tw.getRawMode(1) != 0
                  && tw.idEqual(0, 1)) {
                inserts.add(new AccountPatchReview(
                    new Patch.Key(
                        resource.getPatchSet().getId(),
                        path),
                      userId));
                pathList.add(path);
              }
            }
          }
          db.get().accountPatchReviews().insert(inserts);
          return pathList;
        } finally {
          reader.release();
        }
      } finally {
        git.close();
      }
    }
  }
}
