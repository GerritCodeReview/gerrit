package com.google.gerrit.acceptance.server.mail;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import org.junit.Before;
import org.junit.Test;

import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;

public class CreateChangeSenderIT extends AbstractNotificationTest {
  @Test
  public void createReviewableChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master", NEW_CHANGES, NEW_PATCHSETS);
    assertThat(sender).sent("newchange", spc)
        .notTo(spc.owner)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS);
  }

  @Test
  public void createWipChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master%wip", NEW_CHANGES, NEW_PATCHSETS);
    assertThat(sender).sent("newchange", spc)
        .notTo(spc.owner)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS);
  }

  @Test
  public void createReviewableChangeWithNotifyOwnerReviewers() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER_REVIEWERS", NEW_CHANGES, NEW_PATCHSETS);
    assertThat(sender).notSent();
  }
}
