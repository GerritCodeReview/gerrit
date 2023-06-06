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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.DiffMetaInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.patch.RevisionsComparator;
import com.google.gerrit.server.patch.RevisionsComparator.RelationType;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Option;

/**
 * Returns some metadata about both revisions of a diff in a given change. The input options are
 * similar to the {@link com.google.gerrit.server.restapi.change.Files.ListFiles} endpoint.
 */
public class GetDiffMeta implements RestReadView<RevisionResource> {

  @Option(name = "--base", metaVar = "revision-id")
  String base;

  @Option(name = "--parent", metaVar = "parent-number")
  int parentNum;

  private final RevisionsComparator revisionsComparator;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  GetDiffMeta(RevisionsComparator revisionsComparator, ChangeData.Factory changeDataFactory) {
    this.revisionsComparator = revisionsComparator;
    this.changeDataFactory = changeDataFactory;
  }

  public GetDiffMeta setBase(@Nullable String base) {
    this.base = base;
    return this;
  }

  public GetDiffMeta setParentNum(int parentNum) {
    this.parentNum = parentNum;
    return this;
  }

  @Override
  public Response<?> apply(RevisionResource resource)
      throws AuthException, BadRequestException, ResourceConflictException,
          PermissionBackendException, IOException, ResourceNotFoundException {
    if (parentNum > 0 && base != null) {
      throw new BadRequestException("both parentNum and base cannot be set simultaneously");
    }
    ObjectId rhs = resource.getPatchSet().commitId();
    if (parentNum > 0) {
      return applyForParent(resource.getProject(), rhs);
    }
    return applyForBase(resource, rhs);
  }

  private Response<?> applyForParent(Project.NameKey project, ObjectId rhs)
      throws BadRequestException, IOException {
    DiffMetaInfo diffMetaInfo = new DiffMetaInfo();
    int rhsParentCount = revisionsComparator.getNumParents(project, rhs);
    if (parentNum > rhsParentCount) {
      throw new BadRequestException(
          "parentNum is greater than the number of parents of commit " + rhs.name());
    }
    diffMetaInfo.relationType =
        DiffMetaInfo.RelationType.valueOf(
            (rhsParentCount > 1
                ? RelationType.MERGE_COMMIT.name()
                : RelationType.LHS_PARENT_OF_RHS.name()));
    return Response.ok(diffMetaInfo);
  }

  private Response<?> applyForBase(RevisionResource revisionResource, ObjectId rhs)
      throws BadRequestException, IOException {
    if (base == null) {
      throw new BadRequestException("base revision must be specified");
    }
    ObjectId lhs = ObjectId.fromString(base);
    ChangeData cd = changeDataFactory.create(revisionResource.getChange());
    Collection<PatchSet> patchSets = cd.patchSets();
    boolean isPatchSet = patchSets.stream().anyMatch(p -> p.commitId().equals(lhs));
    if (!isPatchSet) {
      throw new BadRequestException(
          String.format("base revision %s is not a patchset of the change", base));
    }

    DiffMetaInfo diffMetaInfo = new DiffMetaInfo();
    RelationType relationType =
        revisionsComparator.getRelationType(revisionResource.getProject(), lhs, rhs);
    diffMetaInfo.relationType = DiffMetaInfo.RelationType.valueOf(relationType.name());
    return Response.ok(diffMetaInfo);
  }
}
