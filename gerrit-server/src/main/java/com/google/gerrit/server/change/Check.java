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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Check implements RestReadView<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Check.class);

  private final Provider<ConsistencyChecker> checkerProvider;
  private final ChangeJson json;

  @Inject
  Check(Provider<ConsistencyChecker> checkerProvider,
      ChangeJson json) {
    this.checkerProvider = checkerProvider;
    this.json = json;
  }

  @Override
  public CheckResult apply(ChangeResource rsrc) {
    CheckResult result = new CheckResult();
    result.messages = checkerProvider.get().check(rsrc.getChange());
    try {
      result.change = json.format(rsrc);
    } catch (OrmException e) {
      // Even with no options there are a surprising number of dependencies in
      // ChangeJson. Fall back to a very basic implementation with no
      // dependencies if this fails.
      String msg = "Error rendering final ChangeInfo";
      log.warn(msg, e);
      result.messages.add(msg);
      result.change = basicChangeInfo(rsrc.getChange());
    }
    return result;
  }

  private static ChangeInfo basicChangeInfo(Change c) {
    ChangeInfo info = new ChangeInfo();
    info.project = c.getProject().get();
    info.branch = c.getDest().getShortName();
    info.topic = c.getTopic();
    info.changeId = c.getKey().get();
    info.subject = c.getSubject();
    info.status = c.getStatus();
    info.owner = new AccountInfo(c.getOwner());
    info.created = c.getCreatedOn();
    info.updated = c.getLastUpdatedOn();
    info._number = c.getId().get();
    info.finish();
    return info;
  }
}
