// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.ObjectId;

import java.util.LinkedList;
import java.util.List;

public class BanCommitResult {

  private final List<ObjectId> newlyBannedCommits = new LinkedList<ObjectId>();
  private final List<ObjectId> alreadyBannedCommits = new LinkedList<ObjectId>();
  private final List<ObjectId> ignoredObjectIds = new LinkedList<ObjectId>();

  public BanCommitResult() {
  }

  public void commitBanned(final ObjectId commitId) {
    newlyBannedCommits.add(commitId);
  }

  public void commitAlreadyBanned(final ObjectId commitId) {
    alreadyBannedCommits.add(commitId);
  }

  public void notACommit(final ObjectId id, final String message) {
    ignoredObjectIds.add(id);
  }

  public List<ObjectId> getNewlyBannedCommits() {
    return newlyBannedCommits;
  }

  public List<ObjectId> getAlreadyBannedCommits() {
    return alreadyBannedCommits;
  }

  public List<ObjectId> getIgnoredObjectIds() {
    return ignoredObjectIds;
  }
}
