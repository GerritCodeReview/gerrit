// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.common.CommitMessageInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;

@Singleton
public class GetMessage implements RestReadView<ChangeResource> {
  private final ChangeData.Factory changeDataFactory;

  @Inject
  GetMessage(ChangeData.Factory changeDataFactory) {
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public Response<CommitMessageInfo> apply(ChangeResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    CommitMessageInfo commitMessageInfo = new CommitMessageInfo();
    commitMessageInfo.subject = resource.getChange().getSubject();

    ChangeData cd = changeDataFactory.create(resource.getNotes());
    commitMessageInfo.fullMessage = cd.commitMessage();
    commitMessageInfo.footers = getFooters(cd);
    return Response.ok(commitMessageInfo);
  }

  /**
   * Gets the footers of the change.
   *
   * <p>If there are multiple footers with the same key, only the last footer with that key is
   * returned.
   */
  private ImmutableMap<String, String> getFooters(ChangeData cd) {
    HashMap<String, String> footers = new HashMap<>();
    cd.commitFooters()
        .forEach(footerLine -> footers.put(footerLine.getKey(), footerLine.getValue()));
    return ImmutableMap.copyOf(footers);
  }
}
