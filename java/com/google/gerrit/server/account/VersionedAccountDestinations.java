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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;

/** User configured named destinations. */
public class VersionedAccountDestinations extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static VersionedAccountDestinations forUser(Account.Id id) {
    return new VersionedAccountDestinations(RefNames.refsUsers(id));
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
    if (revision == null) {
      return;
    }
    String prefix = DestinationList.DIR_NAME + "/";
    for (PathInfo p : getPathInfos(true)) {
      if (p.fileMode == FileMode.REGULAR_FILE) {
        String path = p.path;
        if (path.startsWith(prefix)) {
          String label = path.substring(prefix.length());
          destinations.parseLabel(
              label,
              readUTF8(path),
              error ->
                  logger.atSevere().log("Error parsing file %s: %s", path, error.getMessage()));
        }
      }
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    throw new UnsupportedOperationException("Cannot yet save destinations");
  }
}
