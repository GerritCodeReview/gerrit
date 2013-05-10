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

import static com.google.common.base.Charsets.UTF_8;

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class GetPatch implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;

  @Inject
  GetPatch(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public BinaryResult apply(RevisionResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException {
    Project.NameKey project = rsrc.getControl().getProject().getNameKey();
    boolean close = true;
    try {
      final Repository repo = repoManager.openRepository(project);
      try {
        final RevWalk rw = new RevWalk(repo);
        try {
          final RevCommit commit =
              rw.parseCommit(ObjectId.fromString(rsrc.getPatchSet()
                  .getRevision().get()));
          RevCommit[] parents = commit.getParents();
          if (parents.length > 1) {
            throw new ResourceConflictException(
                "Revision has more than 1 parent.");
          } else if (parents.length == 0) {
            throw new ResourceConflictException("Revision has no parent.");
          }
          final RevCommit base = parents[0];
          rw.parseBody(base);

          BinaryResult bin = new BinaryResult() {
            @Override
            public void writeTo(OutputStream out) throws IOException {
              out.write(formatEmailHeader(commit).getBytes(UTF_8));
              DiffFormatter fmt = new DiffFormatter(out);
              fmt.setRepository(repo);
              fmt.format(base.getTree(), commit.getTree());
              fmt.flush();
            }

            @Override
            public void close() throws IOException {
              rw.release();
              repo.close();
            }
          }.setContentType("application/mbox")
           .base64();
          close = false;
          return bin;
        } finally {
          if (close) {
            rw.release();
          }
        }
      } finally {
        if (close) {
          repo.close();
        }
      }
    } catch (IOException e) {
      throw new ResourceNotFoundException();
    }
  }

  private static String formatEmailHeader(RevCommit commit) {
    StringBuilder b = new StringBuilder();
    PersonIdent author = commit.getAuthorIdent();
    String subject = commit.getShortMessage();
    String msg = commit.getFullMessage().substring(subject.length());
    b.append("From ").append(commit.getName())
     .append(' ')
     .append("Mon Sep 17 00:00:00 2001\n")
     .append("From: ").append(author.getName())
     .append(" <").append(author.getEmailAddress()).append(">\n")
     .append("Date: ").append(formatDate(author)).append("\n")
     .append("Subject: [PATCH] ").append(subject)
     .append("\n\n")
     .append(msg);
    if (!msg.endsWith("\n")) {
     b.append('\n');
    }
    return b.append("---\n\n").toString();
  }

  private static String formatDate(PersonIdent author) {
    SimpleDateFormat df = new SimpleDateFormat(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        Locale.US);
    df.setCalendar(Calendar.getInstance(author.getTimeZone(), Locale.US));
    return df.format(author.getWhen());
  }
}
