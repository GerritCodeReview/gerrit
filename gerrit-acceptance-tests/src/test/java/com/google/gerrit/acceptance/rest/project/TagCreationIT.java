package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.push;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

public class TagCreationIT extends AbstractDaemonTest {

  @Test
  public void lightweightTagOnNewCommit_noPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void annotatedTagOnNewCommit_noPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    createAnnotatedTag(git, "a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void lightweightTagOnNewCommit_withPushPermission() throws Exception {
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    PushResult r = push(git, "HEAD:refs/tags/v1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/v1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void annotatedTagOnNewCommit_withPushPermission() throws Exception {
    grant(Permission.PUSH_TAG, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    createCommit(git, admin.getIdent(), "subject");
    createAnnotatedTag(git, "a1");
    PushResult r = push(git, "refs/tags/a1:refs/tags/a1");
    RemoteRefUpdate refUpdate = r.getRemoteUpdate("refs/tags/a1");
    assertThat(refUpdate.getStatus()).isEqualTo(Status.OK);
  }

  private void createAnnotatedTag(Git git, String name) throws Exception {
    TagCommand tc = git.tag().setName(name);
    tc.setAnnotated(true).setMessage(name).setTagger(admin.getIdent()).call();
  }
}