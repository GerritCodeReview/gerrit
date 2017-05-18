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
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.EmailHeader;
import com.google.gerrit.server.mail.send.EmailHeader.AddressList;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

  protected static class FakeEmailSenderSubject
      extends Subject<FakeEmailSenderSubject, FakeEmailSender> {
    private final Iterator<Message> messages;

    FakeEmailSenderSubject(FailureStrategy failureStrategy, FakeEmailSender target) {
      super(failureStrategy, target);
      messages = target.getMessages().iterator();
    }

    public void notSent() {
      if (messages.hasNext()) {
        fail("a message wasn't sent");
      }
    }

    public NotificationsSubject sent(String messageType, StagedUsers users) {
      if (!messages.hasNext()) {
        fail("a message was sent");
      }
      Message msg = messages.next();
      if (!msg.headers().containsKey("X-Gerrit-MessageType")) {
        fail("a message was sent with X-Gerrit-MessageType header");
      }
      EmailHeader header = msg.headers().get("X-Gerrit-MessageType");
      if (!header.equals(new EmailHeader.String(messageType))) {
        fail( "message of type " + messageType + " was sent; X-Gerrit-MessageType is " + header);
      }
      return assertThat(new Notifications(msg, users));
    }
  }

  private static final SubjectFactory<NotificationsSubject, Notifications>
      NOTIFICATIONS_SUBJECT_FACTORY =
      new SubjectFactory<NotificationsSubject, Notifications>() {
        @Override
        public NotificationsSubject getSubject(
            FailureStrategy failureStrategy, Notifications target) {
          return new NotificationsSubject(failureStrategy, target);
        }
      };

  protected static class Notifications {
    final Map<RecipientType, List<String>> recipients = new HashMap<>();
    final StagedUsers users;

    Notifications(Message msg, StagedUsers users) {
      recipients.put(TO, parseAddresses(msg, "To"));
      recipients.put(CC, parseAddresses(msg, "CC"));
      recipients.put(
          BCC,
          msg.rcpt()
              .stream()
              .map(Address::getEmail)
              .filter(
                  e ->
                      !recipients.get(TO).contains(e)
                          && !recipients.get(CC).contains(e))
              .collect(Collectors.toList()));
      this.users = users;
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

    @Override
    public String toString() {
      List<String> parts = new ArrayList<>(3);
      for (RecipientType type :
          ImmutableList.of(TO, CC, BCC)) {
        parts.add(type.toString() + ": " + Joiner.on(", ").join(recipients.get(type)));
      }
      return Joiner.on("\n").join(parts);
    }
  }

  protected static NotificationsSubject assertThat(Notifications notifications) {
    return assertAbout(NOTIFICATIONS_SUBJECT_FACTORY).that(notifications);
  }

  protected static class NotificationsSubject extends Subject<NotificationsSubject, Notifications> {
    NotificationsSubject(FailureStrategy failureStrategy, Notifications target) {
      super(failureStrategy, target);
    }

    public NotificationsSubject to(String... emails) {
      return rcpt(actual().users.supportReviewersByEmail ? TO : null, emails);
    }

    public NotificationsSubject cc(String... emails) {
      return rcpt(actual().users.supportReviewersByEmail ? CC : null, emails);
    }

    public NotificationsSubject bcc(String... emails) {
      return rcpt(actual().users.supportReviewersByEmail ? BCC : null, emails);
    }

    private NotificationsSubject rcpt(@Nullable RecipientType type, String[] emails) {
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

    private void rcpt(
        @Nullable RecipientType type, String email, boolean expected) {
      if (actual().recipients.get(type).contains(email) != expected) {
        fail(expected ? "notifies" : "doesn't notify", type + ": " + email);
      }
    }

    public NotificationsSubject notTo(String... emails) {
      return rcpt(null, emails);
    }

    public NotificationsSubject to(TestAccount... accounts) {
      return rcpt(TO, accounts);
    }

    public NotificationsSubject cc(TestAccount... accounts) {
      return rcpt(CC, accounts);
    }

    public NotificationsSubject bcc(TestAccount... accounts) {
      return rcpt(BCC, accounts);
    }

    public NotificationsSubject notTo(TestAccount... accounts) {
      return rcpt(null, accounts);
    }

    private NotificationsSubject rcpt(@Nullable RecipientType type, TestAccount[] accounts) {
      for (TestAccount account : accounts) {
        rcpt(type, account);
      }
      return this;
    }

    private void rcpt(@Nullable RecipientType type, TestAccount account) {
      rcpt(type, account.email);
    }

    public NotificationsSubject to(NotifyType... watches) {
      return rcpt(TO, watches);
    }

    public NotificationsSubject cc(NotifyType... watches) {
      return rcpt(CC, watches);
    }

    public NotificationsSubject bcc(NotifyType... watches) {
      return rcpt(BCC, watches);
    }

    public void notTo(NotifyType... watches) {
      rcpt(null, watches);
    }

    private NotificationsSubject rcpt(@Nullable RecipientType type, NotifyType[] watches) {
      for (NotifyType watch : watches) {
        rcpt(type, watch);
      }
      return this;
    }

    private void rcpt(@Nullable RecipientType type, NotifyType watch) {
      if (!actual().users.watchers.containsKey(watch)) {
        fail("not configured to watch", watch);
      }
      rcpt(type, actual().users.watchers.get(watch));
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
              pwi.notifyAllComments = watches.contains(NotifyType.ALL_COMMENTS);
              pwi.notifyAbandonedChanges = watches.contains(NotifyType.ABANDONED_CHANGES);
              pwi.notifyNewChanges = watches.contains(NotifyType.NEW_CHANGES);
              pwi.notifyNewPatchSets = watches.contains(NotifyType.NEW_PATCHSETS);
              pwi.notifyReviewStartedChanges = watches.contains(NotifyType.REVIEW_STARTED_CHANGES);
              pwi.notifySubmittedChanges = watches.contains(NotifyType.SUBMITTED_CHANGES);
            });
        watchers.put(watch, watcher);
      }
    }

    TestAccount testAccount(String name) throws Exception {
      return accounts.create(name, name + "@example.com", name);
    }

    TestAccount testAccount(String name, String groupName) throws Exception {
      return accounts.create(name, name + "@example.com", name, groupName);
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

  protected class StagedPreChange extends StagedUsers {
    protected final PushOneCommit.Result result;
    public final String changeId;

    StagedPreChange(String ref, List<NotifyType> watches) throws Exception {
      super(watches);
      TestRepository<?> repo = cloneProject(project, owner);
      PushOneCommit push = pushFactory.create(db, owner.getIdent(), repo);
      result = push.to(ref);
      result.assertOkStatus();
      changeId = result.getChangeId();
    }
  }

  protected StagedPreChange stagePreChange(String ref, NotifyType... watches) throws Exception {
    return new StagedPreChange(ref, ImmutableList.copyOf(watches));
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
