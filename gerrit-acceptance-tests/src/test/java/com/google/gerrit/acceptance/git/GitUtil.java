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

package com.google.gerrit.acceptance.git;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TempFileUtil;
import com.google.gerrit.acceptance.TestAccount;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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

  public static void createProject(SshSession s, String name)
      throws JSchException, IOException {
    s.exec("gerrit create-project --empty-commit --name \"" + name + "\"");
  }

  public static Git cloneProject(String url) throws GitAPIException {
    final File gitDir = TempFileUtil.createTempDirectory();
    final CloneCommand cloneCmd = Git.cloneRepository();
    cloneCmd.setURI(url);
    cloneCmd.setDirectory(gitDir);
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

  public static String createCommit(Git git, PersonIdent i, String msg)
      throws GitAPIException, IOException {
    return createCommit(git, i, msg, true, false);
  }

  public static void amendCommit(Git git, PersonIdent i, String msg, String changeId)
      throws GitAPIException, IOException {
    msg = ChangeIdUtil.insertId(msg, ObjectId.fromString(changeId.substring(1)));
    createCommit(git, i, msg, false, true);
  }

  private static String createCommit(Git git, PersonIdent i, String msg,
      boolean insertChangeId, boolean amend) throws GitAPIException, IOException {
    ObjectId changeId = null;
    if (insertChangeId) {
      changeId = computeChangeId(git, i, msg);
      msg = ChangeIdUtil.insertId(msg, changeId);
    }

    final CommitCommand commitCmd = git.commit();
    commitCmd.setAmend(amend);
    commitCmd.setAuthor(i);
    commitCmd.setCommitter(i);
    commitCmd.setMessage(msg);
    commitCmd.call();

    return changeId != null ? "I" + changeId.getName() : null;
  }

  private static ObjectId computeChangeId(Git git, PersonIdent i, String msg)
      throws IOException {
    RevWalk rw = new RevWalk(git.getRepository());
    try {
      RevCommit parent =
          rw.lookupCommit(git.getRepository().getRef(Constants.HEAD).getObjectId());
      return ChangeIdUtil.computeChangeId(parent.getTree(), parent.getId(), i, i, msg);
    } finally {
      rw.release();
    }
  }

  public static PushResult pushHead(Git git, String ref) throws GitAPIException {
    PushCommand pushCmd = git.push();
    pushCmd.setRefSpecs(new RefSpec("HEAD:" + ref));
    Iterable<PushResult> r = pushCmd.call();
    return Iterables.getOnlyElement(r);
  }
}
