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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Strings;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.PatchScript.FileMode;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import eu.medsea.mimeutil.MimeType;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.jgit.errors.LargeObjectException;
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

@Singleton
public class FileContentUtil {
  public static final String TEXT_X_GERRIT_COMMIT_MESSAGE = "text/x-gerrit-commit-message";
  public static final String TEXT_X_GERRIT_MERGE_LIST = "text/x-gerrit-merge-list";
  private static final String X_GIT_SYMLINK = "x-git/symlink";
  private static final String X_GIT_GITLINK = "x-git/gitlink";
  private static final int MAX_SIZE = 5 << 20;
  private static final String ZIP_TYPE = "application/zip";
  private static final Random rng = new Random();

  private final GitRepositoryManager repoManager;
  private final FileTypeRegistry registry;

  @Inject
  FileContentUtil(GitRepositoryManager repoManager, FileTypeRegistry ftr) {
    this.repoManager = repoManager;
    this.registry = ftr;
  }

  /**
   * Get the content of a file at a specific commit or one of it's parent commits.
   *
   * @param project A {@code Project} that this request refers to.
   * @param revstr An {@code ObjectId} specifying the commit.
   * @param path A string specifying the filepath.
   * @param parent A 1-based parent index to get the content from instead. Null if the content
   *     should be obtained from {@code revstr} instead.
   * @return Content of the file as {@code BinaryResult}.
   * @throws ResourceNotFoundException
   * @throws IOException
   */
  public BinaryResult getContent(
      ProjectState project, ObjectId revstr, String path, @Nullable Integer parent)
      throws BadRequestException, ResourceNotFoundException, IOException {
    try (Repository repo = openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      if (parent != null) {
        RevCommit revCommit = rw.parseCommit(revstr);
        if (revCommit == null) {
          throw new ResourceNotFoundException("commit not found");
        }
        if (parent > revCommit.getParentCount()) {
          throw new BadRequestException("invalid parent");
        }
        revstr = rw.parseCommit(revstr).getParent(Integer.max(0, parent - 1)).toObjectId();
      }
      return getContent(repo, project, revstr, path);
    }
  }

  public BinaryResult getContent(
      Repository repo, ProjectState project, ObjectId revstr, String path)
      throws IOException, ResourceNotFoundException {
    try (RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(revstr);
      ObjectReader reader = rw.getObjectReader();
      TreeWalk tw = TreeWalk.forPath(reader, path, commit.getTree());
      if (tw == null) {
        throw new ResourceNotFoundException();
      }

      org.eclipse.jgit.lib.FileMode mode = tw.getFileMode(0);
      ObjectId id = tw.getObjectId(0);
      if (mode == org.eclipse.jgit.lib.FileMode.GITLINK) {
        return BinaryResult.create(id.name()).setContentType(X_GIT_GITLINK).base64();
      }

      ObjectLoader obj = repo.open(id, OBJ_BLOB);
      byte[] raw;
      try {
        raw = obj.getCachedBytes(MAX_SIZE);
      } catch (LargeObjectException e) {
        raw = null;
      }

      String type;
      if (mode == org.eclipse.jgit.lib.FileMode.SYMLINK) {
        type = X_GIT_SYMLINK;
      } else {
        type = registry.getMimeType(path, raw).toString();
        type = resolveContentType(project, path, FileMode.FILE, type);
      }

      return asBinaryResult(raw, obj).setContentType(type).base64();
    }
  }

  private static BinaryResult asBinaryResult(byte[] raw, ObjectLoader obj) {
    if (raw != null) {
      return BinaryResult.create(raw);
    }
    BinaryResult result =
        new BinaryResult() {
          @Override
          public void writeTo(OutputStream os) throws IOException {
            obj.copyTo(os);
          }
        };
    result.setContentLength(obj.getSize());
    return result;
  }

  public BinaryResult downloadContent(
      ProjectState project, ObjectId revstr, String path, @Nullable Integer parent)
      throws ResourceNotFoundException, IOException {
    try (Repository repo = openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      String suffix = "new";
      RevCommit commit = rw.parseCommit(revstr);
      if (parent != null && parent > 0) {
        if (commit.getParentCount() == 1) {
          suffix = "old";
        } else {
          suffix = "old" + parent;
        }
        commit = rw.parseCommit(commit.getParent(parent - 1));
      }
      ObjectReader reader = rw.getObjectReader();
      TreeWalk tw = TreeWalk.forPath(reader, path, commit.getTree());
      if (tw == null) {
        throw new ResourceNotFoundException();
      }

      int mode = tw.getFileMode(0).getObjectType();
      if (mode != Constants.OBJ_BLOB) {
        throw new ResourceNotFoundException();
      }

      ObjectId id = tw.getObjectId(0);
      ObjectLoader obj = repo.open(id, OBJ_BLOB);
      byte[] raw;
      try {
        raw = obj.getCachedBytes(MAX_SIZE);
      } catch (LargeObjectException e) {
        raw = null;
      }

      MimeType contentType = registry.getMimeType(path, raw);
      return registry.isSafeInline(contentType)
          ? wrapBlob(path, obj, raw, contentType, suffix)
          : zipBlob(path, obj, commit, suffix);
    }
  }

  private BinaryResult wrapBlob(
      String path,
      final ObjectLoader obj,
      byte[] raw,
      MimeType contentType,
      @Nullable String suffix) {
    return asBinaryResult(raw, obj)
        .setContentType(contentType.toString())
        .setAttachmentName(safeFileName(path, suffix));
  }

  @SuppressWarnings("resource")
  private BinaryResult zipBlob(
      final String path, ObjectLoader obj, RevCommit commit, @Nullable final String suffix) {
    final String commitName = commit.getName();
    final long when = commit.getCommitTime() * 1000L;
    return new BinaryResult() {
      @Override
      public void writeTo(OutputStream os) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
          String decoration = randSuffix();
          if (!Strings.isNullOrEmpty(suffix)) {
            decoration = suffix + '-' + decoration;
          }
          ZipEntry e = new ZipEntry(safeFileName(path, decoration));
          e.setComment(commitName + ":" + path);
          e.setSize(obj.getSize());
          e.setTime(when);
          zipOut.putNextEntry(e);
          obj.copyTo(zipOut);
          zipOut.closeEntry();
        }
      }
    }.setContentType(ZIP_TYPE).setAttachmentName(safeFileName(path, suffix) + ".zip").disableGzip();
  }

  private static String safeFileName(String fileName, @Nullable String suffix) {
    // Convert a file path (e.g. "src/Init.c") to a safe file name with
    // no meta-characters that might be unsafe on any given platform.
    //
    int slash = fileName.lastIndexOf('/');
    if (slash >= 0) {
      fileName = fileName.substring(slash + 1);
    }

    StringBuilder r = new StringBuilder(fileName.length());
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

    int ext = fileName.lastIndexOf('.');
    if (suffix == null) {
      return fileName;
    } else if (ext <= 0) {
      return fileName + "_" + suffix;
    } else {
      return fileName.substring(0, ext) + "_" + suffix + fileName.substring(ext);
    }
  }

  private static String randSuffix() {
    // Produce a random suffix that is difficult (or nearly impossible)
    // for an attacker to guess in advance. This reduces the risk that
    // an attacker could upload a *.class file and have us send a ZIP
    // that can be invoked through an applet tag in the victim's browser.
    //
    Hasher h = Hashing.md5().newHasher();
    byte[] buf = new byte[8];

    NB.encodeInt64(buf, 0, TimeUtil.nowMs());
    h.putBytes(buf);

    rng.nextBytes(buf);
    h.putBytes(buf);

    return h.hash().toString();
  }

  public static String resolveContentType(
      ProjectState project, String path, FileMode fileMode, String mimeType) {
    switch (fileMode) {
      case FILE:
        if (Patch.COMMIT_MSG.equals(path)) {
          return TEXT_X_GERRIT_COMMIT_MESSAGE;
        }
        if (Patch.MERGE_LIST.equals(path)) {
          return TEXT_X_GERRIT_MERGE_LIST;
        }
        if (project != null) {
          for (ProjectState p : project.tree()) {
            String t = p.getConfig().getMimeTypes().getMimeType(path);
            if (t != null) {
              return t;
            }
          }
        }
        return mimeType;
      case GITLINK:
        return X_GIT_GITLINK;
      case SYMLINK:
        return X_GIT_SYMLINK;
      default:
        throw new IllegalStateException("file mode: " + fileMode);
    }
  }

  private Repository openRepository(ProjectState project)
      throws RepositoryNotFoundException, IOException {
    return repoManager.openRepository(project.getProject().getNameKey());
  }
}
