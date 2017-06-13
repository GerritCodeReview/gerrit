// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.extensions.api.changes.RecipientType.*;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.EmailHeader;
import com.google.gerrit.server.mail.send.EmailHeader.AddressList;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;

public abstract class AbstractNotificationTest extends AbstractDaemonTest {
  @Before
  public void enableReviewerByEmail() throws Exception {
    setApiUser(admin);
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);
  }

  private static final SubjectFactory<FakeEmailSenderSubject, FakeEmailSender>
      FAKE_EMAIL_SENDER_SUBJECT_FACTORY =
          new SubjectFactory<FakeEmailSenderSubject, FakeEmailSender>() {
            @Override
            public FakeEmailSenderSubject getSubject(
                FailureStrategy failureStrategy, FakeEmailSender target) {
              return new FakeEmailSenderSubject(failureStrategy, target);
            }
          };

  protected static FakeEmailSenderSubject assertThat(FakeEmailSender sender) {
    return assertAbout(FAKE_EMAIL_SENDER_SUBJECT_FACTORY).that(sender);
  }

  protected void setEmailStrategy(TestAccount account, EmailStrategy strategy) throws Exception {
    setApiUser(account);
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = strategy;
    gApi.accounts().self().setPreferences(prefs);
  }

  protected static class FakeEmailSenderSubject
      extends Subject<FakeEmailSenderSubject, FakeEmailSender> {
    private Message message;
    private StagedUsers users;
    private Map<RecipientType, List<String>> recipients = new HashMap<>();

    FakeEmailSenderSubject(FailureStrategy failureStrategy, FakeEmailSender target) {
      super(failureStrategy, target);
    }

    public FakeEmailSenderSubject notSent() {
      if (actual().peekMessage() != null) {
        fail("a message wasn't sent");
      }
      return this;
    }

    public FakeEmailSenderSubject sent(String messageType, StagedUsers users) {
      message = actual().nextMessage();
      if (message == null) {
        fail("a message was sent");
      }
      recipients = new HashMap<>();
      recipients.put(TO, parseAddresses(message, "To"));
      recipients.put(CC, parseAddresses(message, "CC"));
      recipients.put(
          BCC,
          message
              .rcpt()
              .stream()
              .map(Address::getEmail)
              .filter(e -> !recipients.get(TO).contains(e) && !recipients.get(CC).contains(e))
              .collect(Collectors.toList()));
      this.users = users;
      if (!message.headers().containsKey("X-Gerrit-MessageType")) {
        fail("a message was sent with X-Gerrit-MessageType header");
      }
      EmailHeader header = message.headers().get("X-Gerrit-MessageType");
      if (!header.equals(new EmailHeader.String(messageType))) {
        fail("message of type " + messageType + " was sent; X-Gerrit-MessageType is " + header);
      }

      // Return a named subject that displays a human-readable table of
      // recipients.
      StringBuilder buf = new StringBuilder();
      buf.append('[');
      for (RecipientType type : ImmutableList.of(TO, CC, BCC)) {
        buf.append('\n');
        buf.append(type);
        buf.append(':');
        String delim = " ";
        for (String r : recipients.get(type)) {
          buf.append(delim);
          buf.append(users.emailToName(r));
          delim = ", ";
        }
      }
      buf.append("\n]");
      return named(buf.toString());
    }

    List<String> parseAddresses(Message msg, String headerName) {
      EmailHeader header = msg.headers().get(headerName);
      if (header == null) {
        return ImmutableList.of();
      }
      Truth.assertThat(header).isInstanceOf(AddressList.class);
      AddressList addrList = (AddressList) header;
      return addrList.getAddressList().stream().map(Address::getEmail).collect(Collectors.toList());
    }

    public FakeEmailSenderSubject to(String... emails) {
      return rcpt(users.supportReviewersByEmail ? TO : null, emails);
    }

    public FakeEmailSenderSubject cc(String... emails) {
      return rcpt(users.supportReviewersByEmail ? CC : null, emails);
    }

    public FakeEmailSenderSubject bcc(String... emails) {
      return rcpt(users.supportReviewersByEmail ? BCC : null, emails);
    }

    private FakeEmailSenderSubject rcpt(@Nullable RecipientType type, String[] emails) {
      for (String email : emails) {
        rcpt(type, email);
      }
      return this;
    }

    private void rcpt(@Nullable RecipientType type, String email) {
      rcpt(TO, email, TO.equals(type));
      rcpt(CC, email, CC.equals(type));
      rcpt(BCC, email, BCC.equals(type));
    }

    private void rcpt(@Nullable RecipientType type, String email, boolean expected) {
      if (recipients.get(type).contains(email) != expected) {
        fail(
            expected ? "notifies" : "doesn't notify",
            "]\n" + type + ": " + users.emailToName(email) + "\n]");
      }
    }

    public FakeEmailSenderSubject notTo(String... emails) {
      return rcpt(null, emails);
    }

    public FakeEmailSenderSubject to(TestAccount... accounts) {
      return rcpt(TO, accounts);
    }

    public FakeEmailSenderSubject cc(TestAccount... accounts) {
      return rcpt(CC, accounts);
    }

    public FakeEmailSenderSubject bcc(TestAccount... accounts) {
      return rcpt(BCC, accounts);
    }

    public FakeEmailSenderSubject notTo(TestAccount... accounts) {
      return rcpt(null, accounts);
    }

    private FakeEmailSenderSubject rcpt(@Nullable RecipientType type, TestAccount[] accounts) {
      for (TestAccount account : accounts) {
        rcpt(type, account);
      }
      return this;
    }

    private void rcpt(@Nullable RecipientType type, TestAccount account) {
      rcpt(type, account.email);
    }

    public FakeEmailSenderSubject to(NotifyType... watches) {
      return rcpt(TO, watches);
    }

    public FakeEmailSenderSubject cc(NotifyType... watches) {
      return rcpt(CC, watches);
    }

    public FakeEmailSenderSubject bcc(NotifyType... watches) {
      return rcpt(BCC, watches);
    }

    public FakeEmailSenderSubject notTo(NotifyType... watches) {
      return rcpt(null, watches);
    }

    private FakeEmailSenderSubject rcpt(@Nullable RecipientType type, NotifyType[] watches) {
      for (NotifyType watch : watches) {
        rcpt(type, watch);
      }
      return this;
    }

    private void rcpt(@Nullable RecipientType type, NotifyType watch) {
      if (!users.watchers.containsKey(watch)) {
        fail("configured to watch", watch);
      }
      rcpt(type, users.watchers.get(watch));
    }
  }

  protected class StagedUsers {
    public final TestAccount owner;
    public final TestAccount author;
    public final TestAccount uploader;
    public final TestAccount reviewer;
    public final TestAccount ccer;
    public final TestAccount starrer;
    public final TestAccount watchingProjectOwner;
    public final String reviewerByEmail = "reviewerByEmail@example.com";
    public final String ccerByEmail = "ccByEmail@example.com";
    private final Map<NotifyType, TestAccount> watchers = new HashMap<>();
    private final Map<String, TestAccount> accountsByEmail = new HashMap<>();
    boolean supportReviewersByEmail;

    StagedUsers(List<NotifyType> watches) throws Exception {
      owner = testAccount("owner");
      reviewer = testAccount("reviewer");
      author = testAccount("author");
      uploader = testAccount("uploader");
      ccer = testAccount("ccer");
      starrer = testAccount("starrer");

      watchingProjectOwner = testAccount("watchingProjectOwner", "Administrators");
      setApiUser(watchingProjectOwner);
      watch(project.get(), pwi -> pwi.notifyNewChanges = true);

      for (NotifyType watch : watches) {
        TestAccount watcher = testAccount(watch.toString());
        setApiUser(watcher);
        watch(
            project.get(),
            pwi -> {
              pwi.notifyAllComments = watch.equals(NotifyType.ALL_COMMENTS);
              pwi.notifyAbandonedChanges = watch.equals(NotifyType.ABANDONED_CHANGES);
              pwi.notifyNewChanges = watch.equals(NotifyType.NEW_CHANGES);
              pwi.notifyNewPatchSets = watch.equals(NotifyType.NEW_PATCHSETS);
              pwi.notifySubmittedChanges = watch.equals(NotifyType.SUBMITTED_CHANGES);
            });
        watchers.put(watch, watcher);
      }
    }

    private String email(String username) {
      // Email validator rejects usernames longer than 64 bytes.
      if (username.length() > 64) {
        username = username.substring(username.length() - 64);
        if (username.startsWith(".")) {
          username = username.substring(1);
        }
      }
      return username + "@example.com";
    }

    TestAccount testAccount(String name) throws Exception {
      String username = name(name);
      TestAccount account = accountCreator.create(username, email(username), name);
      accountsByEmail.put(account.email, account);
      return account;
    }

    TestAccount testAccount(String name, String groupName) throws Exception {
      String username = name(name);
      TestAccount account = accountCreator.create(username, email(username), name, groupName);
      accountsByEmail.put(account.email, account);
      return account;
    }

    String emailToName(String email) {
      if (accountsByEmail.containsKey(email)) {
        return accountsByEmail.get(email).fullName;
      }
      return email;
    }

    protected void addReviewers(PushOneCommit.Result r) throws Exception {
      AddReviewerInput in = new AddReviewerInput();
      in.reviewer = reviewer.email;
      gApi.changes().id(r.getChangeId()).addReviewer(in);

      in.reviewer = ccer.email;
      in.state = ReviewerState.CC;
      gApi.changes().id(r.getChangeId()).addReviewer(in);

      in.reviewer = reviewerByEmail;
      in.state = ReviewerState.REVIEWER;
      AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(in);
      if (result.reviewers == null || result.reviewers.isEmpty()) {
        supportReviewersByEmail = false;
      } else {
        supportReviewersByEmail = true;
        in.reviewer = ccerByEmail;
        in.state = ReviewerState.CC;
        gApi.changes().id(r.getChangeId()).addReviewer(in);
      }
    }
  }

  protected interface PushOptionGenerator {
    List<String> pushOptions(StagedUsers users);
  }

  protected class StagedPreChange extends StagedUsers {
    public final TestRepository<?> repo;
    protected final PushOneCommit.Result result;
    public final String changeId;

    StagedPreChange(String ref, List<NotifyType> watches) throws Exception {
      this(ref, null, watches);
    }

    StagedPreChange(
        String ref, @Nullable PushOptionGenerator pushOptionGenerator, List<NotifyType> watches)
        throws Exception {
      super(watches);
      List<String> pushOptions = null;
      if (pushOptionGenerator != null) {
        pushOptions = pushOptionGenerator.pushOptions(this);
      }
      if (pushOptions != null) {
        ref = ref + '%' + Joiner.on(',').join(pushOptions);
      }
      repo = cloneProject(project, owner);
      PushOneCommit push = pushFactory.create(db, owner.getIdent(), repo);
      result = push.to(ref);
      result.assertOkStatus();
      changeId = result.getChangeId();
    }
  }

  protected StagedPreChange stagePreChange(String ref, NotifyType... watches) throws Exception {
    return new StagedPreChange(ref, ImmutableList.copyOf(watches));
  }

  protected StagedPreChange stagePreChange(
      String ref, @Nullable PushOptionGenerator pushOptionGenerator, NotifyType... watches)
      throws Exception {
    return new StagedPreChange(ref, pushOptionGenerator, ImmutableList.copyOf(watches));
  }

  protected class StagedChange extends StagedPreChange {

    StagedChange(String ref, List<NotifyType> watches) throws Exception {
      super(ref, watches);

      setApiUser(starrer);
      gApi.accounts().self().starChange(result.getChangeId());

      setApiUser(owner);
      addReviewers(result);
      sender.clear();
    }
  }

  protected StagedChange stageReviewableChange(NotifyType... watches) throws Exception {
    return new StagedChange("refs/for/master", ImmutableList.copyOf(watches));
  }

  protected StagedChange stageWipChange(NotifyType... watches) throws Exception {
    return new StagedChange("refs/for/master%wip", ImmutableList.copyOf(watches));
  }

  protected StagedChange stageReviewableWipChange(NotifyType... watches) throws Exception {
    StagedChange sc = stageReviewableChange(watches);
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).setWorkInProgress();
    return sc;
  }

  protected StagedChange stageAbandonedReviewableChange(NotifyType... watches) throws Exception {
    StagedChange sc = stageReviewableChange(watches);
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }

  protected StagedChange stageAbandonedReviewableWipChange(NotifyType... watches) throws Exception {
    StagedChange sc = stageReviewableWipChange(watches);
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }

  protected StagedChange stageAbandonedWipChange(NotifyType... watches) throws Exception {
    StagedChange sc = stageWipChange(watches);
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }
}
