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
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.After;
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
    setEmailStrategy(account, strategy, true);
  }

  protected void setEmailStrategy(TestAccount account, EmailStrategy strategy, boolean record)
      throws Exception {
    if (record) {
      accountsModifyingEmailStrategy.add(account);
    }
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
    private Set<String> accountedFor = new HashSet<>();

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
      return named(recipientMapToString(recipients, e -> users.emailToName(e)));
    }

    private static String recipientMapToString(
        Map<RecipientType, List<String>> recipients, Function<String, String> emailToName) {
      StringBuilder buf = new StringBuilder();
      buf.append('[');
      for (RecipientType type : ImmutableList.of(TO, CC, BCC)) {
        buf.append('\n');
        buf.append(type);
        buf.append(':');
        String delim = " ";
        for (String r : recipients.get(type)) {
          buf.append(delim);
          buf.append(emailToName.apply(r));
          delim = ", ";
        }
      }
      buf.append("\n]");
      return buf.toString();
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
      if (expected) {
        accountedFor.add(email);
      }
    }

    public FakeEmailSenderSubject noOneElse() {
      for (Map.Entry<NotifyType, TestAccount> watchEntry : users.watchers.entrySet()) {
        if (!accountedFor.contains(watchEntry.getValue().email)) {
          notTo(watchEntry.getKey());
        }
      }

      Map<RecipientType, List<String>> unaccountedFor = new HashMap<>();
      boolean ok = true;
      for (Map.Entry<RecipientType, List<String>> entry : recipients.entrySet()) {
        unaccountedFor.put(entry.getKey(), new ArrayList<>());
        for (String address : entry.getValue()) {
          if (!accountedFor.contains(address)) {
            unaccountedFor.get(entry.getKey()).add(address);
            ok = false;
          }
        }
      }
      if (!ok) {
        fail(
            "was fully tested, missing assertions for: "
                + recipientMapToString(unaccountedFor, e -> users.emailToName(e)));
      }
      return this;
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

  private static final Map<String, StagedUsers> stagedUsers = new HashMap<>();

  // TestAccount doesn't implement hashCode/equals, so this set is according
  // to object identity. That's fine for our purposes.
  private Set<TestAccount> accountsModifyingEmailStrategy = new HashSet<>();

  @After
  public void resetEmailStrategies() throws Exception {
    for (TestAccount account : accountsModifyingEmailStrategy) {
      setEmailStrategy(account, EmailStrategy.ENABLED, false);
    }
    accountsModifyingEmailStrategy.clear();
  }

  protected class StagedUsers {
    public final TestAccount owner;
    public final TestAccount author;
    public final TestAccount uploader;
    public final TestAccount reviewer;
    public final TestAccount ccer;
    public final TestAccount starrer;
    public final TestAccount assignee;
    public final TestAccount watchingProjectOwner;
    public final String reviewerByEmail = "reviewerByEmail@example.com";
    public final String ccerByEmail = "ccByEmail@example.com";
    private final Map<NotifyType, TestAccount> watchers = new HashMap<>();
    private final Map<String, TestAccount> accountsByEmail = new HashMap<>();
    boolean supportReviewersByEmail;

    private String usersCacheKey() {
      return description.getClassName();
    }

    private TestAccount evictAndCopy(TestAccount account) throws IOException {
      accountCache.evict(account.id);
      return account;
    }

    public StagedUsers() throws Exception {
      synchronized (stagedUsers) {
        if (stagedUsers.containsKey(usersCacheKey())) {
          StagedUsers existing = stagedUsers.get(usersCacheKey());
          owner = evictAndCopy(existing.owner);
          author = evictAndCopy(existing.author);
          uploader = evictAndCopy(existing.uploader);
          reviewer = evictAndCopy(existing.reviewer);
          ccer = evictAndCopy(existing.ccer);
          starrer = evictAndCopy(existing.starrer);
          assignee = evictAndCopy(existing.assignee);
          watchingProjectOwner = evictAndCopy(existing.watchingProjectOwner);
          watchers.putAll(existing.watchers);
          return;
        }

        owner = testAccount("owner");
        reviewer = testAccount("reviewer");
        author = testAccount("author");
        uploader = testAccount("uploader");
        ccer = testAccount("ccer");
        starrer = testAccount("starrer");
        assignee = testAccount("assignee");

        watchingProjectOwner = testAccount("watchingProjectOwner", "Administrators");
        setApiUser(watchingProjectOwner);
        watch(allProjects.get(), pwi -> pwi.notifyNewChanges = true);

        for (NotifyType watch : NotifyType.values()) {
          if (watch == NotifyType.ALL) {
            continue;
          }
          TestAccount watcher = testAccount(watch.toString());
          setApiUser(watcher);
          watch(
              allProjects.get(),
              pwi -> {
                pwi.notifyAllComments = watch.equals(NotifyType.ALL_COMMENTS);
                pwi.notifyAbandonedChanges = watch.equals(NotifyType.ABANDONED_CHANGES);
                pwi.notifyNewChanges = watch.equals(NotifyType.NEW_CHANGES);
                pwi.notifyNewPatchSets = watch.equals(NotifyType.NEW_PATCHSETS);
                pwi.notifySubmittedChanges = watch.equals(NotifyType.SUBMITTED_CHANGES);
              });
          watchers.put(watch, watcher);
        }

        stagedUsers.put(usersCacheKey(), this);
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

    public TestAccount testAccount(String name) throws Exception {
      String username = name(name);
      TestAccount account = accounts.create(username, email(username), name);
      accountsByEmail.put(account.email, account);
      return account;
    }

    public TestAccount testAccount(String name, String groupName) throws Exception {
      String username = name(name);
      TestAccount account = accounts.create(username, email(username), name, groupName);
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
      ReviewInput in =
          ReviewInput.noScore()
              .reviewer(reviewer.email)
              .reviewer(reviewerByEmail)
              .reviewer(ccer.email, ReviewerState.CC, false)
              .reviewer(ccerByEmail, ReviewerState.CC, false);
      ReviewResult result = gApi.changes().id(r.getChangeId()).revision("current").review(in);
      supportReviewersByEmail = true;
      if (result.reviewers.values().stream().anyMatch(v -> v.error != null)) {
        supportReviewersByEmail = false;
        in =
            ReviewInput.noScore()
                .reviewer(reviewer.email)
                .reviewer(ccer.email, ReviewerState.CC, false);
        result = gApi.changes().id(r.getChangeId()).revision("current").review(in);
      }
      Truth.assertThat(result.reviewers.values().stream().allMatch(v -> v.error == null)).isTrue();
    }
  }

  protected interface PushOptionGenerator {
    List<String> pushOptions(StagedUsers users);
  }

  protected class StagedPreChange extends StagedUsers {
    public final TestRepository<?> repo;
    protected final PushOneCommit.Result result;
    public final String changeId;

    StagedPreChange(String ref) throws Exception {
      this(ref, null);
    }

    StagedPreChange(String ref, @Nullable PushOptionGenerator pushOptionGenerator)
        throws Exception {
      super();
      List<String> pushOptions = null;
      if (pushOptionGenerator != null) {
        pushOptions = pushOptionGenerator.pushOptions(this);
      }
      if (pushOptions != null) {
        ref = ref + '%' + Joiner.on(',').join(pushOptions);
      }
      setApiUser(owner);
      repo = cloneProject(project, owner);
      PushOneCommit push = pushFactory.create(db, owner.getIdent(), repo);
      result = push.to(ref);
      result.assertOkStatus();
      changeId = result.getChangeId();
    }
  }

  protected StagedPreChange stagePreChange(String ref) throws Exception {
    return new StagedPreChange(ref);
  }

  protected StagedPreChange stagePreChange(
      String ref, @Nullable PushOptionGenerator pushOptionGenerator) throws Exception {
    return new StagedPreChange(ref, pushOptionGenerator);
  }

  protected class StagedChange extends StagedPreChange {
    StagedChange(String ref) throws Exception {
      super(ref);

      setApiUser(starrer);
      gApi.accounts().self().starChange(result.getChangeId());

      setApiUser(owner);
      addReviewers(result);
      sender.clear();
    }
  }

  protected StagedChange stageReviewableChange() throws Exception {
    return new StagedChange("refs/for/master");
  }

  protected StagedChange stageWipChange() throws Exception {
    return new StagedChange("refs/for/master%wip");
  }

  protected StagedChange stageReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).setWorkInProgress();
    return sc;
  }

  protected StagedChange stageAbandonedReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }

  protected StagedChange stageAbandonedReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }

  protected StagedChange stageAbandonedWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    setApiUser(sc.owner);
    gApi.changes().id(sc.changeId).abandon();
    sender.clear();
    return sc;
  }
}
