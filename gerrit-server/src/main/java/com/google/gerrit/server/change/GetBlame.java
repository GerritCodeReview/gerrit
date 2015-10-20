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

package com.google.gerrit.server.change;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetBlame implements RestReadView<FileResource> {
  private enum ConflictMarker {
    COMMON, LOCAL, REMOTE
  }

  private static Optional<ConflictMarker> getConflictMarker(String line) {
    if (line.startsWith("<<<<<<< HEAD")) {
      return Optional.of(ConflictMarker.LOCAL);
    } else if (line.startsWith("=======")) {
      return Optional.of(ConflictMarker.REMOTE);
    } else if (line.startsWith(">>>>>>> BRANCH")) {
      return Optional.of(ConflictMarker.COMMON);
    } else {
      return Optional.absent();
    }
  }

  private final GitRepositoryManager repoManager;
  private final ChangeQueryBuilder qb;
  private final QueryProcessor queryProcessor;

  private final GetDiff getDiff;

  private boolean base;
  @Option(name = "--base", aliases = {"-b"}, metaVar = "BASE",
    usage = "whether to load the blame of the base revision")
  public void setBase(boolean base) {
    this.base = base;
  }

  @Inject
  GetBlame(GitRepositoryManager repoManager,
    ChangeQueryBuilder qb,
    QueryProcessor queryProcessor,
    GetDiff getDiff) {
    this.repoManager = repoManager;
    this.qb = qb;
    this.queryProcessor = queryProcessor;
    this.getDiff = getDiff;
  }

  @Override
  public Response<BlameInfo> apply(FileResource resource)
    throws QueryParseException, OrmException, IOException,
    ResourceNotFoundException, AuthException, InvalidChangeOperationException,
    ResourceConflictException {

    Project.NameKey project = resource.getRevision().getChange().getProject();
    try (Repository repository = repoManager.openRepository(project)) {
      String refName = resource.getRevision().getEdit().isPresent()
        ? resource.getRevision().getEdit().get().getRefName()
        : resource.getRevision().getPatchSet().getRefName();

      Ref ref = repository.getRef(refName);
      if (ref == null) {
        throw new IllegalStateException("unknown ref " + refName);
      }
      ObjectId objectId = ref.getObjectId();
      RevWalk revWalk = new RevWalk(repository);
      RevCommit revCommit = revWalk.parseCommit(objectId);
      RevCommit[] parents = revCommit.getParents();

      String path = resource.getPatchKey().getFileName();
      String blameRef;
      if (base && parents.length == 1) {
        blameRef = parents[0].getName();
      } else {
        blameRef = revCommit.getName();
      }

      List<BlameInfo.BlameLine> commonBlames =
        blame(repository.resolve(blameRef), path, repository);
      final List<BlameInfo.BlameLine> blames;
      if (parents.length <= 1 || !base) {
        blames = commonBlames;
      } else if (parents.length == 2){
        List<BlameInfo.BlameLine> localBlames =
          blame(parents[0].getId(), path, repository);
        List<BlameInfo.BlameLine> remoteBlames =
          blame(parents[1].getId(), path, repository);

        Response<DiffInfo> diffResponse = getDiff.apply(resource);
        DiffInfo diff = diffResponse.value();

        blames = blameMergeBase(diff, commonBlames, localBlames, remoteBlames);
      } else {
        throw new IllegalStateException("Cannot generate blame for merge commit"
          + " with more than 2 parents");
      }

      BlameInfo result = new BlameInfo();
      result.blames = packBlames(blames);

      for (BlameInfo.Blame blame : result.blames) {
        QueryResult queryResult = queryProcessor.queryChanges(
          qb.commit(blame.meta.id));
        for (ChangeData changeData : queryResult.changes()) {
          PatchSet.Id patchSetId = changeData.change().currentPatchSetId();
          if (patchSetId != null) {
            blame.meta.changeId = changeData.change().getId().get();
            blame.meta.patchSetId = patchSetId.get();
          }
        }
      }

      Response<BlameInfo> r = Response.ok(result);
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    } catch (GitAPIException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }

  private static List<BlameInfo.BlameLine> blameMergeBase(DiffInfo diff,
    List<BlameInfo.BlameLine> commonBlames,
    List<BlameInfo.BlameLine> localBlames,
    List<BlameInfo.BlameLine> remoteBlames)
    throws OrmException, QueryParseException, GitAPIException, AuthException,
    ResourceNotFoundException, IOException, ResourceConflictException,
    InvalidChangeOperationException {

    List<BlameInfo.BlameLine> blames = new ArrayList<>();

    ConflictMarker conflictContext = ConflictMarker.COMMON;
    int lineNum = 0;
    int realLineNum = 0;
    int nonCommonIndex = 0;
    for (String line : diff.getDiffLines()) {
      Optional<ConflictMarker> conflictMarker = getConflictMarker(line);
      if (conflictMarker.isPresent()) {
        conflictContext = conflictMarker.get();
        nonCommonIndex = 0;
      } else {
        List<BlameInfo.BlameLine> blameLines =
          selectBlames(conflictContext, commonBlames, localBlames, remoteBlames);
        final BlameInfo.BlameLine blameLine;
        if (conflictContext == ConflictMarker.COMMON) {
          blameLine = blameLines.get(lineNum);
          lineNum = lineNum + 1;
        } else {
          blameLine = blameLines.get(lineNum + nonCommonIndex);
          nonCommonIndex = nonCommonIndex + 1;
        }
        blameLine.from = realLineNum + 1;
        blameLine.to = blameLine.from;
        blames.add(blameLine);
      }
      realLineNum = realLineNum + 1;
    }
    return blames;
  }

  private static List<BlameInfo.BlameLine> selectBlames(
    ConflictMarker conflictMarker,
    List<BlameInfo.BlameLine> commonBlames,
    List<BlameInfo.BlameLine> localBlames,
    List<BlameInfo.BlameLine> remoteBlames) {
    switch(conflictMarker) {
      case COMMON:
        return commonBlames;
      case LOCAL:
        return localBlames;
      case REMOTE:
        return remoteBlames;
      default:
        throw new IllegalStateException("unknown conflictMarker " + conflictMarker);
    }
  }

  private static List<BlameInfo.BlameLine> blame(AnyObjectId id, String path,
    Repository repository)
    throws GitAPIException, QueryParseException, OrmException {

    BlameCommand blameCommand = new BlameCommand(repository);
    blameCommand.setFilePath(path);
    blameCommand.setStartCommit(id);
    BlameResult blameResult = blameCommand.call();

    List<BlameInfo.BlameLine> result = new ArrayList<>();
    int numLines = blameResult.getResultContents().size();
    for (int i = 1; i <= numLines; ++i) {
      RevCommit commit = blameResult.getSourceCommit(i - 1);
      BlameInfo.BlameMeta meta = new BlameInfo.BlameMeta();
      meta.author = commit.getAuthorIdent().getName();
      meta.id = commit.getId().getName();
      meta.commitMsg = commit.getFullMessage();
      meta.time = commit.getCommitTime();
      BlameInfo.BlameLine line = new BlameInfo.BlameLine();
      line.meta = meta;
      line.from = i;
      line.to = i;
      result.add(line);
    }
    return result;
  }

  private static List<BlameInfo.Blame> packBlames(List<BlameInfo.BlameLine> blames) {
    ListMultimap<BlameInfo.BlameMeta, BlameInfo.Blame.FromTo> fromTos =
      ArrayListMultimap.create();
    int numLines = blames.size();
    for (int i = 1; i <= numLines; ++i) {
      BlameInfo.BlameLine current = blames.get(i - 1);
      int j = i;
      BlameInfo.BlameLine next = null;
      do {
        j = j + 1;
        if (j <= numLines) {
          next = blames.get(j - 1);
        }
      } while(j <= numLines && next != null
        && next.meta.id.equals(current.meta.id));
      BlameInfo.Blame.FromTo fromTo =
        new BlameInfo.Blame.FromTo(current.from, current.from + j - i - 1);
      fromTos.put(current.meta, fromTo);
      i = j - 1;
    }

    List<BlameInfo.Blame> result = new ArrayList<>();
    for (BlameInfo.BlameMeta key : fromTos.keySet()) {
      BlameInfo.Blame blame = new BlameInfo.Blame();
      blame.meta = key;
      blame.fromTo = fromTos.get(key);
      result.add(blame);
    }
    return result;
  }
}
