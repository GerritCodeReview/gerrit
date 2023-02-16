// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.restapi.change.ApplyPatch;
import com.google.gerrit.server.restapi.change.GetPatch;
import com.google.gerrit.server.util.time.TimeUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class ApplyPatchAndroidIT {

  private Repository originalRepository;
  private Repository destRepository;

  private static final String SERVER_NAME = "Gerrit Server";
  private static final String SERVER_EMAIL = "noreply@gerritcodereview.com";
  private static final ZoneId ZONE_ID = ZoneId.of("America/Los_Angeles");
  PersonIdent COMMITER = new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.now(), ZONE_ID);

  private Repository buildRepoFromPath(File path) throws IOException {
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setMustExist(true);
    // git clone --mirror "https://android.googlesource.com/platform/packages/modules/Bluetooth"
    repositoryBuilder.setGitDir(path);
    return repositoryBuilder.build();
  }
  @Test
  public void testAndroidPatches_success() throws Exception {
    // git clone --mirror "https://android.googlesource.com/platform/packages/modules/Bluetooth"
    originalRepository = buildRepoFromPath(new File("/tmp/android_repo/Bluetooth.git"));
    destRepository = originalRepository;
    List<Ref> firstRefs =
        originalRepository.getRefDatabase().getRefsByPrefix("refs/changes/").stream()
            .filter(ref -> PatchSet.Id.fromRef(ref.getName()) != null)
            .sorted(
                (ref1, ref2) -> {
                  PatchSet.Id ps1 = PatchSet.Id.fromRef(ref1.getName());
                  PatchSet.Id ps2 = PatchSet.Id.fromRef(ref2.getName());
                  int changeComp = Integer.compare(ps1.changeId().get(), ps2.changeId().get());
                  return changeComp != 0 ? changeComp : ps1.compareTo(ps2);
                })
            .limit(2000)
            .collect(Collectors.toUnmodifiableList());

    LinkedHashMap<Change.Id, ObjectId> changeIdToLatestCommit = new LinkedHashMap<>();
    for (Ref ref : firstRefs) {
      PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
      if (psId == null) {
        continue;
      }
      changeIdToLatestCommit.put(psId.changeId(), ref.getObjectId());
    }
    List<Map.Entry<Change.Id, ObjectId>> psIdToLatestCommitList =
        changeIdToLatestCommit.entrySet().stream().collect(Collectors.toList());

    List<String> changesWithNoOriginalDiff = new ArrayList<>();
    try (RevWalk rw = new RevWalk(originalRepository)) {
      for (int i = 0; i < psIdToLatestCommitList.size(); i++) {
        Map.Entry<Change.Id, ObjectId> changeAndCommit = psIdToLatestCommitList.get(i);
        ObjectId originalCommitId = changeAndCommit.getValue();
        BinaryResult originalPatch = null;
        BinaryResult resultPatch = null;

        try {
          RevCommit originalCommit = rw.parseCommit(originalCommitId);
          rw.parseBody(originalCommit);
          if (originalCommit.getParentCount() != 1) {
            continue;
          }
          RevCommit parentCommit = originalCommit.getParent(0);
          rw.parseBody(parentCommit);

           originalPatch = getPatch(originalRepository, originalCommitId);
          try {
            removeHeader(originalPatch);
          } catch (Exception e) {
            changesWithNoOriginalDiff.add("change "+changeAndCommit.getKey()+", commit "+originalCommitId + "\ncommit message:\n"+ originalCommit.getFullMessage());
            continue;
          }
          ApplyPatchPatchSetInput in = buildInput(originalPatch.asString());

          ObjectId appliedCommit;
          appliedCommit = ApplyPatch.apply(originalRepository, parentCommit, in.patch, COMMITER);
           resultPatch = getPatch(destRepository, appliedCommit);
          assertThat(removeHeader(resultPatch))
                  .isEqualTo(removeHeader(originalPatch));
        } catch (Exception e) {
          throw new Exception(
              "Problem testing ["
                  + i
                  + "]: change "
                  + changeAndCommit.getKey().toString()
                  + ", commit "
                  + originalCommitId.getName()
                      + "\noriginal:\n" + originalPatch.asString()
                      + "\ndest:\n" + resultPatch.asString()
                  , e);
        }
      }
    }
    // This should fail. Putting that to easily list all the changes we couldn't get diff for, for manual verification.
    assertThat(changesWithNoOriginalDiff).isEmpty();
  }

  private BinaryResult getPatch(Repository repo, ObjectId commit)
      throws ResourceConflictException, IOException, ResourceNotFoundException {
    return GetPatch.apply(repo, commit, false, false, null).value();
  }

  private ApplyPatchPatchSetInput buildInput(String patch) {
    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = patch;
    return in;
  }
  private String removeHeader(BinaryResult bin) throws Exception {
    return removeHeader(bin.asString());
  }

  private String removeHeader(String s) throws Exception {
    int diffIndex = s.lastIndexOf("\ndiff --git");
    if(diffIndex == -1) {
      throw new Exception("No diff found in:\n" + s + "\n~~~~~~~~end of commit message~~~~~~~~");
    }
    return s.substring(diffIndex, s.length() - 1);
  }
}
