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

import com.google.common.base.Optional;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import eu.medsea.mimeutil.MimeType;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.NB;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Exports a single version of a patch as a normal file download.
 * <p>
 * This can be relatively unsafe with Microsoft Internet Explorer 6.0 as the
 * browser will (rather incorrectly) treat an HTML or JavaScript file its
 * supposed to download as though it was served by this site, and will execute
 * it with the site's own protection domain. This opens a massive security hole
 * so we package the content into a zip file.
 */
@SuppressWarnings("serial")
@Singleton
public class CatServlet extends HttpServlet {
  private static final MimeType ZIP = new MimeType("application/zip");
  private final Provider<ReviewDb> requestDb;
  private final GitRepositoryManager repoManager;
  private final SecureRandom rng;
  private final FileTypeRegistry registry;
  private final Provider<CurrentUser> userProvider;
  private final ChangeControl.GenericFactory changeControl;
  private final ChangeEditUtil changeEditUtil;

  @Inject
  CatServlet(GitRepositoryManager grm,
      Provider<ReviewDb> sf,
      FileTypeRegistry ftr,
      ChangeControl.GenericFactory ccf,
      Provider<CurrentUser> usrprv,
      ChangeEditUtil ceu) {
    requestDb = sf;
    repoManager = grm;
    rng = new SecureRandom();
    registry = ftr;
    changeControl = ccf;
    userProvider = usrprv;
    changeEditUtil = ceu;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
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
    final Project project;
    final String revision;
    try {
      final ReviewDb db = requestDb.get();
      final ChangeControl control = changeControl.validateFor(changeId,
          userProvider.get());

      project = control.getProject();

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
        PatchSet patchSet = db.patchSets().get(patchKey.getParentKey());
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

    ObjectLoader blobLoader;
    RevCommit fromCommit;
    String suffix;
    String path = patchKey.getFileName();
    try (Repository repo = repoManager.openRepository(project.getNameKey())) {
      try (ObjectReader reader = repo.newObjectReader();
          RevWalk rw = new RevWalk(reader)) {
        RevCommit c;

        c = rw.parseCommit(ObjectId.fromString(revision));
        if (side == 0) {
          fromCommit = c;
          suffix = "new";

        } else if (1 <= side && side - 1 < c.getParentCount()) {
          fromCommit = rw.parseCommit(c.getParent(side - 1));
          if (c.getParentCount() == 1) {
            suffix = "old";
          } else {
            suffix = "old" + side;
          }

        } else {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        try (TreeWalk tw = TreeWalk.forPath(reader, path, fromCommit.getTree())) {
          if (tw == null) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
          }

          if (tw.getFileMode(0).getObjectType() == Constants.OBJ_BLOB) {
            blobLoader = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);

          } else {
            rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
          }
        }
      }
    } catch (RepositoryNotFoundException e) {
      getServletContext().log("Cannot open repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    } catch (IOException | RuntimeException e) {
      getServletContext().log("Cannot read repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    final byte[] raw =
        blobLoader.isLarge() ? null : blobLoader.getCachedBytes();
    final long when = fromCommit.getCommitTime() * 1000L;

    rsp.setDateHeader("Last-Modified", when);
    CacheHeaders.setNotCacheable(rsp);

    OutputStream out;
    ZipOutputStream zo;

    final MimeType contentType = registry.getMimeType(path, raw);
    if (!registry.isSafeInline(contentType)) {
      // The content may not be safe to transmit inline, as a browser might
      // interpret it as HTML or JavaScript hosted by this site. Such code
      // might then run in the site's security domain, and may be able to use
      // the user's cookies to perform unauthorized actions.
      //
      // Usually, wrapping the content into a ZIP file forces the browser to
      // save the content to the local system instead.
      //

      rsp.setContentType(ZIP.toString());
      rsp.setHeader("Content-Disposition", "attachment; filename=\""
          + safeFileName(path, suffix) + ".zip" + "\"");

      zo = new ZipOutputStream(rsp.getOutputStream());

      final ZipEntry e = new ZipEntry(safeFileName(path, rand(req, suffix)));
      e.setComment(fromCommit.name() + ":" + path);
      e.setSize(blobLoader.getSize());
      e.setTime(when);
      zo.putNextEntry(e);
      out = zo;

    } else {
      rsp.setContentType(contentType.toString());
      rsp.setHeader("Content-Length", "" + blobLoader.getSize());

      out = rsp.getOutputStream();
      zo = null;
    }

    if (raw != null) {
      out.write(raw);
    } else {
      blobLoader.copyTo(out);
    }

    if (zo != null) {
      zo.closeEntry();
    }
    out.close();
  }

  private static String safeFileName(String fileName, final String suffix) {
    // Convert a file path (e.g. "src/Init.c") to a safe file name with
    // no meta-characters that might be unsafe on any given platform.
    //
    final int slash = fileName.lastIndexOf('/');
    if (slash >= 0) {
      fileName = fileName.substring(slash + 1);
    }

    final StringBuilder r = new StringBuilder(fileName.length());
    for (int i = 0; i < fileName.length(); i++) {
      final char c = fileName.charAt(i);
      if (c == '_' || c == '-' || c == '.' || c == '@') {
        r.append(c);

      } else if ('0' <= c && c <= '9') {
        r.append(c);

      } else if ('A' <= c && c <= 'Z') {
        r.append(c);

      } else if ('a' <= c && c <= 'z') {
        r.append(c);

      } else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
        r.append('-');

      } else {
        r.append('_');
      }
    }
    fileName = r.toString();

    final int ext = fileName.lastIndexOf('.');
    if (ext <= 0) {
      return fileName + "_" + suffix;

    } else {
      return fileName.substring(0, ext) + "_" + suffix
          + fileName.substring(ext);
    }
  }

  private String rand(final HttpServletRequest req, final String suffix)
      throws UnsupportedEncodingException {
    // Produce a random suffix that is difficult (or nearly impossible)
    // for an attacker to guess in advance. This reduces the risk that
    // an attacker could upload a *.class file and have us send a ZIP
    // that can be invoked through an applet tag in the victim's browser.
    //
    final MessageDigest md = Constants.newMessageDigest();
    final byte[] buf = new byte[8];

    NB.encodeInt32(buf, 0, req.getRemotePort());
    md.update(req.getRemoteAddr().getBytes("UTF-8"));
    md.update(buf, 0, 4);

    NB.encodeInt64(buf, 0, TimeUtil.nowMs());
    md.update(buf, 0, 8);

    rng.nextBytes(buf);
    md.update(buf, 0, 8);

    return suffix + "-" + ObjectId.fromRaw(md.digest()).name();
  }

}
