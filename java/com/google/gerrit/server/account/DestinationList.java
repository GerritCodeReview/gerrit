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

import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.TabFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class DestinationList extends TabFile {
  public static final String DIR_NAME = "destinations";
  private SetMultimap<String, BranchNameKey> destinations =
      MultimapBuilder.hashKeys().hashSetValues().build();

  public Set<BranchNameKey> getDestinations(String label) {
    return destinations.get(label);
  }

  void parseLabel(String label, String text, ValidationError.Sink errors) throws IOException {
    destinations.replaceValues(label, toSet(parse(text, DIR_NAME + label, TRIM, null, errors)));
  }

  String asText(String label) {
    Set<BranchNameKey> dests = destinations.get(label);
    if (dests == null) {
      return null;
    }
    List<Row> rows = Lists.newArrayListWithCapacity(dests.size());
    for (BranchNameKey dest : sort(dests)) {
      rows.add(new Row(dest.branch(), dest.project().get()));
    }
    return asText("Ref", "Project", rows);
  }

  private static Set<BranchNameKey> toSet(List<Row> destRows) {
    Set<BranchNameKey> dests = Sets.newHashSetWithExpectedSize(destRows.size());
    for (Row row : destRows) {
      dests.add(BranchNameKey.create(Project.nameKey(row.right), row.left));
    }
    return dests;
  }
}
