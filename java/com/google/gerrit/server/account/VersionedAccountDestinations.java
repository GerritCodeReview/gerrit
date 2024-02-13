// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;

/** User or Group configured named destinations. */
public class VersionedAccountDestinations extends VersionedMetaData {
  public static VersionedAccountDestinations forBranch(BranchNameKey branch) {
    return new VersionedAccountDestinations(branch.branch());
  }

  private final String ref;
  private final DestinationList destinations = new DestinationList();

  private VersionedAccountDestinations(String ref) {
    this.ref = ref;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public DestinationList getDestinationList() {
    return destinations;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    // named destinations are not support internally, never load them
    return;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    throw new UnsupportedOperationException("Cannot yet save destinations");
  }
}
