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

import static com.google.common.base.Strings.nullToEmpty;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GetGroup.GroupInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ListIncludedGroups implements RestReadView<GroupResource> {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ListIncludedGroups.class);

  private final GroupControl.Factory controlFactory;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  ListIncludedGroups(GroupControl.Factory controlFactory,
      Provider<ReviewDb> dbProvider) {
    this.controlFactory = controlFactory;
    this.dbProvider = dbProvider;
  }

  @Override
  public List<GroupInfo> apply(GroupResource rsrc)
      throws ResourceNotFoundException, OrmException {
    if (!rsrc.isInternal()) {
      throw new ResourceNotFoundException(rsrc.getGroupUUID().get());
    }

    boolean ownerOfParent = rsrc.getControl().isOwner();
    List<GroupInfo> included = Lists.newArrayList();
    for (AccountGroupIncludeByUuid u : dbProvider.get()
        .accountGroupIncludesByUuid()
        .byGroup(groupId(rsrc))) {
      try {
        GroupControl i = controlFactory.controlFor(u.getIncludeUUID());
        if (ownerOfParent || i.isVisible()) {
          included.add(new GetGroup.GroupInfo(i.getGroup()));
        }
      } catch (NoSuchGroupException notFound) {
        log.warn(String.format("Group %s no longer available, included into ",
            u.getIncludeUUID(),
            rsrc.getGroup().getName()));
        continue;
      }
    }
    Collections.sort(included, new Comparator<GroupInfo>() {
      @Override
      public int compare(GroupInfo a, GroupInfo b) {
        int cmp = nullToEmpty(a.name).compareTo(nullToEmpty(b.name));
        if (cmp != 0) {
          return cmp;
        }
        return nullToEmpty(a.id).compareTo(nullToEmpty(b.name));
      }
    });
    return included;
  }

  private static AccountGroup.Id groupId(GroupResource rsrc) {
    GroupDescription.Basic d = rsrc.getGroup();
    return ((GroupDescription.Internal) d).getAccountGroup().getId();
  }
}
