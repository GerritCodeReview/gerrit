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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.VerifyInput;
import com.google.gerrit.extensions.common.VerificationInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSetVerification;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
public class PostVerification implements RestModifyView<RevisionResource, VerifyInput> {
  private final Provider<ReviewDb> db;
  private static final Logger log = LoggerFactory.getLogger(PostVerification.class);

  @Inject
  PostVerification(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public Response<?> apply(RevisionResource revision, VerifyInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      OrmException, IOException {
    if (input.verifications == null) {
      throw new BadRequestException("Missing verifications field");
    }

    db.get().changes().beginTransaction(revision.getChange().getId());
    boolean dirty = false;
    try {
      Change change = db.get().changes().get(revision.getChange().getId());
      dirty |= updateLabels(revision, input.verifications);
      if (dirty) {
        db.get().changes().update(Collections.singleton(change));
        db.get().commit();
      }
    } finally {
      db.get().rollback();
    }
    return Response.none();
  }

  private boolean updateLabels(RevisionResource resource,
      Map<String, VerificationInfo> jobs)
      throws OrmException, BadRequestException {
    Preconditions.checkNotNull(jobs);

    List<PatchSetVerification> ups = Lists.newArrayList();
    Map<String, PatchSetVerification> current = scanLabels(resource);

    Timestamp ts = TimeUtil.nowTs();
    for (Map.Entry<String, VerificationInfo> ent : jobs.entrySet()) {
      String name = ent.getKey();
      PatchSetVerification c = current.remove(name);
      Short value = ent.getValue().value;
      if (value == null) {
        throw new BadRequestException("Missing value field");
      }
      if (c != null) {
        c.setGranted(ts);
        c.setValue(value);
        String url = ent.getValue().url;
        if (url != null) {
          c.setUrl(url);
        }
        String verifier = ent.getValue().verifier;
        if (verifier != null) {
          c.setVerifier(verifier);
        }
        String comment = ent.getValue().comment;
        if (comment != null) {
          c.setComment(comment);
        }
        log.info("Updating job " + c.getLabel() + " for change "
            + c.getPatchSetId());
        ups.add(c);
      } else {
        c = new PatchSetVerification(new PatchSetVerification.Key(
                resource.getPatchSet().getId(),
                new LabelId(name)),
            value, TimeUtil.nowTs());
        c.setGranted(ts);
        c.setUrl(ent.getValue().url);
        c.setVerifier(ent.getValue().verifier);
        c.setComment(ent.getValue().comment);
        log.info("Adding job " + c.getLabel() + " for change "
            + c.getPatchSetId());
        ups.add(c);
      }
    }

    db.get().patchSetVerifications().upsert(ups);
    return !ups.isEmpty();
  }

  private Map<String, PatchSetVerification> scanLabels(RevisionResource resource)
      throws OrmException {
    Map<String, PatchSetVerification> current = Maps.newHashMap();
    for (PatchSetVerification v : db.get().patchSetVerifications()
        .byPatchSet(resource.getPatchSet().getId())) {
      current.put(v.getLabelId().get(), v);
    }
    return current;
  }
}
