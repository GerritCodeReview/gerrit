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
import com.google.gerrit.extensions.common.FileInfo;
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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

@Singleton
public class Files implements ChildCollection<RevisionResource, FileResource> {
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
  public FileResource parse(RevisionResource rev, IdString id) {
    return new FileResource(rev, id.get());
  }

  public static final class ListFiles implements RestReadView<RevisionResource> {
    private static final Logger log = LoggerFactory.getLogger(ListFiles.class);

    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--reviewed")
    boolean reviewed;

    @Option(name = "-q")
    String query;

    private final Provider<ReviewDb> db;
    private final Provider<CurrentUser> self;
    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;
    private final GitRepositoryManager gitManager;
    private final PatchListCache patchListCache;

    @Inject
    ListFiles(Provider<ReviewDb> db,
        Provider<CurrentUser> self,
        FileInfoJson fileInfoJson,
        Revisions revisions,
        GitRepositoryManager gitManager,
        PatchListCache patchListCache) {
      this.db = db;
      this.self = self;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
      this.gitManager = gitManager;
      this.patchListCache = patchListCache;
    }

    public ListFiles setReviewed(boolean r) {
      this.reviewed = r;
      return this;
    }

    @Override
    public Response<?> apply(RevisionResource resource) throws AuthException,
        BadRequestException, ResourceNotFoundException, OrmException,
        RepositoryNotFoundException, IOException {
      checkOptions();
      if (reviewed) {
        return Response.ok(reviewed(resource));
      } else if (query != null) {
        return Response.ok(query(resource));
      }

      PatchSet basePatchSet = null;
      if (base != null) {
        RevisionResource baseResource = revisions.parse(
            resource.getChangeResource(), IdString.fromDecoded(base));
        basePatchSet = baseResource.getPatchSet();
      }
      try {
        Response<Map<String, FileInfo>> r = Response.ok(fileInfoJson.toFileInfoMap(
            resource.getChange(),
            resource.getPatchSet().getRevision(),
            basePatchSet));
        if (resource.isCacheable()) {
          r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
        }
        return r;
      } catch (PatchListNotAvailableException e) {
        throw new ResourceNotFoundException(e.getMessage());
      }
    }

    private void checkOptions() throws BadRequestException {
      int supplied = 0;
      if (base != null) {
        supplied++;
      }
      if (reviewed) {
        supplied++;
      }
      if (query != null) {
        supplied++;
      }
      if (supplied > 1) {
        throw new BadRequestException("cannot combine base, reviewed, query");
      }
    }

    private List<String> query(RevisionResource resource)
        throws RepositoryNotFoundException, IOException {
      Project.NameKey project = resource.getChange().getProject();
      try (Repository git = gitManager.openRepository(project);
          ObjectReader or = git.newObjectReader();
          RevWalk rw = new RevWalk(or);
          TreeWalk tw = new TreeWalk(or)) {
        RevCommit c = rw.parseCommit(
            ObjectId.fromString(resource.getPatchSet().getRevision().get()));

        tw.addTree(c.getTree());
        tw.setRecursive(true);
        List<String> paths = new ArrayList<>();
        while (tw.next() && paths.size() < 20) {
          String s = tw.getPathString();
          if (s.contains(query)) {
            paths.add(s);
          }
        }
        return paths;
      }
    }

    private List<String> reviewed(RevisionResource resource)
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
            } catch (IOException | PatchListNotAvailableException e) {
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
      Project.NameKey project = resource.getChange().getProject();
      try (Repository git = gitManager.openRepository(project);
          ObjectReader reader = git.newObjectReader();
          RevWalk rw = new RevWalk(reader);
          TreeWalk tw = new TreeWalk(reader)) {
        PatchList oldList = patchListCache.get(
            resource.getChange(),
            db.get().patchSets().get(old));

        PatchList curList = patchListCache.get(
            resource.getChange(),
            resource.getPatchSet());

        int sz = paths.size();
        List<AccountPatchReview> inserts = Lists.newArrayListWithCapacity(sz);
        List<String> pathList = Lists.newArrayListWithCapacity(sz);

        tw.setFilter(PathFilterGroup.createFromStrings(paths));
        tw.setRecursive(true);
        int o = tw.addTree(rw.parseCommit(oldList.getNewId()).getTree());
        int c = tw.addTree(rw.parseCommit(curList.getNewId()).getTree());

        int op = -1;
        if (oldList.getOldId() != null) {
          op = tw.addTree(rw.parseTree(oldList.getOldId()));
        }

        int cp = -1;
        if (curList.getOldId() != null) {
          cp = tw.addTree(rw.parseTree(curList.getOldId()));
        }

        while (tw.next()) {
          String path = tw.getPathString();
          if (tw.getRawMode(o) != 0 && tw.getRawMode(c) != 0
              && tw.idEqual(o, c)
              && paths.contains(path)) {
            // File exists in previously reviewed oldList and in curList.
            // File content is identical.
            inserts.add(new AccountPatchReview(
                new Patch.Key(
                    resource.getPatchSet().getId(),
                    path),
                  userId));
            pathList.add(path);
          } else if (op >= 0 && cp >= 0
              && tw.getRawMode(o) == 0 && tw.getRawMode(c) == 0
              && tw.getRawMode(op) != 0 && tw.getRawMode(cp) != 0
              && tw.idEqual(op, cp)
              && paths.contains(path)) {
            // File was deleted in previously reviewed oldList and curList.
            // File exists in ancestor of oldList and curList.
            // File content is identical in ancestors.
            inserts.add(new AccountPatchReview(
                new Patch.Key(
                    resource.getPatchSet().getId(),
                    path),
                  userId));
            pathList.add(path);
          }
        }
        db.get().accountPatchReviews().insert(inserts);
        return pathList;
      }
    }

    public ListFiles setBase(String base) {
      this.base = base;
      return this;
    }
  }
}
