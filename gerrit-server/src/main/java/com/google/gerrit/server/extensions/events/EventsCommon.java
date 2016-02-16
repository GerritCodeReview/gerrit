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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class EventsCommon {
  private static final Logger log =
      LoggerFactory.getLogger(EventsCommon.class);

  static String getSubject(Change change) {
    return change.getSubject();
  }

  static String getId(Change change) {
    return change.getKey().get();
  }

  static String getNumber(Change change) {
    return change.getId().toString();
  }

  static String getTopic(Change change) {
    return change.getTopic();
  }

  static String getBranch(Change change) {
    return change.getDest().getShortName();
  }

  static String getNumber(PatchSet ps) {
    return Integer.toString(ps.getPatchSetId());
  }

  static String getRevision(PatchSet ps) {
    return ps.getRevision().get();
  }

  static String getRef(PatchSet ps) {
    return ps.getRefName();
  }

  static List<String> getParents(PatchSet ps, RevWalk rw) {
    List<String> parents = new ArrayList<>();
    try {
      RevCommit c = rw.parseCommit(
          ObjectId.fromString(getRevision(ps)));
      for (RevCommit parent : c.getParents()) {
        parents.add(parent.name());
      }
    } catch (IOException e) {
      log.error("Cannot load patch set parents for " + ps.getId(), e);
    }
    return parents;
  }
}
