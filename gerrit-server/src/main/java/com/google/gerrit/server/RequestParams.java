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

package com.google.gerrit.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.util.Set;

/** Base type for parameter objects represented in JSON */
public abstract class RequestParams {

  private static final JsonParser parser = new JsonParser();

  public class ValidationException extends Exception {
    ValidationException(final String message) {
      super(message);
    }
  }

  public RequestParams() {
  }

  public RequestParams(final String serialized) throws ValidationException {
    parse(serialized);
  }

  abstract protected void parseFields(final JsonObject params)
      throws ValidationException;

  public void parse(final String serialized) throws ValidationException {
    JsonElement params = null;
    try {
      params = parser.parse(serialized);
    } catch (JsonParseException e) {
      throw new ValidationException(e.getMessage());
    }

    if (!params.isJsonObject()) {
      throw new ValidationException("params is not object");
    }
    parseFields(params.getAsJsonObject());
  }

}
