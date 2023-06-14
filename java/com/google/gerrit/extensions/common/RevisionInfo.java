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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.client.ChangeKind;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RevisionInfo {
  // ActionJson#copy(List, RevisionInfo) must be adapted if new fields are added that are not
  // protected by any ListChangesOption.
  public transient boolean isCurrent;
  public ChangeKind kind;
  public int _number;

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp created;

  public AccountInfo uploader;
  public AccountInfo realUploader;
  public String ref;
  public Map<String, FetchInfo> fetch;
  public CommitInfo commit;
  public List<ParentInfo> parentsData;
  public String branch;
  public Map<String, FileInfo> files;
  public Map<String, ActionInfo> actions;
  public String commitWithFooters;
  public PushCertificateInfo pushCertificate;
  public String description;

  public RevisionInfo() {}

  public RevisionInfo(String ref) {
    this.ref = ref;
  }

  public RevisionInfo(String ref, int number) {
    this.ref = ref;
    _number = number;
  }

  public RevisionInfo(AccountInfo uploader) {
    this.uploader = uploader;
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setCreated(Instant date) {
    this.created = Timestamp.from(date);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof RevisionInfo) {
      RevisionInfo revisionInfo = (RevisionInfo) o;
      return isCurrent == revisionInfo.isCurrent
          && Objects.equals(kind, revisionInfo.kind)
          && _number == revisionInfo._number
          && Objects.equals(created, revisionInfo.created)
          && Objects.equals(uploader, revisionInfo.uploader)
          && Objects.equals(realUploader, revisionInfo.realUploader)
          && Objects.equals(ref, revisionInfo.ref)
          && Objects.equals(fetch, revisionInfo.fetch)
          && Objects.equals(commit, revisionInfo.commit)
          && Objects.equals(parentsData, revisionInfo.parentsData)
          && Objects.equals(branch, revisionInfo.branch)
          && Objects.equals(files, revisionInfo.files)
          && Objects.equals(actions, revisionInfo.actions)
          && Objects.equals(commitWithFooters, revisionInfo.commitWithFooters)
          && Objects.equals(pushCertificate, revisionInfo.pushCertificate)
          && Objects.equals(description, revisionInfo.description);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        isCurrent,
        kind,
        _number,
        created,
        uploader,
        realUploader,
        ref,
        fetch,
        commit,
        parentsData,
        branch,
        files,
        actions,
        commitWithFooters,
        pushCertificate,
        description);
  }

  public static class ParentInfo {
    /** The name of the target branch where the patch-set commit is set to be merged into. */
    public String branchName;

    /** The commit SHA-1 of the parent commit. */
    public String commitId;

    /** Whether the parent commit is merged in the target branch. */
    public Boolean isMergedInTargetBranch;

    /**
     * If the parent commit is a patch-set of another gerrit change, this field will hold the change
     * ID of the parent change. Otherwise, will be null.
     */
    public String changeId;

    /**
     * If the parent commit is a patch-set of another gerrit change, this field will hold the change
     * number of the parent change. Otherwise, will be null.
     */
    public Integer changeNumber;

    /**
     * If the parent commit is a patch-set of another gerrit change, this field will hold the
     * patch-set number of the parent change. Otherwise, will be null.
     */
    public Integer patchSetNumber;

    /**
     * If the parent commit is a patch-set of another gerrit change, this field will hold the change
     * status of the parent change. Otherwise, will be null.
     */
    public String changeStatus;

    @Override
    public boolean equals(Object o) {
      if (o instanceof ParentInfo) {
        ParentInfo parentInfo = (ParentInfo) o;
        return Objects.equals(branchName, parentInfo.branchName)
            && Objects.equals(commitId, parentInfo.commitId)
            && Objects.equals(isMergedInTargetBranch, parentInfo.isMergedInTargetBranch)
            && Objects.equals(changeId, parentInfo.changeId)
            && Objects.equals(changeNumber, parentInfo.changeNumber)
            && Objects.equals(patchSetNumber, parentInfo.patchSetNumber)
            && Objects.equals(changeStatus, parentInfo.changeStatus);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          branchName,
          commitId,
          isMergedInTargetBranch,
          changeId,
          changeNumber,
          patchSetNumber,
          changeStatus);
    }
  }
}
