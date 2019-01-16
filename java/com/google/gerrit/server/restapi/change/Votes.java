// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.VoteResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.TreeMap;

@Singleton
public class Votes implements ChildCollection<ReviewerResource, VoteResource> {
  private final DynamicMap<RestView<VoteResource>> views;
  private final List list;

  @Inject
  Votes(DynamicMap<RestView<VoteResource>> views, List list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<VoteResource>> views() {
    return views;
  }

  @Override
  public RestView<ReviewerResource> list() throws AuthException {
    return list;
  }

  @Override
  public VoteResource parse(ReviewerResource reviewer, IdString id)
      throws ResourceNotFoundException, AuthException, MethodNotAllowedException {
    if (reviewer.getRevisionResource() != null && !reviewer.getRevisionResource().isCurrent()) {
      throw new MethodNotAllowedException("Cannot access on non-current patch set");
    }
    return new VoteResource(reviewer, id.get());
  }

  @Singleton
  public static class List implements RestReadView<ReviewerResource> {
    private final ApprovalsUtil approvalsUtil;

    @Inject
    List(ApprovalsUtil approvalsUtil) {
      this.approvalsUtil = approvalsUtil;
    }

    @Override
    public Map<String, Short> apply(ReviewerResource rsrc) throws MethodNotAllowedException {
      if (rsrc.getRevisionResource() != null && !rsrc.getRevisionResource().isCurrent()) {
        throw new MethodNotAllowedException("Cannot list votes on non-current patch set");
      }

      Map<String, Short> votes = new TreeMap<>();
      Iterable<PatchSetApproval> byPatchSetUser =
          approvalsUtil.byPatchSetUser(
              rsrc.getChangeResource().getNotes(),
              rsrc.getChange().currentPatchSetId(),
              rsrc.getReviewerUser().getAccountId(),
              null,
              null);
      for (PatchSetApproval psa : byPatchSetUser) {
        votes.put(psa.getLabel(), psa.getValue());
      }
      return votes;
    }
  }
}
