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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.restapi.change.GetBlame;
import com.google.gerrit.server.restapi.change.GetContent;
import com.google.gerrit.server.restapi.change.GetDiff;
import com.google.gerrit.server.restapi.change.Reviewed;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.List;

class FileApiImpl implements FileApi {
  interface Factory {
    FileApiImpl create(FileResource r);
  }

  private final GetContent getContent;
  private final Provider<GetBlame> getBlame;
  private final GetDiff getDiff;
  private final Reviewed.PutReviewed putReviewed;
  private final Reviewed.DeleteReviewed deleteReviewed;
  private final FileResource file;

  @Inject
  FileApiImpl(
      GetContent getContent,
      Provider<GetBlame> getBlame,
      GetDiff getDiff,
      Reviewed.PutReviewed putReviewed,
      Reviewed.DeleteReviewed deleteReviewed,
      @Assisted FileResource file) {
    this.getContent = getContent;
    this.getBlame = getBlame;
    this.getDiff = getDiff;
    this.putReviewed = putReviewed;
    this.deleteReviewed = deleteReviewed;
    this.file = file;
  }

  @Override
  public BinaryResult content() throws RestApiException {
    try {
      return getContent.apply(file).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve file content", e);
    }
  }

  @Override
  public DiffInfo diff() throws RestApiException {
    try {
      return getDiff.apply(file).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve diff", e);
    }
  }

  @Override
  public DiffInfo diff(String base) throws RestApiException {
    try {
      return getDiff.setBase(base).apply(file).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve diff", e);
    }
  }

  @Override
  public DiffInfo diff(int parent) throws RestApiException {
    try {
      return getDiff.setParent(parent).apply(file).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve diff", e);
    }
  }

  @Override
  public DiffRequest diffRequest() {
    return new DiffRequest() {
      @Override
      public DiffInfo get() throws RestApiException {
        return FileApiImpl.this.get(this);
      }
    };
  }

  @Override
  public void setReviewed(boolean reviewed) throws RestApiException {
    try {
      if (reviewed) {
        putReviewed.apply(file, new Input());
      } else {
        deleteReviewed.apply(file, new Input());
      }
    } catch (Exception e) {
      throw asRestApiException(String.format("Cannot set %sreviewed", reviewed ? "" : "un"), e);
    }
  }

  private DiffInfo get(DiffRequest r) throws RestApiException {
    if (r.getBase() != null) {
      getDiff.setBase(r.getBase());
    }
    if (r.getContext() != null) {
      getDiff.setContext(r.getContext());
    }
    if (r.getIntraline() != null) {
      getDiff.setIntraline(r.getIntraline());
    }
    if (r.getWhitespace() != null) {
      getDiff.setWhitespace(r.getWhitespace());
    }
    r.getParent().ifPresent(getDiff::setParent);
    try {
      return getDiff.apply(file).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve diff", e);
    }
  }

  @Override
  public BlameRequest blameRequest() throws RestApiException {
    return new BlameRequest() {
      @Override
      public List<BlameInfo> get() throws RestApiException {
        try {
          return getBlame.get().setBase(isForBase()).apply(file).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot retrieve blame", e);
        }
      }
    };
  }
}
