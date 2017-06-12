// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exports a single version of a patch as a normal file download.
 *
 * <p>This can be relatively unsafe with Microsoft Internet Explorer 6.0 as the browser will (rather
 * incorrectly) treat an HTML or JavaScript file its supposed to download as though it was served by
 * this site, and will execute it with the site's own protection domain. This opens a massive
 * security hole so we package the content into a zip file.
 */
@SuppressWarnings("serial")
@Singleton
public class CatServlet extends HttpServlet {
  private final Provider<ReviewDb> requestDb;
  private final Provider<CurrentUser> userProvider;
  private final ChangeControl.GenericFactory changeControl;
  private final ChangeEditUtil changeEditUtil;
  private final PatchSetUtil psUtil;

  @Inject
  CatServlet(
      Provider<ReviewDb> sf,
      ChangeControl.GenericFactory ccf,
      Provider<CurrentUser> usrprv,
      ChangeEditUtil ceu,
      PatchSetUtil psu) {
    requestDb = sf;
    changeControl = ccf;
    userProvider = usrprv;
    changeEditUtil = ceu;
    psUtil = psu;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    String keyStr = req.getPathInfo();

    // We shouldn't have to do this extra decode pass, but somehow we
    // are now receiving our "^1" suffix as "%5E1", which confuses us
    // downstream. Other times we get our embedded "," as "%2C", which
    // is equally bad. And yet when these happen a "%2F" is left as-is,
    // rather than escaped as "%252F", which makes me feel really really
    // uncomfortable with a blind decode right here.
    //
    keyStr = Url.decode(keyStr);

    if (!keyStr.startsWith("/")) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    keyStr = keyStr.substring(1);

    final Patch.Key patchKey;
    final int side;
    {
      final int c = keyStr.lastIndexOf('^');
      if (c == 0) {
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (c < 0) {
        side = 0;
      } else {
        try {
          side = Integer.parseInt(keyStr.substring(c + 1));
          keyStr = keyStr.substring(0, c);
        } catch (NumberFormatException e) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      }

      try {
        patchKey = Patch.Key.parse(keyStr);
      } catch (NumberFormatException e) {
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
    }

    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    String revision;
    try {
      final ReviewDb db = requestDb.get();
      final ChangeControl control = changeControl.validateFor(db, changeId, userProvider.get());
      if (patchKey.getParentKey().get() == 0) {
        // change edit
        try {
          Optional<ChangeEdit> edit = changeEditUtil.byChange(control.getChange());
          if (edit.isPresent()) {
            revision = edit.get().getRevision().get();
          } else {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }
        } catch (AuthException e) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      } else {
        PatchSet patchSet = psUtil.get(db, control.getNotes(), patchKey.getParentKey());
        if (patchSet == null) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        revision = patchSet.getRevision().get();
      }
    } catch (NoSuchChangeException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    } catch (OrmException e) {
      getServletContext().log("Cannot query database", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String path = patchKey.getFileName();
    String restUrl =
        String.format(
            "%s/changes/%d/revisions/%s/files/%s/download?parent=%d",
            req.getContextPath(), changeId.get(), revision, Url.encode(path), side);
    rsp.sendRedirect(restUrl);
  }
}
