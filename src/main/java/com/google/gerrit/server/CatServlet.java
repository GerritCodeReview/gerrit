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

package com.google.gerrit.server;

import static com.google.gerrit.client.rpc.BaseServiceImplementation.canRead;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
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
public class CatServlet extends HttpServlet {
  private GerritServer server;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    try {
      server = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot load GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot load GerritServer", e);
    }
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    String keyStr = req.getPathInfo();
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

    final Account.Id me = new GerritCall(server,req,rsp).getAccountId();
    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    final Change change;
    final PatchSet patchSet;
    final Patch patch;
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        change = db.changes().get(changeId);
        if (change == null || !canRead(me, change.getDest().getParentKey())) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        patchSet = db.patchSets().get(patchKey.getParentKey());
        patch = db.patches().get(patchKey);
        if (patchSet == null || patch == null) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("Cannot query database", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    final Repository repo;
    try {
      repo =
          server.getRepositoryCache()
              .get(change.getDest().getParentKey().get());
    } catch (InvalidRepositoryException e) {
      getServletContext().log("Cannot open repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    final byte[] blobData;
    final RevCommit fromCommit;
    final String suffix;
    try {
      final RevWalk rw = new RevWalk(repo);
      final RevCommit c;
      final TreeWalk tw;

      c = rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
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

      tw = TreeWalk.forPath(repo, patch.getFileName(), fromCommit.getTree());
      if (tw == null) {
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      switch (tw.getFileMode(0).getObjectType()) {
        case Constants.OBJ_BLOB:
          blobData = repo.openBlob(tw.getObjectId(0)).getCachedBytes();
          break;
        default:
          rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          return;
      }
    } catch (IOException e) {
      getServletContext().log("Cannot read repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    } catch (RuntimeException e) {
      getServletContext().log("Cannot read repository", e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    final long when = fromCommit.getCommitTime() * 1000L;
    String fn = safeFileName(patch.getFileName(), suffix);
    final byte[] outData;
    final String contentType;
    final boolean wrapInZip = true;

    if (wrapInZip) {
      final ByteArrayOutputStream zip = new ByteArrayOutputStream();
      final ZipOutputStream zo = new ZipOutputStream(zip);
      final ZipEntry e = new ZipEntry(fn);
      e.setSize(blobData.length);
      e.setTime(when);
      zo.putNextEntry(e);
      zo.write(blobData);
      zo.closeEntry();
      zo.close();

      outData = zip.toByteArray();
      contentType = "application/zip";
      fn += ".zip";

    } else {
      outData = blobData;
      contentType = "application/octet-stream";
    }

    rsp.setContentType(contentType);
    rsp.setContentLength(outData.length);
    rsp.setDateHeader("Last-Modified", when);
    rsp.setDateHeader("Expires", 0L);
    rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fn + "\"");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
    rsp.getOutputStream().write(outData);
  }

  private String safeFileName(String fileName, final String suffix) {
    final int slash = fileName.lastIndexOf('/');
    if (slash >= 0) {
      fileName = fileName.substring(slash + 1);
    }

    final StringBuilder r = new StringBuilder(fileName.length());
    for (int i = 0; i < fileName.length(); i++) {
      final char c = fileName.charAt(i);
      switch (c) {
        case ' ':
        case '_':
        case '-':
        case '.':
        case '@':
          r.append(c);
          continue;
      }
      if ('0' <= c && c <= '9') {
        r.append(c);
        continue;
      }
      if ('A' <= c && c <= 'Z') {
        r.append(c);
        continue;
      }
      if ('a' <= c && c <= 'z') {
        r.append(c);
        continue;
      }
    }
    fileName = r.toString();

    final int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return fileName + "_" + suffix;

    } else if (dot == 0) {
      return fileName + "_" + suffix;

    } else {
      return fileName.substring(0, dot) + "_" + suffix
          + fileName.substring(dot);
    }
  }
}
