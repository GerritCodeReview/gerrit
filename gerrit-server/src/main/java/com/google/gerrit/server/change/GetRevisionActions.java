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

package com.google.gerrit.server.change;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.nio.charset.Charset;

@Singleton
public class GetRevisionActions implements RestReadView<RevisionResource>,
com.google.gerrit.extensions.restapi.ETagView {
  private final ActionJson delegate;
  private final Provider<Submit> submitProvider;

  @Inject
  GetRevisionActions(
      ActionJson delegate,
      Provider<Submit> submitProvider
      ) {
    this.delegate = delegate;
    this.submitProvider = submitProvider;
  }

  @Override
  public Object apply(RevisionResource rsrc) {
    return Response.withMustRevalidate(delegate.format(rsrc));
  }

  @Override
  public String getETag(RestResource rsrc) {
    RevisionResource rev;
    if (rsrc instanceof RevisionResource) {
      rev = (RevisionResource) rsrc;
    } else {
      throw new RuntimeException("wat");
    }
    boolean submitWholeTopic = submitProvider.get().submitWholeTopicEnabled();
    if (submitWholeTopic
        && rev.getChange().getTopic() != null
        && !rev.getChange().getTopic().equals("")) {
      String topic = rev.getChange().getTopic();
      Hasher h = Hashing.md5().newHasher();
      try {
        for (ChangeData c : submitProvider.get().changesByTopic(topic)) {
          h.putString(new ChangeResource(c.changeControl()).getETag(),
              Charset.forName("UTF-8"));
        }
      } catch (OrmException e){
        throw new OrmRuntimeException(e);
      }
      return h.hash().toString();
    } else {
      return rev.getChangeResource().getETag();
    }
  }
}
