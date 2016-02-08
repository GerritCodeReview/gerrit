package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.createAnnotatedTag;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.forcePush;
import static com.google.gerrit.acceptance.GitUtil.push;
import static com.google.gerrit.acceptance.GitUtil.updateAnnotatedTag;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class TagUpdateIT extends AbstractDaemonTest {

  private static final boolean FORCE = true;

  @Test
  public void updateLightweight_noPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject 1");
    push(git, "HEAD:refs/for/master%submit");
    push(git, "HEAD:refs/tags/v1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void updateAnnotated_noPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject 1");
    createAnnotatedTag(git, "a1", "a1", admin.getIdent());
    push(git, "HEAD:refs/for/master%submit");
    push(git, "refs/tags/a1:refs/tags/a1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    updateAnnotatedTag(git, "a1", admin.getIdent());
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    // actually this fails with non-fast-forward
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  /** "Push Annotated Tag" +force doesn't help. What is it good for? */
  @Test
  public void updateAnnotated_noPushPermission_withForcePushAnnotatedPermission()
      throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*", FORCE);

    createCommit(git, admin.getIdent(), "subject 1");
    createAnnotatedTag(git, "a1", "a1", admin.getIdent());
    push(git, "HEAD:refs/for/master%submit");
    push(git, "refs/tags/a1:refs/tags/a1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    updateAnnotatedTag(git, "a1", admin.getIdent());
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void updateLightweight_withPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject 1");
    push(git, "HEAD:refs/for/master%submit");
    push(git, "HEAD:refs/tags/v1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void updateAnnotated_withPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject 1");
    createAnnotatedTag(git, "a1", "a1", admin.getIdent());
    push(git, "HEAD:refs/for/master%submit");
    push(git, "refs/tags/a1:refs/tags/a1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    updateAnnotatedTag(git, "a1", admin.getIdent());
    push(git, "refs/tags/a1:refs/tags/a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateLightweight() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", FORCE);

    createCommit(git, admin.getIdent(), "subject 1");
    push(git, "HEAD:refs/for/master%submit");
    push(git, "HEAD:refs/tags/v1");

    createCommit(git, admin.getIdent(), "subject 2");
    push(git, "HEAD:refs/for/master%submit");

    PushResult r = forcePush(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateAnnotated() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", FORCE);

    createCommit(git, admin.getIdent(), "subject");
    createAnnotatedTag(git, "a1", "a1", admin.getIdent());
    push(git, "HEAD:refs/for/master%submit");
    push(git, "refs/tags/a1:refs/tags/a1");

    createCommit(git, admin.getIdent(), "subject");
    push(git, "HEAD:refs/for/master%submit");

    updateAnnotatedTag(git, "a1", admin.getIdent());
    PushResult r = forcePush(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateLightweight_noForcePushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    push(git, "HEAD:refs/for/master%submit");
    push(git, "HEAD:refs/tags/v1");

    createCommit(git, admin.getIdent(), "subject");
    push(git, "HEAD:refs/for/master%submit");

    PushResult r = forcePush(git, ":refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void forceUpdateAnnotated_noForcePushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    createAnnotatedTag(git, "a1", "a1", admin.getIdent());
    push(git, "HEAD:refs/for/master%submit");
    push(git, "refs/tags/a1:refs/tags/a1");

    createCommit(git, admin.getIdent(), "subject");
    push(git, "HEAD:refs/for/master%submit");

    updateAnnotatedTag(git, "a1", admin.getIdent());
    PushResult r = forcePush(git, ":refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }
}
