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

import com.google.common.base.Charsets;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutContent.Input;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;

public class PutContent implements RestModifyView<FileResource, Input> {
  static class Input {
    @DefaultInput
    String content;
  }

  private final PersonIdent serverIdent;
  private final Provider<CurrentUser> user;
  private final GitRepositoryManager repoManager;

  @Inject
  PutContent(@GerritPersonIdent PersonIdent serverIdent,
      Provider<CurrentUser> user,
      GitRepositoryManager repoManager) {
    this.serverIdent = serverIdent;
    this.user = user;
    this.repoManager = repoManager;
  }

  @Override
  public Object apply(FileResource rsrc, Input input) throws AuthException,
      ResourceNotFoundException, BadRequestException, ResourceConflictException,
      IOException {
    RevisionResource rev = rsrc.getRevision();
    rev.checkEdit();
    RevisionEdit edit = new RevisionEdit(checkIdentifiedUser(), rev.getId());
    Repository repo = repoManager.openRepository(
        rsrc.getRevision().getChange().getProject());
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter ins = repo.newObjectInserter();
      try {
        RevCommit prevEdit = edit.get(repo, rw);
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            rev.getPatchSet().getRevision().get()));
        if (prevEdit == null) {
          prevEdit = base;
        }

        ObjectId tree = editTree(rsrc, repo, ins, input, base);
        if (ObjectId.equals(tree, prevEdit.getTree())) {
          // TODO(dborowitz): Allow empty edits until explicitly deleted?
          return Response.none();
        }

        CommitBuilder builder = new CommitBuilder();
        builder.setTreeId(tree);
        builder.setParentIds(base.getParents());
        builder.setAuthor(prevEdit.getAuthorIdent());
        builder.setCommitter(getCommitterIdent());
        builder.setMessage(prevEdit.getFullMessage());
        ObjectId newEdit = ins.insert(builder);

        RefUpdate ru = repo.updateRef(edit.toRefName());
        ru.setExpectedOldObjectId(
            prevEdit == base ? ObjectId.zeroId() : prevEdit);
        ru.setNewObjectId(newEdit);
        ru.setRefLogIdent(getRefLogIdent());
        ru.setForceUpdate(true);
        ru.update(rw);
        return result(ru.getResult());
      } finally {
        rw.release();
        ins.release();
      }
    } finally {
      repo.close();
    }
  }

  private IdentifiedUser checkIdentifiedUser() throws AuthException {
    CurrentUser u = user.get();
    if (!(u instanceof IdentifiedUser)) {
      throw new AuthException("edits only available to authenticated users");
    }
    return (IdentifiedUser) u;
  }

  private ObjectId insert(ObjectInserter ins, Input input) throws IOException {
    // TODO(dborowitz): Support non-UTF-8 (first need to pass the input as a
    // byte array somehow).
    return ins.insert(Constants.OBJ_BLOB, Constants.encode(input.content));
  }

  private ObjectId editTree(FileResource rsrc, Repository repo,
      ObjectInserter ins, Input input, RevCommit prevEdit)
      throws BadRequestException, IOException {
    // TODO(dborowitz): Handle non-UTF-8 paths.
    byte[] path = rsrc.getPatchKey().getFileName().getBytes(Charsets.UTF_8);
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder dcb = dc.builder();
    TreeWalk tw = new TreeWalk(repo);
    tw.reset();
    tw.setRecursive(true);
    tw.addTree(prevEdit.getTree());
    // TODO(dborowitz): This is awful. Surely we can do this without
    // iterating the entire tree. Take a closer look at ResolveMerger to
    // figure this out.
    boolean found = false;
    while (tw.next()) {
      DirCacheEntry dce = new DirCacheEntry(tw.getRawPath());
      dce.setFileMode(tw.getFileMode(0));
      if (tw.isPathPrefix(path, path.length) == 0
          && tw.getPathLength() == path.length) {
        if (tw.isSubtree()) {
          throw new BadRequestException("invalid PUT on directory");
        }
        dce.setObjectId(insert(ins, input));
        found = true;
      } else {
        dce.setObjectId(tw.getObjectId(0));
      }
      dcb.add(dce);
    }
    if (!found) {
      // TODO(dborowitz): Use path compare above to insert in order.
      DirCacheEntry dce = new DirCacheEntry(path);
      dce.setFileMode(FileMode.REGULAR_FILE);
      dce.setObjectId(insert(ins, input));
      dcb.add(dce);
    }
    dcb.finish();
    return dc.writeTree(ins);
  }

  private PersonIdent getCommitterIdent() {
    CurrentUser cu = user.get();
    if (cu instanceof IdentifiedUser) {
      return ((IdentifiedUser) cu).newCommitterIdent(
          serverIdent.getWhen(), serverIdent.getTimeZone());
    } else {
      return serverIdent;
    }
  }

  private PersonIdent getRefLogIdent() {
    CurrentUser cu = user.get();
    if (cu instanceof IdentifiedUser) {
      return ((IdentifiedUser) cu).newRefLogIdent(
          serverIdent.getWhen(), serverIdent.getTimeZone());
    } else {
      return serverIdent;
    }
  }

  private Response<?> result(RefUpdate.Result result)
      throws ResourceConflictException, IOException {
    switch (result) {
      case LOCK_FAILURE:
        throw new ResourceConflictException("write conflict on edit");
      case NEW:
      case FORCED:
        return Response.none();
      default:
        throw new IOException(
            "unexpected result from ref update on edit: " + result);
    }
  }
}
