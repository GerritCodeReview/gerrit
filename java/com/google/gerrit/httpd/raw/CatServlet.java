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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Exports a single version of a patch as a normal file download.
 *
 * <p>This can be relatively unsafe with Microsoft Internet Explorer 6.0 as the browser will (rather
 * incorrectly) treat an HTML or JavaScript file its supposed to download as though it was served by
 * this site, and will execute it with the site's own protection domain. This opens a massive
 * security hole so we package the content into a zip file.
 */
@Singleton
public class CatServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final ChangeEditUtil changeEditUtil;
  private final PatchSetUtil psUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  @Inject
  CatServlet(
      ChangeEditUtil ceu,
      PatchSetUtil psu,
      ChangeNotes.Factory cnf,
      PermissionBackend pb,
      ProjectCache pc) {
    changeEditUtil = ceu;
    psUtil = psu;
    changeNotesFactory = cnf;
    permissionBackend = pb;
    projectCache = pc;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
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

    final Change.Id changeId = patchKey.patchSetId().changeId();
    String revision;
    try {
      ChangeNotes notes = changeNotesFactory.createChecked(changeId);
      permissionBackend.currentUser().change(notes).check(ChangePermission.READ);
      projectCache.checkedGet(notes.getProjectName()).checkStatePermitsRead();
      if (patchKey.patchSetId().get() == 0) {
        // change edit
        Optional<ChangeEdit> edit = changeEditUtil.byChange(notes);
        if (edit.isPresent()) {
          revision = ObjectId.toString(edit.get().getEditCommit());
        } else {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      } else {
        PatchSet patchSet = psUtil.get(notes, patchKey.patchSetId());
        if (patchSet == null) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        revision = patchSet.commitId().name();
      }
    } catch (ResourceConflictException | NoSuchChangeException | AuthException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    } catch (PermissionBackendException | IOException e) {
      getServletContext().log("Cannot query database", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String path = patchKey.fileName();
    String restUrl =
        String.format(
            "%s/changes/%d/revisions/%s/files/%s/download?parent=%d",
            req.getContextPath(), changeId.get(), revision, Url.encode(path), side);
    rsp.sendRedirect(restUrl);
  }
}
