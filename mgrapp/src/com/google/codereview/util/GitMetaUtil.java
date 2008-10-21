// Copyright 2008 Google Inc.
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

package com.google.codereview.util;

import com.google.codereview.internal.GitMeta.GitCommit;
import com.google.codereview.internal.GitMeta.GitPerson;

import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;

public class GitMetaUtil {
  public static GitCommit toGitCommit(final RevCommit c) {
    final GitCommit.Builder b = GitCommit.newBuilder();
    b.setId(c.getId().name());
    for (final RevCommit p : c.getParents())
      b.addParentId(p.getId().name());
    b.setAuthor(toGitPerson(c.getAuthorIdent()));
    b.setCommitter(toGitPerson(c.getCommitterIdent()));
    b.setMessage(c.getFullMessage());
    b.setSubject(c.getShortMessage());
    return b.build();
  }

  public static GitPerson toGitPerson(final PersonIdent who) {
    final GitPerson.Builder b = GitPerson.newBuilder();
    b.setName(who.getName());
    b.setEmail(who.getEmailAddress());
    b.setWhen((int) (who.getWhen().getTime() / 1000L));
    b.setTz(who.getTimeZoneOffset());
    return b.build();
  }

  public static boolean isGitRepository(final File gitdir) {
    return new File(gitdir, "config").isFile()
        && new File(gitdir, "HEAD").isFile()
        && new File(gitdir, "objects").isDirectory()
        && new File(gitdir, "refs/heads").isDirectory();
  }

  public static Repository open(final File gitdir) throws IOException {
    if (isGitRepository(gitdir)) {
      return new Repository(gitdir);
    }

    if (isGitRepository(new File(gitdir, ".git"))) {
      return new Repository(new File(gitdir, ".git"));
    }

    final String name = gitdir.getName();
    final File parent = gitdir.getParentFile();
    if (isGitRepository(new File(parent, name + ".git"))) {
      return new Repository(new File(parent, name + ".git"));
    }

    return null;
  }
}
