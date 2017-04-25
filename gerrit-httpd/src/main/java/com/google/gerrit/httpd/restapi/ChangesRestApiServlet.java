// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ChangesRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;

  @Inject
  ChangesRestApiServlet(RestApiServlet.Globals globals, Provider<ChangesCollection> changes) {
    super(globals, changes);
  }

  @Override
  protected String topLevelRedirect(RestResource rsrc, IdString id, Iterable<IdString> path) {
    if (!(rsrc instanceof ChangeResource)) {
      return null;
    }
    if (id.encoded().contains("/+/")) {
      // New project-based changeId; no redirect
      return null;
    }
    ChangeResource changeResource = (ChangeResource) rsrc;
    List<String> idPart =
        ImmutableList.of(
            "/changes",
            changeResource.getProject().get(),
            "+",
            Integer.toString(changeResource.getChange().getChangeId()));
    return Joiner.on('/').join(Iterables.concat(idPart, path));
  }
}
