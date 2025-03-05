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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetPatch implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;

  /**
   * What is this base64 and zip business doing here? Just give me a patch file!
   *
   * <p>The reason these legacy types are here is to force paleolithic browsers like IE6 to not do
   * cross site scripting. We have since invented X-Content-Type-Options: nosniff, which every
   * browser released since IE8 supports, making this madness unnecessary in the modern era, thus
   * the raw mode being available.
   *
   * <p>The only reason raw is not default is to not break old scripts.
   */
  private enum OutputType {
    ZIP,
    BASE64,
    RAW,
  }

  @Option(name = "--zip", usage = "retrieve a zip file with one patch file inside it")
  private boolean zip;

  @Option(name = "--raw", usage = "retrieve a plain-text patch file rather than base64")
  private boolean raw;

  @Option(name = "--download", usage = "send the file with a download hint")
  private boolean download;

  @Option(name = "--path")
  private String path;

  /** 1-based index of the parent's position in the commit object. */
  @Option(name = "--parent", metaVar = "parent-number")
  private Integer parentNum;

  @Inject
  GetPatch(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public Response<BinaryResult> apply(RevisionResource rsrc)
      throws BadRequestException,
          ResourceConflictException,
          IOException,
          ResourceNotFoundException {
    if (raw && zip) {
      throw new BadRequestException("raw and zip options are mutually exclusive");
    }
    final OutputType outputType;
    if (raw) {
      outputType = OutputType.RAW;
    } else if (zip) {
      outputType = OutputType.ZIP;
    } else {
      outputType = OutputType.BASE64;
    }

    try (Repository repo = repoManager.openRepository(rsrc.getProject());
        RevWalk rw = new RevWalk(repo)) {
      BinaryResult bin = null;
      try {
        final RevCommit commit = rw.parseCommit(rsrc.getPatchSet().commitId());
        RevCommit[] parents = commit.getParents();
        if (parentNum == null && parents.length > 1) {
          throw new ResourceConflictException("Revision has more than 1 parent.");
        }
        if (parents.length == 0) {
          throw new ResourceConflictException("Revision has no parent.");
        }
        if (parentNum != null && (parentNum < 1 || parentNum > parents.length)) {
          throw new BadRequestException(String.format("invalid parent number: %d", parentNum));
        }
        final RevCommit base = parents[parentNum == null ? 0 : parentNum - 1];
        rw.parseBody(base);

        bin =
            new BinaryResult() {
              @Override
              public void writeTo(OutputStream out) throws IOException {
                try (Repository repo = repoManager.openRepository(rsrc.getProject());
                    RevWalk rw = new RevWalk(repo)) {
                  switch (outputType) {
                    case ZIP -> {
                      ZipOutputStream zos = new ZipOutputStream(out);
                      ZipEntry e = new ZipEntry(fileName(rw, commit));
                      e.setTime(commit.getCommitTime() * 1000L);
                      zos.putNextEntry(e);
                      format(zos);
                      zos.closeEntry();
                      zos.finish();
                    }
                    case RAW, BASE64 -> format(out);
                  }
                }
              }

              private void format(OutputStream out) throws IOException {
                // Only add header if no path is specified
                if (path == null) {
                  out.write(formatEmailHeader(commit).getBytes(UTF_8));
                }
                DiffUtil.getFormattedDiff(repo, base, commit, path, out);
              }
            };

        if (path != null && bin.asString().isEmpty()) {
          throw new ResourceNotFoundException(String.format("File not found: %s.", path));
        }

        switch (outputType) {
          case ZIP ->
              bin.disableGzip()
                  .setContentType("application/zip")
                  .setAttachmentName(fileName(rw, commit) + ".zip");
          case BASE64 ->
              bin.base64()
                  .setContentType("application/mbox")
                  .setAttachmentName(download ? fileName(rw, commit) + ".base64" : null);
          case RAW ->
              bin.setContentType("text/plain")
                  .setAttachmentName(download ? fileName(rw, commit) : null);
        }

        return Response.ok(bin);
      } finally {
        if (bin != null) {
          bin.close();
        }
      }
    }
  }

  public GetPatch setPath(String path) {
    this.path = path;
    return this;
  }

  private static String formatEmailHeader(RevCommit commit) {
    StringBuilder b = new StringBuilder();
    PersonIdent author = commit.getAuthorIdent();
    String subject = commit.getShortMessage();
    String msg = commit.getFullMessage().substring(subject.length());
    if (msg.startsWith("\n\n")) {
      msg = msg.substring(2);
    }
    b.append("From ")
        .append(commit.getName())
        .append(' ')
        .append(
            "Mon Sep 17 00:00:00 2001\n") // Fixed timestamp to match output of C Git's format-patch
        .append("From: ")
        .append(author.getName())
        .append(" <")
        .append(author.getEmailAddress())
        .append(">\n")
        .append("Date: ")
        .append(formatDate(author))
        .append('\n')
        .append("Subject: [PATCH] ")
        .append(subject)
        .append('\n')
        .append('\n')
        .append(msg);
    if (!msg.endsWith("\n")) {
      b.append('\n');
    }
    return b.append("---\n\n").toString();
  }

  private static String formatDate(PersonIdent author) {
    SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    df.setCalendar(Calendar.getInstance(author.getTimeZone(), Locale.US));
    return df.format(author.getWhen());
  }

  private static String fileName(RevWalk rw, RevCommit commit) throws IOException {
    return abbreviateName(commit, rw.getObjectReader()) + ".diff";
  }
}
