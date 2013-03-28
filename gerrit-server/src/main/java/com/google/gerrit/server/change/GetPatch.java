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

import com.google.common.base.Charsets;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.StreamingResponse;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class GetPatch implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final PersonIdent gerritIdent;

  @Inject
  GetPatch(@GerritPersonIdent final PersonIdent gerritIdent,
      GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
    this.gerritIdent = gerritIdent;
  }

  @Override
  public StreamingResponse apply(RevisionResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException {
    Project.NameKey project = rsrc.getControl().getProject().getNameKey();
    try {
      Repository repo = repoManager.openRepository(project);
      try {
        RevWalk rw = new RevWalk(repo);
        try {
          RevCommit commit =
              rw.parseCommit(ObjectId.fromString(rsrc.getPatchSet()
                  .getRevision().get()));
          RevCommit[] parents = commit.getParents();
          if (parents.length > 1) {
            throw new ResourceConflictException(
                "Revision has more than 1 parent.");
          } else if (parents.length == 0) {
            throw new ResourceConflictException("Revision has no parent.");
          }
          rw.parseBody(parents[0]);
          return new PatchResponse(project, commit, parents[0],
              gerritIdent.getTimeZone());
        } finally {
          rw.release();
        }
      } finally {
        repo.close();
      }
    } catch (IOException e) {
      throw new ResourceNotFoundException();
    }
  }

  private class PatchResponse implements StreamingResponse {
    private final Project.NameKey project;
    private final RevCommit commit;
    private final RevCommit parent;
    private final SimpleDateFormat df;

    PatchResponse(Project.NameKey project, RevCommit commit, RevCommit parent,
        TimeZone tz) {
      this.project = project;
      this.commit = commit;
      this.parent = parent;
      this.df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
      this.df.setCalendar(Calendar.getInstance(tz, Locale.US));
    }

    @Override
    public String getContentType() {
      return "text/plain;charset=UTF-8";
    }

    @Override
    public void stream(OutputStream out) throws IOException {
      writeHeader(out);
      DiffFormatter f = new DiffFormatter(out);
      Repository repo = repoManager.openRepository(project);
      try {
        f.setRepository(repo);
        f.format(parent.getTree(), commit.getTree());
      } finally {
        repo.close();
        f.flush();
      }
    }

    private void writeHeader(OutputStream out) {
      PersonIdent author = commit.getAuthorIdent();
      StringBuilder b = new StringBuilder();
      b.append("From ").append(commit.getId().getName()).append(" ")
          .append(df.format(Long.valueOf(System.currentTimeMillis()))).append("\n");
      b.append("From: ").append(author.getName()).append(" <").append(author.getEmailAddress()).append(">\n");
      b.append("Date: ").append(df.format(author.getWhen())).append("\n");
      b.append("Subject: [PATCH] ").append(commit.getShortMessage());
      String message = commit.getFullMessage().substring(
          commit.getShortMessage().length());
      b.append(message).append("\n\n");

      PrintWriter w =
          new PrintWriter(new OutputStreamWriter(out, Charsets.UTF_8));
      w.print(b.toString());
      w.flush();
    }
  }
}
