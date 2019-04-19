// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Redirects {@code domain.tld/123} to {@code domain.tld/c/project/+/123}. */
@Singleton
public class NumericChangeIdRedirectServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final ChangesCollection changesCollection;

  @Inject
  NumericChangeIdRedirectServlet(ChangesCollection changesCollection) {
    this.changesCollection = changesCollection;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    String idString = req.getPathInfo();
    if (idString.endsWith("/")) {
      idString = idString.substring(0, idString.length() - 1);
    }
    Change.Id id;
    try {
      id = Change.Id.parse(idString);
    } catch (IllegalArgumentException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    ChangeResource changeResource;
    try {
      changeResource = changesCollection.parse(id);
    } catch (ResourceConflictException | ResourceNotFoundException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    } catch (PermissionBackendException | RuntimeException e) {
      throw new IOException("Unable to lookup change " + id.get(), e);
    }
    String path =
        PageLinks.toChange(changeResource.getProject(), changeResource.getChange().getId());
    UrlModule.toGerrit(path, req, rsp);
  }
}
