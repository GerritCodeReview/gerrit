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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.ssh.SshInfo;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends an email alerting a user to a new change for them to review. */
public abstract class NewChangeSender extends ChangeEmail {
  private static final Logger log =
      LoggerFactory.getLogger(NewChangeSender.class);

  private final SshInfo sshInfo;
  private final Set<Account.Id> reviewers = new HashSet<Account.Id>();
  private final Set<Account.Id> extraCC = new HashSet<Account.Id>();

  protected NewChangeSender(EmailArguments ea, String anonymousCowardName,
      SshInfo sshInfo, Change c) {
    super(ea, anonymousCowardName, c, "newchange");
    this.sshInfo = sshInfo;
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    setHeader("Message-ID", getChangeMessageThreadId());

    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    rcptToAuthors(RecipientType.CC);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(velocifyFile("NewChange.vm"));
  }

  public List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  public String getSshHost() {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }

  public boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  /** Show patch set as unified difference.  */
  public String getUnifiedDiff() {
    PatchList patchList;
    try {
      patchList = getPatchList();
      if (patchList.getOldId() == null) {
        // Octopus merges are not well supported for diff output by Gerrit.
        // Currently these always have a null oldId in the PatchList.
        return "";
      }
    } catch (PatchListNotAvailableException e) {
      log.error("Cannot format patch", e);
      return "";
    }

    TemporaryBuffer.Heap buf =
        new TemporaryBuffer.Heap(args.settings.maximumDiffSize);
    DiffFormatter fmt = new DiffFormatter(buf);
    Repository git;
    try {
      git = args.server.openRepository(change.getProject());
    } catch (IOException e) {
      log.error("Cannot open repository to format patch", e);
      return "";
    }
    try {
      fmt.setRepository(git);
      fmt.setDetectRenames(true);

      List<DiffEntry> entries =
          fmt.scan(patchList.getOldId(), patchList.getNewId());
      try {
        fmt.format(entries);
        return RawParseUtils.decode(buf.toByteArray());
      } catch (IOException err) {
        if (!JGitText.get().inMemoryBufferLimitExceeded.equals(err.getMessage())) {
          throw err;
        }

        StringBuilder sb = new StringBuilder();
        for (DiffEntry e : entries) {
          switch (e.getChangeType()) {
            case ADD:
              sb.append("A    ").append(e.getNewPath());
              break;
            case MODIFY:
              sb.append("M    ").append(e.getNewPath());
              break;
            case DELETE:
              sb.append("D    ").append(e.getOldPath());
              break;
            case RENAME:
              sb.append(String.format("R%03d %s\n     to %s",
                  e.getScore(), e.getOldPath(), e.getNewPath()));
              break;
            case COPY:
              sb.append(String.format("C%03d %s\n     from %s",
                  e.getScore(), e.getNewPath(), e.getOldPath()));
              break;
          }
          sb.append('\n');
        }
        sb.append(String.format(
            " %d files changed, %d insertions(+), %d deletions(-)\n",
            entries.size(), patchList.getInsertions(), patchList.getDeletions()));
        return sb.toString();
      }
    } catch (IOException e) {
      log.error("Cannot format patch", e);
      return "";
    } finally {
      fmt.release();
      git.close();
    }
  }
}
