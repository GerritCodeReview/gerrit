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

public interface PluginApi {
  PluginInfo get() throws RestApiException;

  void enable() throws RestApiException;

  void disable() throws RestApiException;

  void reload() throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements PluginApi {
    @Override
    public PluginInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void enable() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void disable() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void reload() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
