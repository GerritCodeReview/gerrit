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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates a {@link IncludedInDetail} of a {@link Change}. */
class IncludedInDetailFactory extends Handler<IncludedInDetail> {
  private static final Logger log = LoggerFactory
      .getLogger(IncludedInDetailFactory.class);

  interface Factory {
    IncludedInDetailFactory create(Change.Id id);
  }

  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final GitRepositoryManager repoManager;
  private final Change.Id changeId;

  private IncludedInDetail detail;
  private ChangeControl control;

  @Inject
  IncludedInDetailFactory(final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final GitRepositoryManager repoManager, @Assisted final Change.Id changeId) {
    this.changeControlFactory = changeControlFactory;
    this.repoManager = repoManager;
    this.changeId = changeId;
    this.db = db;
  }

  @Override
  public IncludedInDetail call() throws OrmException, NoSuchChangeException,
      IOException, InvalidRevisionException {
    control = changeControlFactory.validateFor(changeId);

    final PatchSet patch =
        db.patchSets().get(control.getChange().currentPatchSetId());
    final Repository repo =
        repoManager.openRepository(control.getProject().getNameKey());
    try {
      final RevWalk rw = new RevWalk(repo);
      try {
        rw.setRetainBody(false);

        final RevCommit rev;
        try {
          rev = rw.parseCommit(ObjectId.fromString(patch.getRevision().get()));
        } catch (IncorrectObjectTypeException err) {
          throw new InvalidRevisionException();
        } catch (MissingObjectException err) {
          throw new InvalidRevisionException();
        }

        List<Ref> tags =
            new ArrayList<Ref>(repo.getRefDatabase().getRefs(Constants.R_TAGS)
                .values());
        List<Ref> branches =
            new ArrayList<Ref>(repo.getRefDatabase().getRefs(Constants.R_HEADS)
                .values());
        Set<Ref> allRefs = new HashSet<Ref>();
        allRefs.addAll(tags);
        allRefs.addAll(branches);
        List<Ref> allMatchingRefs = includedIn(repo, rw, rev, allRefs);


        detail = new IncludedInDetail();
        detail.setBranches(getMatchingRefNames(allMatchingRefs, branches));
        detail.setTags(getMatchingRefNames(allMatchingRefs, tags));

        return detail;
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  /**
   * Resolves which tip ref's include the target commit.
   */
  private List<Ref> includedIn(final Repository repo, final RevWalk rw,
      final RevCommit target, final Set<Ref> tipRefs) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {

    List<Ref> result = new ArrayList<Ref>();

    Map<RevCommit, Set<Ref>> tipsAndCommits = parseCommits(repo, rw, tipRefs);

    List<RevCommit> tips = new ArrayList<RevCommit>(tipsAndCommits.keySet());
    Collections.sort(tips, new Comparator<RevCommit>() {
      @Override
      public int compare(RevCommit c1, RevCommit c2) {
        Integer date1 = c1.getCommitTime();
        Integer date2 = c2.getCommitTime();
        return date1.compareTo(date2);
      }
    });

    Set<RevCommit> hits = new HashSet<RevCommit>();
    hits.add(target);

    for (RevCommit tip : tips) {
      boolean commitFound = false;
      rw.resetRetain(RevFlag.UNINTERESTING);
      rw.markStart(tip);
      for (RevCommit commit : rw) {
        if (hits.contains(commit)) {
          commitFound = true;
          hits.add(commit);
          result.addAll(tipsAndCommits.get(tip));
          break;
        }
      }
      if (!commitFound) {
        rw.markUninteresting(tip);
      }
    }

    return result;
  }

  /**
   * Returns the short names of refs which are as well in the matchingRefs list
   * as well as in the allRef list.
   */
  private List<String> getMatchingRefNames(List<Ref> matchingRefs,
      List<Ref> allRefs) {
    List<String> refNames = new ArrayList<String>();
    for (Ref matchingRef : matchingRefs) {
      if (allRefs.contains(matchingRef)) {
        refNames.add(Repository.shortenRefName(matchingRef.getName()));
      }
    }
    return refNames;
  }

  /**
   * Parse commit of ref and store the relation between ref and commit.
   */
  private Map<RevCommit, Set<Ref>> parseCommits(final Repository repo,
      final RevWalk rw, final Set<Ref> refs) throws IOException {
    Map<RevCommit, Set<Ref>> result = new HashMap<RevCommit, Set<Ref>>();
    for (Ref ref : refs) {
      final RevCommit commit;
      try {
        commit = rw.parseCommit(ref.getObjectId());
      } catch (IncorrectObjectTypeException notCommit) {
        // Its OK for a tag reference to point to a blob or a tree, this
        // is common in the Linux kernel or git.git repository.
        //
        continue;
      } catch (MissingObjectException notHere) {
        // Log the problem with this branch, but keep processing.
        //
        log.warn("Reference " + ref.getName() + " in " + repo.getDirectory()
            + " points to dangling object " + ref.getObjectId());
        continue;
      }
      Set<Ref> relatedTags = result.get(commit);
      if (relatedTags == null) {
        relatedTags = new HashSet<Ref>();
        result.put(commit, relatedTags);
      }
      relatedTags.add(ref);
    }
    return result;
  }

}
