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

package com.google.gerrit.server.git;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class DestinationList extends TabFile {
  public static final String DIR_NAME = "destinations";
  private SetMultimap<String, Branch.NameKey> destinations = HashMultimap.create();

  public Set<Branch.NameKey> getDestinations(String label) {
    return destinations.get(label);
  }

  public void parseLabel(String label, String text, ValidationError.Sink errors)
      throws IOException {
    destinations.replaceValues(label, toSet(parse(text, DIR_NAME + label, TRIM, null, errors)));
  }

  public String asText(String label) {
    Set<Branch.NameKey> dests = destinations.get(label);
    if (dests == null) {
      return null;
    }
    List<Row> rows = Lists.newArrayListWithCapacity(dests.size());
    for (Branch.NameKey dest : sort(dests)) {
      rows.add(new Row(dest.get(), dest.getParentKey().get()));
    }
    return asText("Ref", "Project", rows);
  }

  protected static Set<Branch.NameKey> toSet(List<Row> destRows) {
    Set<Branch.NameKey> dests = Sets.newHashSetWithExpectedSize(destRows.size());
    for (Row row : destRows) {
      dests.add(new Branch.NameKey(new Project.NameKey(row.right), row.left));
    }
    return dests;
  }
}
