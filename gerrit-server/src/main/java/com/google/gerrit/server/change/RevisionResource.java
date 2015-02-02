// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Optional;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import java.nio.charset.Charset;

public class RevisionResource implements RestResource, HasETag {
  public static final TypeLiteral<RestView<RevisionResource>> REVISION_KIND =
      new TypeLiteral<RestView<RevisionResource>>() {};

  private final ChangeResource change;
  private final PatchSet ps;
  private final Optional<ChangeEdit> edit;
  private boolean cacheable = true;

  public RevisionResource(ChangeResource change, PatchSet ps) {
    this(change, ps, Optional.<ChangeEdit> absent());
  }

  public RevisionResource(ChangeResource change, PatchSet ps,
      Optional<ChangeEdit> edit) {
    this.change = change;
    this.ps = ps;
    this.edit = edit;
  }

  public boolean isCacheable() {
    return cacheable;
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public ChangeControl getControl() {
    return getChangeResource().getControl();
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public ChangeNotes getNotes() {
    return getChangeResource().getNotes();
  }

  public PatchSet getPatchSet() {
    return ps;
  }

  @Override
  public String getETag() {
    // Conservative estimate: refresh the revision if its parent change has
    // changed, so we don't have to check whether a given modification affected
    // this revision specifically. If submitWholetopic is enabled, a change
    // may stay unchanged, but a change in the same topic was changed,
    // which may enable the submit button, so we need to take care of that
    // as well here.
    Provider<Submit> submitProvider;
    boolean submitWholeTopic = submitProvider.get().submitWholeTopicEnabled();

    if (submitWholeTopic
        && change.getChange().getTopic() != null
        && !change.getChange().getTopic().equals("")) {
          String topic = change.getChange().getTopic();
      Hasher h = Hashing.md5().newHasher();
      try {
        for (ChangeData c : submitProvider.get().changesByTopic(topic)) {
          h.putString(new ChangeResource(c.changeControl()).getETag(), Charset.forName("UTF-8"));
        }
      } catch (OrmException e){
        throw new OrmRuntimeException(e);
      }
      return h.hash().toString();
    } else {
      return change.getETag();
    }
  }

  Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  IdentifiedUser getUser() {
    return (IdentifiedUser) getControl().getCurrentUser();
  }

  RevisionResource doNotCache() {
    cacheable = false;
    return this;
  }

  Optional<ChangeEdit> getEdit() {
    return edit;
  }

  @Override
  public String toString() {
    String s = ps.getId().toString();
    if (edit.isPresent()) {
      s = "edit:" + s;
    }
    return s;
  }
}
