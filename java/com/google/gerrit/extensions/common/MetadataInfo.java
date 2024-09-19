// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Metadata populated by plugins, see {code com.google.gerrit.server.account.AccountStateProvider}
 * and {code com.google.gerrit.server.ServerStateProvider}.
 */
public class MetadataInfo {
  /**
   * The metadata name.
   *
   * <p>Not guaranteed to be unique, e.g. multiple metadata entries with the same name may be
   * returned.
   */
  public String name;

  /** The metadata value. May be unset. */
  @Nullable public String value;

  /** A description of the metadata. May be unset. */
  @Nullable public String description;

  /** Web links. May be unset. */
  @Nullable public List<WebLinkInfo> webLinks;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("value", value)
        .add("description", description)
        .add("webLinks", webLinks)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value, description, webLinks);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MetadataInfo) {
      MetadataInfo metadata = (MetadataInfo) o;
      return Objects.equals(name, metadata.name)
          && Objects.equals(value, metadata.value)
          && Objects.equals(description, metadata.description)
          && Objects.equals(webLinks, metadata.webLinks);
    }
    return false;
  }
}
