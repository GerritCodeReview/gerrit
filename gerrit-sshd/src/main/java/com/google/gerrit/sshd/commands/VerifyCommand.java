// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.VerifyInput;
import com.google.gerrit.extensions.common.VerificationInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.gerrit.sshd.commands.CommandUtils;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@CommandMetaData(name = "verify", description = "Verify one or more patch sets")
public class VerifyCommand extends SshCommand {
  private static final Logger log =
      LoggerFactory.getLogger(VerifyCommand.class);

  private final Set<PatchSet> patchSets = new HashSet<>();

  @Argument(index = 0, required = true, multiValued = true,
      metaVar = "{COMMIT | CHANGE,PATCHSET}",
      usage = "list of commits or patch sets to verify")
  void addPatchSetId(String token) {
    try {
      PatchSet ps = CommandUtils.parsePatchSet(token, db, projectControl,
          branch);
      patchSets.add(ps);
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Option(name = "--project", aliases = "-p",
      usage = "project containing the specified patch set(s)")
  private ProjectControl projectControl;

  @Option(name = "--branch", aliases = "-b",
      usage = "branch containing the specified patch set(s)")
  private String branch;

  @Option(name = "--verification", aliases = "-v",
      usage = "verification to set the result for", metaVar = "VERIFY=OUTCOME")
  void addJob(String token) {
    parseWithEquals(token);
  }

  private void parseWithEquals(String text) {
    log.debug("processing verification: " + text);
    checkArgument(!Strings.isNullOrEmpty(text), "Empty verification vote");
    Map<String, String> params = null;
    try {
      params = Splitter.on("|").withKeyValueSeparator("=").split(text);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.valueOf("Invalid verification parameters"));
    }

    String category = params.get("category");
    checkArgument(category != null, "Verification is missing a category");
    String value = params.get("value");
    checkArgument(value != null, "Verification is missing a value");
    VerificationInfo data = new VerificationInfo();
    data.value = Short.parseShort(value);
    data.url = params.get("url");
    data.verifier = params.get("verifier");
    data.comment = params.get("comment");
    jobResult.put(category, data);
  }

  @Inject
  private ReviewDb db;

  @Inject
  private Provider<GerritApi> gApi;

  private Map<String, VerificationInfo> jobResult = Maps.newHashMap();

  @Override
  protected void run() throws UnloggedFailure {
    boolean ok = true;
    for (PatchSet patchSet : patchSets) {
      try {
        verifyOne(patchSet);
      } catch (UnloggedFailure e) {
        ok = false;
        writeError("error: " + e.getMessage() + "\n");
      }
    }

    if (!ok) {
      throw new UnloggedFailure(1, "one or more verifications failed;"
          + " review output above");
    }
  }

  private void applyVerification(PatchSet patchSet, VerifyInput verify)
      throws RestApiException {
    gApi.get().changes()
        .id(patchSet.getId().getParentKey().get())
        .revision(patchSet.getRevision().get())
        .verify(verify);
  }

  private void verifyOne(PatchSet patchSet) throws UnloggedFailure {
    VerifyInput verify = new VerifyInput();
    verify.verifications = jobResult;
    try {
      applyVerification(patchSet, verify);
    } catch (RestApiException e) {
      throw CommandUtils.error(e.getMessage());
    }
  }

  private void writeError(String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

}
