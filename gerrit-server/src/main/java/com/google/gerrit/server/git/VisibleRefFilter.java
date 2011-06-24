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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private final TagCache tagCache;
  private final Repository db;
  private final Project.NameKey projectName;
  private final ProjectControl projectCtl;
  private final ReviewDb reviewDb;
  private final boolean showChanges;

  private RevWalk rw;

  public VisibleRefFilter(final TagCache tagCache, final Repository db,
      final ProjectControl projectControl, final ReviewDb reviewDb,
      final boolean showChanges) {
    this.tagCache = tagCache;
    this.db = db;
    this.projectName = projectControl.getProject().getNameKey();
    this.projectCtl = projectControl;
    this.reviewDb = reviewDb;
    this.showChanges = showChanges;
  }

  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs) {
    try {
      return doFilter(refs);
    } finally {
      if (rw != null) {
        rw.release();
        rw = null;
      }
    }
  }

  private Map<String, Ref> doFilter(Map<String, Ref> refs) {
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
        if (ref.getObjectId() != null) {
          deferredTags.add(ref);
        }

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
      TagMatcher tags = tagCache.get(projectName).matcher(db, result.values());
      for (Ref tag : deferredTags) {
        if (tags.isReachable(tag)) {
          result.put(tag.getName(), tag);
        }
      }
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

  private static boolean isTag(Ref ref) {
    return ref.getLeaf().getName().startsWith(Constants.R_TAGS);
  }
}
