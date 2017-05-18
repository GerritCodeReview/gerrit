package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertAbout;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractNotificationTest extends AbstractDaemonTest {
  private static final SubjectFactory<NotificationsSubject, Notifications>
      NOTIFICATIONS_SUBJECT_FACTORY =
          new SubjectFactory<NotificationsSubject, Notifications>() {
            @Override
            public NotificationsSubject getSubject(
                FailureStrategy failureStrategy, Notifications target) {
              return new NotificationsSubject(failureStrategy, target);
            }
          };

  /*
  private static SubjectFactory<NotificationsSubject, Notifications> notifications() {
    return NOTIFICATIONS_SUBJECT_FACTORY;
  }
  */

  protected static class Notifications {
    final Map<RecipientType, List<String>> recipients = new HashMap<>();
    final Participants participants;

    Notifications(FakeEmailSender sender, Participants participants) {
      Truth.assertThat(sender.getMessages()).hasSize(1);
      Message msg = sender.getMessages().get(0);
      recipients.put(RecipientType.TO, parseAddresses(msg, "To"));
      recipients.put(RecipientType.CC, parseAddresses(msg, "CC"));
      recipients.put(
          RecipientType.BCC,
          msg.rcpt()
              .stream()
              .map(Address::getEmail)
              .filter(
                  e ->
                      !recipients.get(RecipientType.TO).contains(e)
                          && !recipients.get(RecipientType.CC).contains(e))
              .collect(Collectors.toList()));
      this.participants = participants;
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
          ImmutableList.of(RecipientType.TO, RecipientType.CC, RecipientType.BCC)) {
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

    public NotificationsSubject receives(TestAccount account, @Nullable RecipientType type) {
      return receives(account.email, type);
    }

    public NotificationsSubject receives(String addr, @Nullable RecipientType type) {
      return receivesOn(addr, RecipientType.TO, RecipientType.TO.equals(type))
          .receivesOn(addr, RecipientType.CC, RecipientType.CC.equals(type))
          .receivesOn(addr, RecipientType.BCC, RecipientType.BCC.equals(type));
    }

    private NotificationsSubject receivesOn(String addr, RecipientType type, boolean expected) {
      if (actual().recipients.get(type).contains(addr) != expected) {
        fail(expected ? "notifies" : "doesn't notify", type + ": " + addr);
      }
      return this;
    }

    public NotificationsSubject owner(@Nullable RecipientType type) {
      return receives(actual().participants.owner, type);
    }

    public NotificationsSubject reviewers(@Nullable RecipientType type) {
      return receives(actual().participants.reviewer, type);
    }

    public NotificationsSubject ccers(@Nullable RecipientType type) {
      return receives(actual().participants.ccer, type);
    }

    public NotificationsSubject starrers(@Nullable RecipientType type) {
      return receives(actual().participants.starrer, type);
    }

    public NotificationsSubject watcher(NotifyType watch, @Nullable RecipientType type) {
      if (!actual().participants.watchers.containsKey(watch)) {
        fail("not configured to watch", watch);
      }
      return receives(actual().participants.watchers.get(watch), type);
    }

    public NotificationsSubject reviewersByEmail(@Nullable RecipientType type) {
      if (!actual().participants.supportReviewersByEmail) {
        return this;
      }
      return receives(actual().participants.reviewerByEmail, type);
    }

    public NotificationsSubject ccersByEmail(@Nullable RecipientType type) {
      if (!actual().participants.supportReviewersByEmail) {
        return this;
      }
      return receives(actual().participants.ccByEmail, type);
    }
  }

  private class Participants {
    final TestAccount owner;
    final TestAccount author;
    final TestAccount uploader;
    final TestAccount reviewer;
    final TestAccount ccer;
    final TestAccount starrer;
    final String reviewerByEmail = "reviewerByEmail@example.com";
    final String ccByEmail = "ccByEmail@example.com";
    final Map<NotifyType, TestAccount> watchers = new HashMap<>();
    boolean supportReviewersByEmail;

    Participants(List<NotifyType> watches) throws Exception {
      owner = testAccount("owner");
      reviewer = testAccount("reviewer");
      author = testAccount("author");
      uploader = testAccount("uploader");
      ccer = testAccount("ccer");
      starrer = testAccount("starrer");
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

    PushOneCommit.Result stageChange(String ref) throws Exception {
      setApiUser(admin);
      ConfigInput conf = new ConfigInput();
      conf.enableReviewerByEmail = InheritableBoolean.TRUE;
      gApi.projects().name(project.get()).config(conf);

      setApiUser(owner);
      PushOneCommit push = pushFactory.create(db, owner.getIdent(), testRepo);
      PushOneCommit.Result result = push.to(ref);
      result.assertOkStatus();
      addReviewers(result);

      setApiUser(starrer);
      gApi.accounts().self().starChange(result.getChangeId());

      return result;
    }

    private void addReviewers(PushOneCommit.Result r) throws Exception {
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
        in.reviewer = ccByEmail;
        in.state = ReviewerState.CC;
        gApi.changes().id(r.getChangeId()).addReviewer(in);
      }
    }
  }

  protected interface ChangeInteraction {
    void apply(String changeId) throws Exception;
  }

  protected Notifications notificationsForReviewableChange(
      ChangeInteraction interaction, NotifyType... watches) throws Exception {
    Participants participants = new Participants(ImmutableList.copyOf(watches));
    String changeId = participants.stageChange("refs/for/master").getChangeId();
    sender.clear();
    setApiUser(participants.owner);
    interaction.apply(changeId);
    try {
      return new Notifications(sender, participants);
    } finally {
      sender.clear();
    }
  }
}
