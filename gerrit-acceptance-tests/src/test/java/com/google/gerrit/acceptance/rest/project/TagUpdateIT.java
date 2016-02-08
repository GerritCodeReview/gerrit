package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.forcePush;
import static com.google.gerrit.acceptance.GitUtil.push;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class TagUpdateIT extends AbstractDaemonTest {

  @Test
  public void updateLightweight_noPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.Tag v1 = new PushOneCommit.Tag("v1");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(v1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  @Test
  public void updateAnnotated_noPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.AnnotatedTag a1 =
        new PushOneCommit.AnnotatedTag("a1", "a1", admin.getIdent());
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(a1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    updateAnnotatedTag(git, "a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    // actually this fails with non-fast-forward
    assertThat(refUpdate.getStatus()).isEqualTo(Status.REJECTED_OTHER_REASON);
  }

  /** "Push Annotated Tag" +force doesn't help. What is it good for? */
  @Test
  public void updateAnnotated_noPushPermission_withForcePushAnnotatedPermission()
      throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*", true);

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.AnnotatedTag a1 =
        new PushOneCommit.AnnotatedTag("a1", "a1", admin.getIdent());
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(a1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    updateAnnotatedTag(git, "a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void updateLightweight_withPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.Tag v1 = new PushOneCommit.Tag("v1");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(v1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void updateAnnotated_withPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.AnnotatedTag a1 =
        new PushOneCommit.AnnotatedTag("a1", "a1", admin.getIdent());
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(a1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    updateAnnotatedTag(git, "a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateLightweight() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", true);

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.Tag v1 = new PushOneCommit.Tag("v1");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(v1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    PushResult r = forcePush(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateAnnotated() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", true);

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.AnnotatedTag a1 =
        new PushOneCommit.AnnotatedTag("a1", "a1", admin.getIdent());
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(a1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    updateAnnotatedTag(git, "a1");
    PushResult r = forcePush(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateLightweight_noForcePushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.Tag v1 = new PushOneCommit.Tag("v1");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(v1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    PushResult r = forcePush(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void forceUpdateAnnotated_noForcePushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushOneCommit.AnnotatedTag a1 =
        new PushOneCommit.AnnotatedTag("a1", "a1", admin.getIdent());
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(a1);
    push1.to(git, "refs/for/master%submit");

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.to(git, "refs/for/master%submit");
    updateAnnotatedTag(git, "a1");
    PushResult r = forcePush(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  private void updateAnnotatedTag(Git git, String name) throws Exception {
    TagCommand tc = git.tag().setName(name);
    tc.setAnnotated(true)
        .setMessage(name)
        .setTagger(admin.getIdent())
        .setForceUpdate(true)
        .call();
  }
}
