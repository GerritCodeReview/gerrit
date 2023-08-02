// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.group;

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.groups.ProjectRefInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.VersionedAccountDestinations;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.NamedDestinationResource;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class CreateNamedDestinationChange
    implements RestModifyView<NamedDestinationResource, CreateNamedDestinationChange.Input> {
  public static class Input {
    public List<ProjectRefInfo> projectsAndRefs;
  }

  protected final AllUsersName allUsersName;
  protected final ChangeInserter.Factory changeInserterFactory;
  protected final ChangeJson.Factory jsonFactory;
  protected final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  protected final Sequences seq;
  protected final BatchUpdate.Factory updateFactory;

  @Inject
  public CreateNamedDestinationChange(
      AllUsersName allUsersName,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson.Factory jsonFactory,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      Sequences seq,
      BatchUpdate.Factory updateFactory) {
    this.allUsersName = allUsersName;
    this.changeInserterFactory = changeInserterFactory;
    this.jsonFactory = jsonFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.seq = seq;
    this.updateFactory = updateFactory;
  }

  @Override
  public Response<ChangeInfo> apply(NamedDestinationResource resource, Input input)
      throws BadRequestException, Exception {
    if (input == null || input.projectsAndRefs == null || input.projectsAndRefs.isEmpty()) {
      throw new BadRequestException("Input cannot be empty");
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      String destinationName = resource.getNamedDestination();
      String groupRef = RefNames.refsGroups(resource.getGroup().getGroupUUID());
      VersionedAccountDestinations versionedDestinations =
          VersionedAccountDestinations.forBranch(BranchNameKey.create(allUsersName, groupRef));
      versionedDestinations.load(md);
      versionedDestinations
          .getDestinationList()
          .updateLabel(
              destinationName,
              input.projectsAndRefs.stream()
                  .map((info) -> BranchNameKey.create(info.project, info.ref))
                  .collect(Collectors.toSet()));

      ObjectId oldCommit = versionedDestinations.getRevision();
      String oldCommitSha1 = oldCommit == null ? null : oldCommit.getName();

      md.setMessage("Review named destination change");
      md.setInsertChangeId(true);
      Change.Id changeId = Change.id(seq.nextChangeId());
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RevCommit commit =
            versionedDestinations.commitToNewRef(
                md, PatchSet.id(changeId, Change.INITIAL_PATCH_SET_ID).toRefName());

        if (commit.name().equals(oldCommitSha1)) {
          throw new BadRequestException("no change");
        }

        try (ObjectInserter objInserter = md.getRepository().newObjectInserter();
            ObjectReader objReader = objInserter.newReader();
            RevWalk rw = new RevWalk(objReader);
            BatchUpdate bu =
                updateFactory.create(
                    allUsersName, resource.getControl().getUser(), TimeUtil.now())) {
          bu.setRepository(md.getRepository(), rw, objInserter);
          ChangeInserter ins = newInserter(changeId, commit, groupRef);
          bu.insertChange(ins);
          bu.execute();
          return Response.created(jsonFactory.noOptions().format(ins.getChange()));
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  private ChangeInserter newInserter(Change.Id changeId, RevCommit commit, String refName) {
    return changeInserterFactory
        .create(changeId, commit, refName)
        .setMessage(
            ApprovalsUtil.renderMessageWithApprovals(1, ImmutableMap.of(), ImmutableMap.of()))
        .setValidate(false)
        .setUpdateRef(false);
  }
}
