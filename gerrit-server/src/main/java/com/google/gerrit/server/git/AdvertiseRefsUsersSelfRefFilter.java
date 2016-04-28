// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;

import java.util.HashMap;
import java.util.Map;

public class AdvertiseRefsUsersSelfRefFilter implements RefFilter {

  private final Provider<CurrentUser> self;

  @Inject
  AdvertiseRefsUsersSelfRefFilter(Provider<CurrentUser> self) {
    this.self = self;
  }

  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs) {
    if (!self.get().isIdentifiedUser()) {
      return refs;
    }

    final Ref userRef = refs.get(RefNames.refsUsers(self.get().getAccountId()));
    if (userRef != null) {
      refs = new HashMap<>(refs);
      refs.put(RefNames.REFS_USERS_SELF, new Ref() {
        @Override
        public String getName() {
          return RefNames.REFS_USERS_SELF;
        }

        @Override
        public boolean isSymbolic() {
          return userRef.isSymbolic();
        }

        @Override
        public Ref getLeaf() {
          return userRef.getLeaf();
        }

        @Override
        public Ref getTarget() {
          return userRef.getTarget();
        }

        @Override
        public ObjectId getObjectId() {
          return userRef.getObjectId();
        }

        @Override
        public ObjectId getPeeledObjectId() {
          return userRef.getPeeledObjectId();
        }

        @Override
        public boolean isPeeled() {
          return userRef.isPeeled();
        }

        @Override
        public Storage getStorage() {
          return userRef.getStorage();
        }
      });
    }
    return refs;
  }
}
