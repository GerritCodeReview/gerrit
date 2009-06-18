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

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SafeFile;
import com.google.gerrit.client.reviewdb.SafeFileAccess;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.treewalk.TreeWalk;
import org.spearce.jgit.util.NB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
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
@SuppressWarnings("serial")
public class CatServlet extends HttpServlet {
  private static final String APPLICATION_OCTET_STREAM =
      "application/octet-stream";
  private GerritServer server;
  private SecureRandom rng;

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
    rng = new SecureRandom();
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

    final Account.Id me = new GerritCall(server, req, rsp).getAccountId();
    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    final Project project;
    final Change change;
    final PatchSet patchSet;
    final Patch patch;
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        change = db.changes().get(changeId);
        if (change == null) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        final ProjectCache.Entry e =
            Common.getProjectCache().get(change.getDest().getParentKey());
        if (e == null || !canRead(me, e)) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        project = e.getProject();
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
    final String path = patch.getFileName();
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

      tw = TreeWalk.forPath(repo, path, fromCommit.getTree());
      if (tw == null) {
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (tw.getFileMode(0).getObjectType() == Constants.OBJ_BLOB) {
        blobData = repo.openBlob(tw.getObjectId(0)).getCachedBytes();

      } else {
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
    String contentType = guessContentType(project, path, blobData);
    final String fn;
    final byte[] outData;

    if (isSafeInline(contentType, keyStr)) {
      fn = safeFileName(path, suffix);
      outData = blobData;

    } else {
      // The content may not be safe to transmit inline, as a browser might
      // interpret it as HTML or JavaScript hosted by this site. Such code
      // might then run in the site's security domain, and may be able to use
      // the user's cookies to perform unauthorized actions.
      //
      // Usually, wrapping the content into a ZIP file forces the browser to
      // save the content to the local system instead.
      //
      final ByteArrayOutputStream zip = new ByteArrayOutputStream();
      final ZipOutputStream zo = new ZipOutputStream(zip);
      final ZipEntry e = new ZipEntry(safeFileName(path, rand(req, suffix)));
      e.setComment(fromCommit.name() + ":" + path);
      e.setSize(blobData.length);
      e.setTime(when);
      zo.putNextEntry(e);
      zo.write(blobData);
      zo.closeEntry();
      zo.close();

      outData = zip.toByteArray();
      contentType = "application/zip";
      fn = safeFileName(path, suffix) + ".zip";
    }

    rsp.setContentType(contentType);
    rsp.setContentLength(outData.length);
    rsp.setDateHeader("Last-Modified", when);
    rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fn + "\"");
    rsp.setDateHeader("Expires", 0L);
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
    rsp.getOutputStream().write(outData);
  }

  private String guessContentType(final Project project, final String path,
      final byte[] content) {
    // When in doubt, call it a generic binary stream.
    //
    return APPLICATION_OCTET_STREAM;
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

    NB.encodeInt64(buf, 0, System.currentTimeMillis());
    md.update(buf, 0, 8);

    rng.nextBytes(buf);
    md.update(buf, 0, 8);

    return suffix + "-" + ObjectId.fromRaw(md.digest()).name();
  }

  /**
   * @return true if the file can safely be displayed in a direct link. This method should only
   * be invoked from the server side.
   */
  public static boolean isSafeInline(String contentType, String name) {
    boolean result = false;
    ReviewDb db = null;
    
    try {
      int index = name.lastIndexOf('.');
      if (index >= 0) {
        String suffix = name.substring(index + 1);
        String[] allowedMimeTypes = GerritServer.getInstance().getGerritConfig()
            .getStringList("viewinline", null, "allow");
        for (String allowedMimeType : allowedMimeTypes) {
          if (allowedMimeType.endsWith(suffix)) {
            return true;
          }
        }
      }
    } catch (OrmException e) {
      e.printStackTrace();
    } catch (XsrfException e) {
      e.printStackTrace();
    }
    
    return false;
  }
}
