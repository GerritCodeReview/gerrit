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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.VerifyInput;
import com.google.gerrit.extensions.common.VerificationInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

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
      patchSets.add(parsePatchSet(token));
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
    checkArgument(!Strings.isNullOrEmpty(text), "Empty verification vote");
    int e = text.lastIndexOf('=');
    checkArgument(e >= 0, "Verification missing '=': %s", text);
    int p = text.lastIndexOf('|');
    checkArgument(e >= 0, "Verification missing '|': %s", text);
    VerificationInfo data = new VerificationInfo();
    data.value = Short.parseShort(text.substring(e + 1, p));
    data.url = text.substring(p + 1);
    jobResult.put(text.substring(0, e), data);
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
      } catch (NoSuchChangeException e) {
        ok = false;
        writeError("no such change " + patchSet.getId().getParentKey().get());
      } catch (Exception e) {
        ok = false;
        writeError("fatal: internal server error while approving "
            + patchSet.getId() + "\n");
        log.error("internal error while verifying " + patchSet.getId(), e);
      }
    }

    if (!ok) {
      throw new UnloggedFailure(1, "one or more veifications failed;"
          + " review output above");
    }
  }

  private void applyVerification(PatchSet patchSet, VerifyInput verify)
      throws Exception {
    gApi.get().changes()
        .id(patchSet.getId().getParentKey().get())
        .revision(patchSet.getRevision().get())
        .verify(verify);
  }

  private void verifyOne(PatchSet patchSet) throws Exception {
    VerifyInput verify = new VerifyInput();
    verify.jobs = jobResult;
    try {
      applyVerification(patchSet, verify);
    } catch (RestApiException e) {
      throw error(e.getMessage());
    }
  }

  private PatchSet parsePatchSet(String patchIdentity)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      RevId id = new RevId(patchIdentity);
      ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      Set<PatchSet> matches = new HashSet<>();
      for (PatchSet ps : patches) {
        Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change) && inBranch(change)) {
          matches.add(ps);
        }
      }

      switch (matches.size()) {
        case 1:
          return matches.iterator().next();
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      PatchSet patchSet = db.patchSets().get(patchSetId);
      if (patchSet == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null || branch != null) {
        Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
        if (!inBranch(change)) {
          throw error("change " + change.getId() + " not in branch "
              + change.getDest().get());
        }
      }
      return patchSet;
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private boolean inProject(Change change) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  private boolean inBranch(Change change) {
    if (branch == null) {
      // No --branch option, so they want every branch.
      return true;
    }
    return change.getDest().get().equals(branch);
  }

  private void writeError(String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

  private static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }
}
