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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.testutil.TempFileUtil;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.eclipse.jgit.util.FS;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class GitUtil {

  public static void initSsh(final TestAccount a) {
    final Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    JSch.setConfig(config);

    // register a JschConfigSessionFactory that adds the private key as identity
    // to the JSch instance of JGit so that SSH communication via JGit can
    // succeed
    SshSessionFactory.setInstance(new JschConfigSessionFactory() {
      @Override
      protected void configure(Host hc, Session session) {
        try {
          final JSch jsch = getJSch(hc, FS.DETECTED);
          jsch.addIdentity("KeyPair", a.privateKey(),
              a.sshKey.getPublicKeyBlob(), null);
        } catch (JSchException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public static Git cloneProject(String url) throws GitAPIException, IOException {
    return cloneProject(url, true);
  }

  public static Git cloneProject(String url, boolean checkout) throws GitAPIException, IOException {
    final File gitDir = TempFileUtil.createTempDirectory();
    final CloneCommand cloneCmd = Git.cloneRepository();
    cloneCmd.setURI(url);
    cloneCmd.setDirectory(gitDir);
    cloneCmd.setNoCheckout(!checkout);
    return cloneCmd.call();
  }

  public static void add(Git git, String path, String content)
      throws GitAPIException, IOException {
    File f = new File(git.getRepository().getDirectory().getParentFile(), path);
    File p = f.getParentFile();
    if (!p.exists() && !p.mkdirs()) {
      throw new IOException("failed to create dir: " + p.getAbsolutePath());
    }
    FileWriter w = new FileWriter(f);
    BufferedWriter out = new BufferedWriter(w);
    try {
      out.write(content);
    } finally {
      out.close();
    }

    final AddCommand addCmd = git.add();
    addCmd.addFilepattern(path);
    addCmd.call();
  }

  public static void rm(Git gApi, String path)
      throws GitAPIException {
    gApi.rm()
        .addFilepattern(path)
        .call();
  }

  public static Commit createCommit(Git git, PersonIdent i, String msg)
      throws GitAPIException {
    return createCommit(git, i, msg, null);
  }

  public static Commit amendCommit(Git git, PersonIdent i, String msg, String changeId)
      throws GitAPIException {
    msg = ChangeIdUtil.insertId(msg, ObjectId.fromString(changeId.substring(1)));
    return createCommit(git, i, msg, changeId);
  }

  private static Commit createCommit(Git git, PersonIdent i, String msg,
      String changeId) throws GitAPIException {

    final CommitCommand commitCmd = git.commit();
    commitCmd.setAmend(changeId != null);
    commitCmd.setAuthor(i);
    commitCmd.setCommitter(i);
    commitCmd.setMessage(msg);
    commitCmd.setInsertChangeId(changeId == null);

    RevCommit c = commitCmd.call();

    List<String> ids = c.getFooterLines(FooterConstants.CHANGE_ID);
    checkState(ids.size() >= 1,
        "No Change-Id found in new commit:\n%s", c.getFullMessage());
    changeId = ids.get(ids.size() - 1);

    return new Commit(c, changeId);
  }

  public static void fetch(Git git, String spec) throws GitAPIException {
    FetchCommand fetch = git.fetch();
    fetch.setRefSpecs(new RefSpec(spec));
    fetch.call();
  }

  public static void checkout(Git git, String name) throws GitAPIException {
    CheckoutCommand checkout = git.checkout();
    checkout.setName(name);
    checkout.call();
  }

  public static PushResult pushHead(Git git, String ref, boolean pushTags)
      throws GitAPIException {
    return pushHead(git, ref, pushTags, false);
  }

  public static PushResult pushHead(Git git, String ref, boolean pushTags,
      boolean force) throws GitAPIException {
    PushCommand pushCmd = git.push();
    pushCmd.setForce(force);
    pushCmd.setRefSpecs(new RefSpec("HEAD:" + ref));
    if (pushTags) {
      pushCmd.setPushTags();
    }
    Iterable<PushResult> r = pushCmd.call();
    return Iterables.getOnlyElement(r);
  }

  public static class Commit {
    private final RevCommit commit;
    private final String changeId;

    Commit(RevCommit commit, String changeId) {
      this.commit = commit;
      this.changeId = changeId;
    }

    public RevCommit getCommit() {
      return commit;
    }

    public String getChangeId() {
      return changeId;
    }
  }
}
