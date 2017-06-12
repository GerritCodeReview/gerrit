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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.config.ExternalIncludedIn;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
class IncludedIn implements RestReadView<ChangeResource> {

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final DynamicSet<ExternalIncludedIn> includedIn;

  @Inject
  IncludedIn(
      Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      PatchSetUtil psUtil,
      DynamicSet<ExternalIncludedIn> includedIn) {
    this.db = db;
    this.repoManager = repoManager;
    this.psUtil = psUtil;
    this.includedIn = includedIn;
  }

  @Override
  public IncludedInInfo apply(ChangeResource rsrc)
      throws BadRequestException, ResourceConflictException, OrmException, IOException {
    ChangeControl ctl = rsrc.getControl();
    PatchSet ps = psUtil.current(db.get(), rsrc.getNotes());
    Project.NameKey project = ctl.getProject().getNameKey();
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      rw.setRetainBody(false);
      RevCommit rev;
      try {
        rev = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
      } catch (IncorrectObjectTypeException err) {
        throw new BadRequestException(err.getMessage());
      } catch (MissingObjectException err) {
        throw new ResourceConflictException(err.getMessage());
      }

      IncludedInResolver.Result d = IncludedInResolver.resolve(r, rw, rev);
      Multimap<String, String> external = ArrayListMultimap.create();
      for (ExternalIncludedIn ext : includedIn) {
        Multimap<String, String> extIncludedIns =
            ext.getIncludedIn(project.get(), rev.name(), d.getTags(), d.getBranches());
        if (extIncludedIns != null) {
          external.putAll(extIncludedIns);
        }
      }
      return new IncludedInInfo(d, (!external.isEmpty() ? external.asMap() : null));
    }
  }

  static class IncludedInInfo {
    Collection<String> branches;
    Collection<String> tags;
    Map<String, Collection<String>> external;

    IncludedInInfo(IncludedInResolver.Result in, Map<String, Collection<String>> e) {
      branches = in.getBranches();
      tags = in.getTags();
      external = e;
    }
  }
}
