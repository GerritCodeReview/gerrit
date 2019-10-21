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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.change.AccountPatchReviewStore.PatchSetWithReviewedFiles;
import com.google.gerrit.server.change.FileInfoJson;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.kohsuke.args4j.Option;

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

  public static final class ListFiles implements ETagView<RevisionResource> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    @Option(name = "--base", metaVar = "revision-id")
    String base;

    @Option(name = "--parent", metaVar = "parent-number")
    int parentNum;

    @Option(name = "--reviewed")
    boolean reviewed;

    @Option(name = "-q")
    String query;

    private final Provider<CurrentUser> self;
    private final FileInfoJson fileInfoJson;
    private final Revisions revisions;
    private final GitRepositoryManager gitManager;
    private final PatchListCache patchListCache;
    private final PatchSetUtil psUtil;
    private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;
    private final GerritApi gApi;

    @Inject
    ListFiles(
        Provider<CurrentUser> self,
        FileInfoJson fileInfoJson,
        Revisions revisions,
        GitRepositoryManager gitManager,
        PatchListCache patchListCache,
        PatchSetUtil psUtil,
        PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore,
        GerritApi gApi) {
      this.self = self;
      this.fileInfoJson = fileInfoJson;
      this.revisions = revisions;
      this.gitManager = gitManager;
      this.patchListCache = patchListCache;
      this.psUtil = psUtil;
      this.accountPatchReviewStore = accountPatchReviewStore;
      this.gApi = gApi;
    }

    public ListFiles setReviewed(boolean r) {
      this.reviewed = r;
      return this;
    }

    @Override
    public Response<?> apply(RevisionResource resource)
        throws RestApiException, RepositoryNotFoundException, IOException,
            PatchListNotAvailableException, PermissionBackendException {
      checkOptions();
      if (reviewed) {
        return Response.ok(reviewed(resource));
      } else if (query != null) {
        return Response.ok(query(resource));
      }

      Response<Map<String, FileInfo>> r;
      if (base != null) {
        RevisionResource baseResource =
            revisions.parse(resource.getChangeResource(), IdString.fromDecoded(base));
        r =
            Response.ok(
                fileInfoJson.toFileInfoMap(
                    resource.getChange(),
                    resource.getPatchSet().commitId(),
                    baseResource.getPatchSet()));
      } else if (parentNum != 0) {
        int parents =
            gApi.changes()
                .id(resource.getChange().getChangeId())
                .revision(resource.getPatchSet().id().get())
                .commit(false)
                .parents
                .size();
        if (parentNum < 0 || parentNum > parents) {
          throw new BadRequestException(String.format("invalid parent number: %d", parentNum));
        }
        r =
            Response.ok(
                fileInfoJson.toFileInfoMap(
                    resource.getChange(), resource.getPatchSet().commitId(), parentNum - 1));
      } else {
        r = Response.ok(fileInfoJson.toFileInfoMap(resource.getChange(), resource.getPatchSet()));
      }

      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }

    private void checkOptions() throws BadRequestException {
      int supplied = 0;
      if (base != null) {
        supplied++;
      }
      if (parentNum > 0) {
        supplied++;
      }
      if (reviewed) {
        supplied++;
      }
      if (query != null) {
        supplied++;
      }
      if (supplied > 1) {
        throw new BadRequestException("cannot combine base, parent, reviewed, query");
      }
    }

    private List<String> query(RevisionResource resource)
        throws RepositoryNotFoundException, IOException {
      Project.NameKey project = resource.getChange().getProject();
      try (Repository git = gitManager.openRepository(project);
          ObjectReader or = git.newObjectReader();
          RevWalk rw = new RevWalk(or);
          TreeWalk tw = new TreeWalk(or)) {
        RevCommit c = rw.parseCommit(resource.getPatchSet().commitId());

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

    private Collection<String> reviewed(RevisionResource resource) throws AuthException {
      CurrentUser user = self.get();
      if (!(user.isIdentifiedUser())) {
        throw new AuthException("Authentication required");
      }

      Account.Id userId = user.getAccountId();
      PatchSet patchSetId = resource.getPatchSet();
      Optional<PatchSetWithReviewedFiles> o;
      o = accountPatchReviewStore.call(s -> s.findReviewed(patchSetId.id(), userId));

      if (o.isPresent()) {
        PatchSetWithReviewedFiles res = o.get();
        if (res.patchSetId().equals(patchSetId.id())) {
          return res.files();
        }

        try {
          return copy(res.files(), res.patchSetId(), resource, userId);
        } catch (PatchListObjectTooLargeException e) {
          logger.atWarning().log("Cannot copy patch review flags: %s", e.getMessage());
        } catch (IOException | PatchListNotAvailableException e) {
          logger.atWarning().withCause(e).log("Cannot copy patch review flags");
        }
      }

      return Collections.emptyList();
    }

    private List<String> copy(
        Set<String> paths, PatchSet.Id old, RevisionResource resource, Account.Id userId)
        throws IOException, PatchListNotAvailableException {
      Project.NameKey project = resource.getChange().getProject();
      try (Repository git = gitManager.openRepository(project);
          ObjectReader reader = git.newObjectReader();
          RevWalk rw = new RevWalk(reader);
          TreeWalk tw = new TreeWalk(reader)) {
        Change change = resource.getChange();
        PatchSet patchSet = psUtil.get(resource.getNotes(), old);
        if (patchSet == null) {
          throw new PatchListNotAvailableException(
              String.format(
                  "patch set %s of change %s not found", old.get(), change.getId().get()));
        }

        PatchList oldList = patchListCache.get(change, patchSet);

        PatchList curList = patchListCache.get(change, resource.getPatchSet());

        int sz = paths.size();
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
          if (tw.getRawMode(o) != 0
              && tw.getRawMode(c) != 0
              && tw.idEqual(o, c)
              && paths.contains(path)) {
            // File exists in previously reviewed oldList and in curList.
            // File content is identical.
            pathList.add(path);
          } else if (op >= 0
              && cp >= 0
              && tw.getRawMode(o) == 0
              && tw.getRawMode(c) == 0
              && tw.getRawMode(op) != 0
              && tw.getRawMode(cp) != 0
              && tw.idEqual(op, cp)
              && paths.contains(path)) {
            // File was deleted in previously reviewed oldList and curList.
            // File exists in ancestor of oldList and curList.
            // File content is identical in ancestors.
            pathList.add(path);
          }
        }

        accountPatchReviewStore.run(
            s -> s.markReviewed(resource.getPatchSet().id(), userId, pathList));
        return pathList;
      }
    }

    public ListFiles setQuery(String query) {
      this.query = query;
      return this;
    }

    public ListFiles setBase(@Nullable String base) {
      this.base = base;
      return this;
    }

    public ListFiles setParent(int parentNum) {
      this.parentNum = parentNum;
      return this;
    }

    @Override
    public String getETag(RevisionResource resource) {
      Hasher h = Hashing.murmur3_128().newHasher();
      resource.prepareETag(h, resource.getUser());
      // File list comes from the PatchListCache, so any change to the key or value should
      // invalidate ETag.
      h.putLong(PatchListKey.serialVersionUID);
      return h.hash().toString();
    }
  }
}
