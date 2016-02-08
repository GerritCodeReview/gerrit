package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.createAnnotatedTag;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.pushTag;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class TagCreationIT extends AbstractDaemonTest {

  @Test
  public void lightweightTagOnNewCommit_noPushPermission_rejected()
      throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");

    commit(admin.getIdent(), "subject");
    PushResult r = pushHead(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void annotatedTagOnNewCommit_noPushPermission_rejected()
      throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");

    commit(admin.getIdent(), "subject");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = pushTag(testRepo, "a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    System.out.println(refUpdate.getMessage());
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void lightweightTagOnNewCommit_withPushPermission_ok() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject");
    PushResult r = pushHead(testRepo, "refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void annotatedTagOnNewCommit_withPushPermission_ok() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    commit(admin.getIdent(), "subject");
    createAnnotatedTag(testRepo, "a1", admin.getIdent());
    PushResult r = pushTag(testRepo, "a1");
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