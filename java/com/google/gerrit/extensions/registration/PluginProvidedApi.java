// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.extensions.registration;

import java.util.HashMap;

public interface PluginProvidedApi {
  /* something went wrong calling into another plugin. */
  public class PluginApiException extends Exception {
    public PluginApiException(String msg) {
      super(msg);
    }
  }

  /* utility: use this type to create ad-hoc data-types */
  public class Message extends HashMap<String, Object> {}

  Object call(Object req) throws PluginApiException;

  public class Client<I, O> {
    PluginProvidedApi api;

    public Client(PluginProvidedApi api) {
      this.api = api;
    };

    public O call(I in) throws PluginApiException {
      Object out = api.call(in);
      try {
        return (O) out;
      } catch (ClassCastException e) {
        throw new PluginApiException("Plugin provided wrong return type: " + e);
      }
    }
  }

  // TODO - a server-side wrapper that checks the request type.

  /**
   * TODO - Perhaps the types could be encoded in the binding, so a mismatch in types would lead to
   * the interface not being found.
   */
}
