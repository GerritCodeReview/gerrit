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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.List;
import org.kohsuke.args4j.Option;

public class ListMembers implements RestReadView<GroupResource> {
  protected final GroupMembers groupMembers;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  protected ListMembers(GroupMembers groupMembers) {
    this.groupMembers = groupMembers;
  }

  public ListMembers setRecursive(boolean recursive) {
    this.recursive = recursive;
    return this;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource)
      throws MethodNotAllowedException, OrmException {
    GroupDescription.Internal group =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);
    if (recursive) {
      return groupMembers.getTransitiveMembers(group, resource.getControl());
    }
    return groupMembers.getDirectMembers(group, resource.getControl());
  }
}
