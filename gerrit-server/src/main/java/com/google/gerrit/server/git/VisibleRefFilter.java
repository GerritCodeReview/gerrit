// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VisibleRefFilter implements RefFilter {
  private static final Logger log =
      LoggerFactory.getLogger(VisibleRefFilter.class);

  private final Repository db;
  private final ProjectControl projectCtl;
  private final ReviewDb reviewDb;
  private final boolean showChanges;

  public VisibleRefFilter(final Repository db,
      final ProjectControl projectControl, final ReviewDb reviewDb,
      final boolean showChanges) {
    this.db = db;
    this.projectCtl = projectControl;
    this.reviewDb = reviewDb;
    this.showChanges = showChanges;
  }

  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs) {
    final Set<Change.Id> visibleChanges = visibleChanges();
    final Map<String, Ref> result = new HashMap<String, Ref>();
    final List<Ref> deferredTags = new ArrayList<Ref>();

    for (Ref ref : refs.values()) {
      if (PatchSet.isRef(ref.getName())) {
        // Reference to a patch set is visible if the change is visible.
        //
        if (visibleChanges.contains(Change.Id.fromRef(ref.getName()))) {
          result.put(ref.getName(), ref);
        }

      } else if (isTag(ref)) {
        // If its a tag, consider it later.
        //
        deferredTags.add(ref);

      } else if (projectCtl.controlForRef(ref.getLeaf().getName()).isVisible()) {
        // Use the leaf to lookup the control data. If the reference is
        // symbolic we want the control around the final target. If its
        // not symbolic then getLeaf() is a no-op returning ref itself.
        //
        result.put(ref.getName(), ref);
      }
    }

    // If we have tags that were deferred, we need to do a revision walk
    // to identify what tags we can actually reach, and what we cannot.
    //
    if (!deferredTags.isEmpty() && !result.isEmpty()) {
      addVisibleTags(result, deferredTags);
    }

    return result;
  }

  private Set<Change.Id> visibleChanges() {
    if (!showChanges) {
      return Collections.emptySet();
    }

    final Project project = projectCtl.getProject();
    try {
      final Set<Change.Id> visibleChanges = new HashSet<Change.Id>();
      for (Change change : reviewDb.changes().byProject(project.getNameKey())) {
        if (projectCtl.controlFor(change).isVisible()) {
          visibleChanges.add(change.getId());
        }
      }
      return visibleChanges;
    } catch (OrmException e) {
      log.error("Cannot load changes for project " + project.getName()
          + ", assuming no changes are visible", e);
      return Collections.emptySet();
    }
  }

  private void addVisibleTags(final Map<String, Ref> result,
      final List<Ref> tags) {
    final RevWalk rw = new RevWalk(db);
    try {
      final RevFlag VISIBLE = rw.newFlag("VISIBLE");
      final List<RevCommit> starts;

      rw.carry(VISIBLE);
      starts = lookupVisibleCommits(result, rw, VISIBLE);

      for (Ref tag : tags) {
        if (isTagVisible(rw, VISIBLE, starts, tag)) {
          result.put(tag.getName(), tag);
        }
      }
    } finally {
      rw.release();
    }
  }

  private List<RevCommit> lookupVisibleCommits(final Map<String, Ref> result,
      final RevWalk rw, final RevFlag VISIBLE) {
    // Lookup and cache the roots of the graph that we know we can see.
    //
    final List<RevCommit> roots = new ArrayList<RevCommit>(result.size());
    for (Ref ref : result.values()) {
      try {
        RevObject c = rw.parseAny(ref.getObjectId());
        c.add(VISIBLE);
        if (c instanceof RevCommit) {
          roots.add((RevCommit) c);
        } else if (c instanceof RevTag) {
          roots.add(rw.parseCommit(c));
        }
      } catch (IOException e) {
      }
    }
    return roots;
  }

  private boolean isTagVisible(final RevWalk rw, final RevFlag VISIBLE,
      final List<RevCommit> starts, Ref tag) {
    try {
      final RevObject obj = peelTag(rw, tag);
      if (obj.has(VISIBLE)) {
        // If the target is immediately visible, continue on. This case
        // is quite common as tags are often sorted alphabetically by the
        // version number, so earlier tags usually compute the data needed
        // to answer later tags with no additional effort.
        //
        return true;
      }

      if (obj instanceof RevCommit) {
        // Cast to a commit and traverse the history to determine if
        // the commit is reachable through one or more references.
        //
        final RevCommit c = (RevCommit) obj;
        walk(rw, VISIBLE, c, starts);
        return c.has(VISIBLE);
      }

      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private RevObject peelTag(final RevWalk rw, final Ref tag)
      throws MissingObjectException, IOException {
    // Try to use the peeled object identity, because it may be
    // able to save us from parsing the tag object itself.
    //
    ObjectId target = tag.getPeeledObjectId();
    if (target == null) {
      target = tag.getObjectId();
    }
    RevObject o = rw.parseAny(target);
    while (o instanceof RevTag) {
      o = ((RevTag) o).getObject();
      rw.parseHeaders(o);
    }
    return o;
  }

  private void walk(final RevWalk rw, final RevFlag VISIBLE,
      final RevCommit tagged, final List<RevCommit> starts)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    // Reset the traversal, but keep VISIBLE flags live as they aren't
    // invalidated by the change in starting points.
    //
    rw.resetRetain(VISIBLE);
    for (RevCommit o : starts) {
      try {
        rw.markStart(o);
      } catch (IOException e) {
      }
    }

    // Traverse the history until the tag is found.
    //
    rw.markUninteresting(tagged);
    while (rw.next() != null) {
    }
  }

  private static boolean isTag(Ref ref) {
    return ref.getLeaf().getName().startsWith(Constants.R_TAGS);
  }
}
