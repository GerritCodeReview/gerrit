// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.SshClientImplementation;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;

public class GitUtil {
  private static final AtomicInteger testRepoCount = new AtomicInteger();
  private static final int TEST_REPO_WINDOW_DAYS = 2;

  public static void initSsh(KeyPair keyPair) {
    SshClientImplementation client = SshClientImplementation.getFromEnvironment();
    switch (client) {
      case JSCH:
        initJschClient(keyPair);
        break;
      case APACHE:
        initMinaClient();
        break;
      default:
        throw new IllegalArgumentException("Unknown client type: " + client);
    }
  }

  private static void initJschClient(KeyPair keyPair) {
    Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    JSch.setConfig(config);

    // register a JschConfigSessionFactory that adds the private key as identity
    // to the JSch instance of JGit so that SSH communication via JGit can
    // succeed
    SshSessionFactory.setInstance(
        new JschConfigSessionFactory() {
          @Override
          protected void configure(Host hc, Session session) {
            try {
              JSch jsch = getJSch(hc, FS.DETECTED);
              jsch.addIdentity(
                  "KeyPair", TestSshKeys.privateKey(keyPair), keyPair.getPublicKeyBlob(), null);
            } catch (JSchException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  private static void initMinaClient() {
    JGitKeyCache keyCache = new JGitKeyCache();
    SshdSessionFactory factory = new SshdSessionFactory(keyCache, new DefaultProxyDataFactory());
    SshSessionFactory.setInstance(factory);
  }

  /**
   * Create a new {@link TestRepository} with a distinct commit clock.
   *
   * <p>It is very easy for tests to create commits with identical subjects and trees; if such
   * commits also have identical authors/committers, then the computed Change-Id is identical as
   * well. Tests may generally assume that Change-Ids are unique, so to ensure this, we provision
   * TestRepository instances with non-overlapping commit clock times.
   *
   * <p>Space test repos 1 day apart, which allows for about 86k ticks per repo before overlapping,
   * and about 8k instances per process before hitting JGit's year 2038 limit.
   *
   * @param repo repository to wrap.
   * @return wrapped test repository with distinct commit time space.
   */
  public static <R extends Repository> TestRepository<R> newTestRepository(R repo)
      throws IOException {
    TestRepository<R> tr = new TestRepository<>(repo);
    tr.tick(
        Ints.checkedCast(
            TimeUnit.SECONDS.convert(
                testRepoCount.getAndIncrement() * TEST_REPO_WINDOW_DAYS, TimeUnit.DAYS)));
    return tr;
  }

  public static TestRepository<InMemoryRepository> cloneProject(Project.NameKey project, String uri)
      throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("clone of " + project.get());

    InMemoryRepository.Builder b = new InMemoryRepository.Builder().setRepositoryDescription(desc);
    if (uri.startsWith("ssh://")) {
      // SshTransport depends on a real FS to read ~/.ssh/config, but InMemoryRepository by default
      // uses a null FS.
      // Avoid leaking user state into our tests.
      b.setFS(FS.detect().setUserHome(null));
    }
    InMemoryRepository dest = b.build();
    Config cfg = dest.getConfig();
    cfg.setString("remote", "origin", "url", uri);
    cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
    TestRepository<InMemoryRepository> testRepo = newTestRepository(dest);
    FetchResult result = testRepo.git().fetch().setRemote("origin").call();
    String originMaster = "refs/remotes/origin/master";
    if (result.getTrackingRefUpdate(originMaster) != null) {
      testRepo.reset(originMaster);
    }
    return testRepo;
  }

  public static Ref createAnnotatedTag(TestRepository<?> testRepo, String name, PersonIdent tagger)
      throws GitAPIException {
    TagCommand cmd =
        testRepo.git().tag().setName(name).setAnnotated(true).setMessage(name).setTagger(tagger);
    return cmd.call();
  }

  public static Ref updateAnnotatedTag(TestRepository<?> testRepo, String name, PersonIdent tagger)
      throws GitAPIException {
    TagCommand tc = testRepo.git().tag().setName(name);
    return tc.setAnnotated(true).setMessage(name).setTagger(tagger).setForceUpdate(true).call();
  }

  public static void fetch(TestRepository<?> testRepo, String spec) throws GitAPIException {
    FetchCommand fetch = testRepo.git().fetch();
    fetch.setRefSpecs(new RefSpec(spec));
    fetch.call();
  }

  public static PushResult pushHead(TestRepository<?> testRepo, String ref) throws GitAPIException {
    return pushHead(testRepo, ref, false);
  }

  public static PushResult pushHead(TestRepository<?> testRepo, String ref, boolean pushTags)
      throws GitAPIException {
    return pushHead(testRepo, ref, pushTags, false);
  }

  public static PushResult pushHead(
      TestRepository<?> testRepo, String ref, boolean pushTags, boolean force)
      throws GitAPIException {
    return pushOne(testRepo, "HEAD", ref, pushTags, force, null);
  }

  public static PushResult pushHead(
      TestRepository<?> testRepo,
      String ref,
      boolean pushTags,
      boolean force,
      List<String> pushOptions)
      throws GitAPIException {
    return pushOne(testRepo, "HEAD", ref, pushTags, force, pushOptions);
  }

  public static PushResult deleteRef(TestRepository<?> testRepo, String ref)
      throws GitAPIException {
    return pushOne(testRepo, "", ref, false, true, null);
  }

  public static PushResult pushOne(
      TestRepository<?> testRepo,
      String source,
      String target,
      boolean pushTags,
      boolean force,
      List<String> pushOptions)
      throws GitAPIException {
    PushCommand pushCmd = testRepo.git().push();
    pushCmd.setForce(force);
    pushCmd.setPushOptions(pushOptions);
    pushCmd.setRefSpecs(new RefSpec((source != null ? source : "") + ":" + target));
    if (pushTags) {
      pushCmd.setPushTags();
    }
    Iterable<PushResult> r = pushCmd.call();
    return Iterables.getOnlyElement(r);
  }

  public static void assertPushOk(PushResult result, String ref) {
    RemoteRefUpdate rru = result.getRemoteUpdate(ref);
    assertWithMessage(rru.toString()).that(rru.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
  }

  public static void assertPushRejected(PushResult result, String ref, String expectedMessage) {
    RemoteRefUpdate rru = result.getRemoteUpdate(ref);
    assertWithMessage(rru.toString())
        .that(rru.getStatus())
        .isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(rru.getMessage()).isEqualTo(expectedMessage);
  }

  public static PushResult pushTag(TestRepository<?> testRepo, String tag) throws GitAPIException {
    return pushTag(testRepo, tag, false);
  }

  public static PushResult pushTag(TestRepository<?> testRepo, String tag, boolean force)
      throws GitAPIException {
    PushCommand pushCmd = testRepo.git().push();
    pushCmd.setForce(force);
    pushCmd.setRefSpecs(new RefSpec("refs/tags/" + tag + ":refs/tags/" + tag));
    Iterable<PushResult> r = pushCmd.call();
    return Iterables.getOnlyElement(r);
  }

  public static Optional<String> getChangeId(TestRepository<?> tr, ObjectId id) throws IOException {
    RevCommit c = tr.getRevWalk().parseCommit(id);
    tr.getRevWalk().parseBody(c);
    return Lists.reverse(c.getFooterLines(FooterConstants.CHANGE_ID)).stream().findFirst();
  }
}
