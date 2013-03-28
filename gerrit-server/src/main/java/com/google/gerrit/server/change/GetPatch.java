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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class GetPatch implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final SimpleDateFormat df;

  @Inject
  GetPatch(@GerritPersonIdent final PersonIdent gerritIdent,
      GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
    this.df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    this.df.setCalendar(Calendar.getInstance(gerritIdent.getTimeZone(), Locale.US));
  }

  @Override
  public String apply(RevisionResource rsrc)
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

          StringBuilder b = new StringBuilder();
          appendHeader(b, commit);
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          DiffFormatter f = new DiffFormatter(out);
          f.setRepository(repo);
          f.format(parents[0].getTree(), commit.getTree());
          f.flush();
          b.append(out.toString(UTF_8.name()));
          return b.toString();
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

  private void appendHeader(StringBuilder b, RevCommit commit) {
    PersonIdent author = commit.getAuthorIdent();
    b.append("From ").append(commit.getId().getName()).append(" ")
        .append(df.format(Long.valueOf(System.currentTimeMillis()))).append("\n");
    b.append("From: ").append(author.getName()).append(" <").append(author.getEmailAddress()).append(">\n");
    b.append("Date: ").append(df.format(author.getWhen())).append("\n");
    b.append("Subject: [PATCH] ").append(commit.getShortMessage());
    String message = commit.getFullMessage().substring(
        commit.getShortMessage().length());
    b.append(message).append("\n\n");
  }
}
