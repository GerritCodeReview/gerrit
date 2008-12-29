// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.KeyUtil;

/** Content of a single patch file */
public final class PatchContent {
  public static class Key implements
      com.google.gwtorm.client.Key<com.google.gwtorm.client.Key<?>> {
    @Column(length = 40)
    protected String sha1;

    protected Key() {
    }

    public Key(final String ps) {
      sha1 = ps;
    }

    @Override
    public int hashCode() {
      return sha1.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      return getClass() == o.getClass() && sha1.equals(((Key) o).sha1);
    }

    @Override
    public String toString() {
      return KeyUtil.encode(sha1);
    }

    public com.google.gwtorm.client.Key<?> getParentKey() {
      return null;
    }
  }

  @Column(name = Column.NONE)
  protected PatchContent.Key key;

  @Column(length = Integer.MAX_VALUE)
  protected String content;

  protected PatchContent() {
  }

  public PatchContent(final PatchContent.Key k, final String c) {
    key = k;
    content = c;
  }

  public PatchContent.Key getKey() {
    return key;
  }

  public String getContent() {
    return content;
  }
}
