// Copyright (C) 2011 The Android Open Source Project
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

/**
 *
 * @author conleyo@google.com (Conley Owens)
 */
public abstract class RequestParams {

  private static final JsonParser parser = new JsonParser();

  private JsonElement wrapped;

  public class ValidationException extends Exception {
    ValidationException(final String message) {
      super(message);
    }
  }

  public RequestParams() {
  }

  public RequestParams(final String serialized) {
    this();
    update(serialized);
  }

  public void update(final String serialized)
      throws JsonParseException, JsonSyntaxException{
    wrapped = parser.parse(serialized);
  }

  abstract protected void validationImpl(final JsonObject obj)
      throws ValidationException;

  public void  validate() throws ValidationException {
    if (!wrapped.isJsonObject()) {
      throw new ValidationException("params is not object");
    }
    validationImpl(wrapped.getAsJsonObject());
  }

  public JsonObject getObject() {
    return wrapped.getAsJsonObject();
  }

}
