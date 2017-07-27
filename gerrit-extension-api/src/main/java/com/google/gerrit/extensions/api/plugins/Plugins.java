// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.plugins;

import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public interface Plugins {

  ListRequest list();

  abstract class ListRequest {
    private boolean all;

    public List<PluginInfo> get() throws RestApiException {
      Map<String, PluginInfo> map = getAsMap();
      List<PluginInfo> result = new ArrayList<>(map.size());
      for (Map.Entry<String, PluginInfo> e : map.entrySet()) {
        result.add(e.getValue());
      }
      return result;
    }

    public abstract SortedMap<String, PluginInfo> getAsMap() throws RestApiException;

    public ListRequest all(boolean all) {
      this.all = all;
      return this;
    }

    public boolean getAll() {
      return all;
    }
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements Plugins {
    @Override
    public ListRequest list() {
      throw new NotImplementedException();
    }
  }
}
