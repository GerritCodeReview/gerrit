package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.createAnnotatedTag;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.forcePushTag;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.pushTag;
import static com.google.gerrit.acceptance.GitUtil.updateAnnotatedTag;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class TagUpdateIT extends AbstractDaemonTest {

  private static final boolean FORCE = true;

  @Test
  public void fastForwardLightweight_noPushPermission_rejected()
      throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    pushHead(testRepo, "refs/for/master%submit");
    pushHead(testRepo, "refs/tags/v1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    PushResult r = pushHead(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void fastForwardAnnotated_noPushPermission_rejected() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    updateAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = pushTag(testRepo, "a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    // this is rejected with REJECTED_NONFASTFORWARD
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void fastForwardLightweight_withPushPermission_ok() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    pushHead(testRepo, "refs/for/master%submit");
    pushHead(testRepo, "refs/tags/v1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    PushResult r = pushHead(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void fastForwardAnnotated_withPushPermission_rejected() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    updateAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = pushTag(testRepo, "a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    // this gets rejected with REJECTED_NONFASTFORWARD
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void fastForwardAnnotated_withForcePushPermission_ok() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", FORCE);

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    updateAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = forcePushTag(testRepo, "a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  /** "Push Annotated Tag" +force has the same effect as Push+force */
  @Test
  public void fastForwardAnnotated_noPushPermission_withForcePushAnnotatedPermission_ok()
      throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*", FORCE);

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    commit(admin.getIdent(), "subject 2");
    pushTag(testRepo, "a1");

    updateAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = pushTag(testRepo, "a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    // this is rejected with REJECTED_NONFASTFORWARD
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateLightweight_noForcePushPermission_rejected() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    pushHead(testRepo, "refs/for/master%submit");
    pushHead(testRepo, "refs/tags/v1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    PushResult r = deleteRef(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void forceUpdateAnnotated_noForcePushPermission_rejected() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    PushResult r = deleteRef(testRepo, "refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void forceUpdateLightweight_withForcePushPermission_ok() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", FORCE);

    commit(admin.getIdent(), "subject 1");
    pushHead(testRepo, "refs/for/master%submit");
    pushHead(testRepo, "refs/tags/v1");

    commit(admin.getIdent(), "subject 2");
    pushHead(testRepo, "refs/for/master%submit");

    PushResult r = deleteRef(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateAnnotated_withForcePushPermission_ok() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", FORCE);

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    PushResult r = deleteRef(testRepo, "refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  /** "Push Annotated Tag" +force has the same effect as Push+force */
  @Test
  public void forceUpdateAnnotated_noPushPermission_withForcePushAnnotatedPermission_ok()
      throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*", FORCE);

    commit(admin.getIdent(), "subject 1");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    pushHead(testRepo, "refs/for/master%submit");
    pushTag(testRepo, "a1");

    PushResult r = deleteRef(testRepo, "refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder()
        .ident(ident)
        .message(subject)
        .create();
  }
}
