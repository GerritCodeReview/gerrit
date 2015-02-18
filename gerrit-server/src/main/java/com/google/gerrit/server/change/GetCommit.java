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

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.concurrent.TimeUnit;

public class GetCommit implements RestReadView<RevisionResource> {
  private final ChangeJson json;

  @Option(name = "--links", usage = "Add weblinks")
  private boolean addLinks;

  @Inject
  GetCommit(ChangeJson json) {
    this.json = json;
  }

  @Override
  public Response<CommitInfo> apply(RevisionResource resource)
      throws OrmException {
    try {
      Response<CommitInfo> r =
          Response.ok(json.toCommit(resource.getPatchSet(), resource
              .getChange().getProject(), addLinks));
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    } catch (PatchSetInfoNotAvailableException e) {
      throw new OrmException(e);
    }
  }
}
