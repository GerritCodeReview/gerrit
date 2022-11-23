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

package com.google.gerrit.server.git.receive;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.git.HookUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Exposes only the non refs/changes/ reference names and provide additional haves.
 *
 * <p>Negotiation on Git push is suboptimal in that it tends to send more objects to the server than
 * it should. This results in increased latencies for {@code git push}.
 *
 * <p>Ref advertisement for Git pushes still works in a "the server speaks first fashion" as Git
 * Protocol V2 only addressed fetches Therefore the server needs to send all available references.
 * For large repositories, this can be in the tens of megabytes to send to the client. We therefore
 * remove all refs in refs/changes/* to scale down that footprint. Trivially, this would increase
 * the unnecessary objects that the client has to send to the server because the common ancestor it
 * finds in negotiation might be further back in history.
 *
 * <p>To work around this, we advertise the last 32 changes in that repository as additional {@code
 * .haves}. This is a heuristical approach that aims at scaling down the number of unnecessary
 * objects that client sends to the server. Unnecessary here refers to objects that the server
 * already has.
 *
 * <p>TODO(hiesel): Instrument this heuristic and proof its value.
 */
public class ReceiveCommitsAdvertiseRefsHook implements AdvertiseRefsHook {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
  private final Project.NameKey projectName;
  private final Account.Id user;

  public ReceiveCommitsAdvertiseRefsHook(
      Provider<InternalChangeQuery> queryProvider, Project.NameKey projectName, Account.Id user) {
    this.queryProvider = queryProvider;
    this.projectName = projectName;
    this.user = user;
  }

  @Override
  public void advertiseRefs(UploadPack us) {
    throw new UnsupportedOperationException(
        "ReceiveCommitsAdvertiseRefsHook cannot be used for UploadPack");
  }

  @Override
  public void advertiseRefs(ReceivePack rp) throws ServiceMayNotContinueException {
    Map<String, Ref> advertisedRefs = HookUtil.ensureAllRefsAdvertised(rp);
    advertisedRefs.keySet().stream()
        .filter(ReceiveCommitsAdvertiseRefsHook::skip)
        .collect(toImmutableList())
        .forEach(r -> advertisedRefs.remove(r));
    try {
      rp.setAdvertisedRefs(advertisedRefs, advertiseOpenChanges(rp.getRepository()));
    } catch (IOException e) {
      throw new ServiceMayNotContinueException(e);
    }
  }

  private Set<ObjectId> advertiseOpenChanges(Repository repo)
      throws ServiceMayNotContinueException {
    // Advertise the user's most recent open changes. It's likely that the user has one of these in
    // their local repo and they can serve as starting points to figure out the common ancestor of
    // what the client and server have in common.
    int limit = 32;
    try {
      Set<ObjectId> r = Sets.newHashSetWithExpectedSize(limit);
      for (ChangeData cd :
          queryProvider
              .get()
              .setRequestedFields(
                  // Required for ChangeIsVisibleToPrdicate.
                  ChangeField.CHANGE,
                  ChangeField.REVIEWER_SPEC,
                  // Required during advertiseOpenChanges.
                  ChangeField.PATCH_SET_SPEC)
              .enforceVisibility(true)
              .setLimit(limit)
              .query(
                  Predicate.and(
                      ChangePredicates.project(projectName),
                      ChangeStatusPredicate.open(),
                      ChangePredicates.owner(user)))) {
        PatchSet ps = cd.currentPatchSet();
        if (ps != null) {
          // Ensure we actually observed a patch set ref pointing to this
          // object, in case the database is out of sync with the repo and the
          // object doesn't actually exist.
          try {
            Ref psRef = repo.getRefDatabase().exactRef(RefNames.patchSetRef(ps.id()));
            if (psRef != null) {
              r.add(ps.commitId());
            }
          } catch (IOException e) {
            throw new ServiceMayNotContinueException(e);
          }
        }
      }

      return r;
    } catch (StorageException err) {
      logger.atSevere().withCause(err).log("Cannot list open changes of %s", projectName);
      return Collections.emptySet();
    }
  }

  private static boolean skip(String name) {
    return name.startsWith(RefNames.REFS_CHANGES)
        || name.startsWith(RefNames.REFS_CACHE_AUTOMERGE)
        || MagicBranch.isMagicBranch(name);
  }
}
