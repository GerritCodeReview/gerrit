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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exposes only the non refs/changes/ reference names. */
public class ReceiveCommitsAdvertiseRefsHook implements AdvertiseRefsHook {
  private static final Logger log = LoggerFactory.getLogger(ReceiveCommitsAdvertiseRefsHook.class);

  @VisibleForTesting
  @AutoValue
  public abstract static class Result {
    public abstract Map<String, Ref> allRefs();

    public abstract Set<ObjectId> additionalHaves();
  }

  private final Provider<InternalChangeQuery> queryProvider;
  private final Project.NameKey projectName;

  public ReceiveCommitsAdvertiseRefsHook(
      Provider<InternalChangeQuery> queryProvider, Project.NameKey projectName) {
    this.queryProvider = queryProvider;
    this.projectName = projectName;
  }

  @Override
  public void advertiseRefs(UploadPack us) {
    throw new UnsupportedOperationException(
        "ReceiveCommitsAdvertiseRefsHook cannot be used for UploadPack");
  }

  @Override
  public void advertiseRefs(BaseReceivePack rp) throws ServiceMayNotContinueException {
    Map<String, Ref> oldRefs = rp.getAdvertisedRefs();
    if (oldRefs == null) {
      try {
        oldRefs = rp.getRepository().getRefDatabase().getRefs(ALL);
      } catch (ServiceMayNotContinueException e) {
        throw e;
      } catch (IOException e) {
        ServiceMayNotContinueException ex = new ServiceMayNotContinueException();
        ex.initCause(e);
        throw ex;
      }
    }
    Result r = advertiseRefs(oldRefs);
    rp.setAdvertisedRefs(r.allRefs(), r.additionalHaves());
  }

  @VisibleForTesting
  public Result advertiseRefs(Map<String, Ref> oldRefs) {
    Map<String, Ref> r = Maps.newHashMapWithExpectedSize(oldRefs.size());
    Set<ObjectId> allPatchSets = Sets.newHashSetWithExpectedSize(oldRefs.size());
    for (Map.Entry<String, Ref> e : oldRefs.entrySet()) {
      String name = e.getKey();
      if (!skip(name)) {
        r.put(name, e.getValue());
      }
      if (name.startsWith(RefNames.REFS_CHANGES)) {
        allPatchSets.add(e.getValue().getObjectId());
      }
    }
    return new AutoValue_ReceiveCommitsAdvertiseRefsHook_Result(
        r, advertiseOpenChanges(allPatchSets));
  }

  private static final ImmutableSet<String> OPEN_CHANGES_FIELDS =
      ImmutableSet.of(
          // Required for ChangeIsVisibleToPrdicate.
          ChangeField.CHANGE.getName(),
          // Required during advertiseOpenChanges.
          ChangeField.PATCH_SET.getName());

  private Set<ObjectId> advertiseOpenChanges(Set<ObjectId> allPatchSets) {
    // Advertise some recent open changes, in case a commit is based on one.
    int limit = 32;
    try {
      Set<ObjectId> r = Sets.newHashSetWithExpectedSize(limit);
      for (ChangeData cd :
          queryProvider
              .get()
              .setRequestedFields(OPEN_CHANGES_FIELDS)
              .enforceVisibility(true)
              .setLimit(limit)
              .byProjectOpen(projectName)) {
        PatchSet ps = cd.currentPatchSet();
        if (ps != null) {
          ObjectId id = ObjectId.fromString(ps.getRevision().get());
          // Ensure we actually observed a patch set ref pointing to this
          // object, in case the database is out of sync with the repo and the
          // object doesn't actually exist.
          if (allPatchSets.contains(id)) {
            r.add(id);
          }
        }
      }
      return r;
    } catch (OrmException err) {
      log.error("Cannot list open changes of " + projectName, err);
      return Collections.emptySet();
    }
  }

  private static boolean skip(String name) {
    return name.startsWith(RefNames.REFS_CHANGES)
        || name.startsWith(RefNames.REFS_CACHE_AUTOMERGE)
        || MagicBranch.isMagicBranch(name);
  }
}
