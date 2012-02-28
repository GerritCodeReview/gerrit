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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 *
 * @author conleyo@google.com (Conley Owens)
 */
public class ReviewParams extends RequestParams {

  public ReviewParams() {
    super();
  }

  public ReviewParams(final String serialized) {
    super(serialized);
  }

  private void validateLabels(final JsonObject labels)
      throws ValidationException {
    for (final Map.Entry<String, JsonElement> entry : labels.entrySet()) {
      final JsonElement label = entry.getValue();
      if (!label.isJsonPrimitive() || !label.getAsJsonPrimitive().isString()) {
        throw new ValidationException("\"label\" must be a string");
      }
    }
  }

  private void validateReview(final JsonObject review)
      throws ValidationException {
    final JsonElement query = review.get("query");
    if (query == null) {
      throw new ValidationException("\"query\" must exist in each review");
    }
    if (!query.isJsonPrimitive() || !query.getAsJsonPrimitive().isString()) {
      throw new ValidationException("\"query\" must be a string");
    }

    final JsonElement message = review.get("message");
    if (message != null &&
        (!message.isJsonPrimitive() ||
         !message.getAsJsonPrimitive().isString())) {
      throw new ValidationException("\"message\" must be a string");
    }

    final JsonElement forceMessage = review.get("force_message");
    if (forceMessage != null &&
        (!forceMessage.isJsonPrimitive() ||
         !forceMessage.getAsJsonPrimitive().isBoolean())) {
      throw new ValidationException("\"force_message\" must be a boolean");
    }

    final JsonElement labels = review.get("labels");
    if (labels != null && !labels.isJsonObject()) {
      throw new ValidationException("\"labels\" must be an object");
    }
    validateLabels(labels.getAsJsonObject());

    final JsonElement action = review.get("action");
    if (action == null) {
      throw new ValidationException("\"action\" must exist in each review");
    }
    if (!action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) {
      throw new ValidationException("\"action\" must be a string");
    }
    final String actionStr = action.getAsJsonPrimitive().getAsString();
    if (!actionStr.equals("restore") &&
        !actionStr.equals("submit") &&
        !actionStr.equals("publish") &&
        !actionStr.equals("delete") &&
        !actionStr.equals("comment")) {
      throw new ValidationException("\"action\" cannot be " + actionStr);
    }
  }

  protected void validationImpl(final JsonObject obj)
      throws ValidationException {
    final JsonElement reviews = obj.get("reviews");
    if (reviews == null) {
      throw new ValidationException("\"reviews\" missing from params");
    }
    if (!reviews.isJsonArray()) {
      throw new ValidationException("\"reviews\" should be an array");
    }

    for (final JsonElement elem : obj.getAsJsonArray("reviews")) {
      if (!reviews.isJsonObject()) {
        throw new ValidationException(
            "each element of \"reviews\" should be an object");
      }
      validateReview(elem.getAsJsonObject());
    }
  }

}
