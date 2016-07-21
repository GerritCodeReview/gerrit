// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.server.change.HashtagsUtil.cleanupHashtag;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwt.dev.util.collect.HashMap;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public interface ReceiveCommits {
  public static final Pattern NEW_PATCHSET = Pattern.compile(
      "^" + REFS_CHANGES + "(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  Capable canUpload();
  void init();
  void addReviewers(Collection<Account.Id> reviewerId);
  void addExtraCC(Collection<Account.Id> ccId);
  void addError(String string);
  void sendMessages();
  ReceivePack getReceivePack();
  Project getProject();
  MessageSender getMessageSender();
  void processCommands(Collection<ReceiveCommand> commands,
      MultiProgressMonitor progress);

  public interface MessageSender {
    void sendMessage(String what);
    void sendError(String what);
    void sendBytes(byte[] what);
    void sendBytes(byte[] what, int off, int len);
    void flush();
  }

  static class MagicBranchInput {
    private static final Logger log =
        LoggerFactory.getLogger(MagicBranchInput.class);

    private static final Splitter COMMAS = Splitter.on(',').omitEmptyStrings();

    final ReceiveCommand cmd;
    Branch.NameKey dest;
    RefControl ctl;
    Set<Account.Id> reviewer = Sets.newLinkedHashSet();
    Set<Account.Id> cc = Sets.newLinkedHashSet();
    Map<String, Short> labels = new HashMap<>();
    String message;
    List<RevCommit> baseCommit;
    LabelTypes labelTypes;
    CmdLineParser clp;
    Set<String> hashtags = new HashSet<>();
    NotesMigration notesMigration;

    @Option(name = "--base", metaVar = "BASE", usage = "merge base of changes")
    List<ObjectId> base;

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

    @Option(name = "--draft", usage = "mark new/updated changes as draft")
    boolean draft;

    @Option(name = "--edit", aliases = {"-e"}, usage = "upload as change edit")
    boolean edit;

    @Option(name = "--submit", usage = "immediately submit the change")
    boolean submit;

    @Option(name = "--notify",
        usage = "Notify handling that defines to whom email notifications "
            + "should be sent. Allowed values are NONE, OWNER, "
            + "OWNER_REVIEWERS, ALL. If not set, the default is ALL.")
    NotifyHandling notify = NotifyHandling.ALL;

    @Option(name = "--reviewer", aliases = {"-r"}, metaVar = "EMAIL",
        usage = "add reviewer to changes")
    void reviewer(Account.Id id) {
      reviewer.add(id);
    }

    @Option(name = "--cc", metaVar = "EMAIL", usage = "notify user by CC")
    void cc(Account.Id id) {
      cc.add(id);
    }

    @Option(name = "--publish", usage = "publish new/updated changes")
    void publish(boolean publish) {
      draft = !publish;
    }

    @Option(name = "--label", aliases = {"-l"}, metaVar = "LABEL+VALUE",
        usage = "label(s) to assign (defaults to +1 if no value provided")
    void addLabel(String token) throws CmdLineException {
      LabelVote v = LabelVote.parse(token);
      try {
        LabelType.checkName(v.label());
        ApprovalsUtil.checkLabel(labelTypes, v.label(), v.value());
      } catch (IllegalArgumentException e) {
        throw clp.reject(e.getMessage());
      }
      labels.put(v.label(), v.value());
    }

    @Option(name = "--message", aliases = {"-m"}, metaVar = "MESSAGE",
        usage = "Comment message to apply to the review")
    void addMessage(final String token) {
      // git push does not allow spaces in refs.
      message = token.replace("_", " ");
    }

    @Option(name = "--hashtag", aliases = {"-t"}, metaVar = "HASHTAG",
        usage = "add hashtag to changes")
    void addHashtag(String token) throws CmdLineException {
      if (!notesMigration.readChanges()) {
        throw clp.reject("cannot add hashtags; noteDb is disabled");
      }
      String hashtag = cleanupHashtag(token);
      if (!hashtag.isEmpty()) {
        hashtags.add(hashtag);
      }
      //TODO(dpursehouse): validate hashtags
    }

    MagicBranchInput(ReceiveCommand cmd, LabelTypes labelTypes,
        NotesMigration notesMigration) {
      this.cmd = cmd;
      this.draft = cmd.getRefName().startsWith(MagicBranch.NEW_DRAFT_CHANGE);
      this.labelTypes = labelTypes;
      this.notesMigration = notesMigration;
    }

    MailRecipients getMailRecipients() {
      return new MailRecipients(reviewer, cc);
    }

    String parse(CmdLineParser clp, Repository repo, Set<String> refs)
        throws CmdLineException {
      String ref = RefNames.fullName(
          MagicBranch.getDestBranchName(cmd.getRefName()));

      int optionStart = ref.indexOf('%');
      if (0 < optionStart) {
        ListMultimap<String, String> options = LinkedListMultimap.create();
        for (String s : COMMAS.split(ref.substring(optionStart + 1))) {
          int e = s.indexOf('=');
          if (0 < e) {
            options.put(s.substring(0, e), s.substring(e + 1));
          } else {
            options.put(s, "");
          }
        }
        clp.parseOptionMap(options);
        ref = ref.substring(0, optionStart);
      }

      // Split the destination branch by branch and topic. The topic
      // suffix is entirely optional, so it might not even exist.
      String head = readHEAD(repo);
      int split = ref.length();
      for (;;) {
        String name = ref.substring(0, split);
        if (refs.contains(name) || name.equals(head)) {
          break;
        }

        split = name.lastIndexOf('/', split - 1);
        if (split <= Constants.R_REFS.length()) {
          return ref;
        }
      }
      if (split < ref.length()) {
        topic = Strings.emptyToNull(ref.substring(split + 1));
      }
      return ref.substring(0, split);
    }

    static String readHEAD(Repository repo) {
      try {
        return repo.getFullBranch();
      } catch (IOException e) {
        log.error("Cannot read HEAD symref", e);
        return null;
      }
    }
  }
}
