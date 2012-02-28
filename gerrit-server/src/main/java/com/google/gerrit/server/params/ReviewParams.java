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

package com.google.gerrit.common.data.params;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.inject.Inject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parameter class for all review related handlers */
public class ReviewParams {

  @Inject
  private ApprovalTypes approvalTypes;

  private List<Review> reviews = new ArrayList<Review>();

  public static class Review {

    private Labels labels;
    private String query;
    private String message;
    private ClosedActionType closedAction;

    public static class Labels extends HashSet<ApprovalCategoryValue.Id> {
    }

    public static class LabelsDeserializer implements JsonDeserializer<Labels> {
  
      @Inject
      private ApprovalTypes approvalTypes;

      @Override
      public Labels deserialize(final JsonElement json,
          Type typeOfT, final JsonDeserializationContext context)
          throws JsonParseException {
        Labels labels = new Labels();
        if (!json.isJsonObject()) {
          throw new JsonParseException("\"labels\" must be an object");
        }
        for (final Map.Entry<String, JsonElement> entry :
             json.getAsJsonObject().entrySet()) {
          final ApprovalType approvalType =
              approvalTypes.byLabel(entry.getKey());
          if (approvalType == null) {
            throw new JsonParseException("\"" + entry.getKey()
                + "\" is not a valid approval type");
          }
          final ApprovalCategory.Id categoryId =
              approvalType.getCategory().getId();
  
          final JsonElement value = entry.getValue();
          if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("\"label\" must be a string");
          }
          final Short valueShort = Short.valueOf(value.toString());
          if (valueShort == null) {
            throw new JsonParseException("\"" + value
                + "\" must represent an integer");
          }
          labels.add(new ApprovalCategoryValue.Id(categoryId, valueShort));
        }
        return labels;
      }
  
    }

    public Review() {
      labels = null;
      query = null;
      message = null;
      closedAction = ClosedActionType.SKIP;
    }

    public static enum ClosedActionType {
      /** Not a type. */
      NULL,

      /** Skip performing this review. */
      SKIP,

      /** Publish comment but skip labeling this change. */
      SKIP_LABELS
    }

    public String getQuery() {
      return query;
    }

    public String getMessage() {
      return message;
    }

    //public void setClosedAction(ClosedActionType closedAction) {
      //this.closedAction = closedAction;
    //}

    public ClosedActionType getClosedAction() {
      return closedAction;
    }

    public static ClosedActionType getClosedActionTypeByName(final String name) {
      if (name.equals("skip")) {
        return ClosedActionType.SKIP;
      } else if (name.equals("skip_labels")) {
        return ClosedActionType.SKIP_LABELS;
      }
      return ClosedActionType.NULL;
    }

    //public void addLabel(final ApprovalCategoryValue.Id label) {
      //labels.add(label);
    //}

    public Set<ApprovalCategoryValue.Id> getLabels() {
      return labels;
    }

  }

  public ReviewParams() {
  }

  //public ReviewParams(final String serialized) throws ValidationException {
    //super(serialized);
  //}

  public List<Review> getReviews() {
    return reviews;
  }

  //private void parseReview(final JsonObject obj)
      //throws ValidationException {
    //final Review review = new Review();

    //final JsonElement query = obj.get("query");
    //if (query == null) {
      //throw new ValidationException("\"query\" must exist in each review");
    //}
    //if (!query.isJsonPrimitive() || !query.getAsJsonPrimitive().isString()) {
      //throw new ValidationException("\"query\" must be a string");
    //}
    //review.setQuery(query.toString());

    //final JsonElement message = obj.get("message");
    //if (message != null) {
      //if (!message.isJsonPrimitive() ||
          //!message.getAsJsonPrimitive().isString()) {
        //throw new ValidationException("\"message\" must be a string");
      //}
      //review.setMessage(message.toString());
    //}

    //final JsonElement closedAction = obj.get("closed_action");
    //if (closedAction != null) {
      //if (!closedAction.isJsonPrimitive() ||
          //!closedAction.getAsJsonPrimitive().isString()) {
        //throw new ValidationException("\"closed_action\" must be a string");
      //}
      //final Review.ClosedActionType type =
          //Review.getClosedActionTypeByName(closedAction.toString());
      //if (type == Review.ClosedActionType.NULL) {
        //throw new ValidationException("\"" + closedAction
            //+ "\" is not a valid value for \"closed_action\"");
      //}
      //review.setClosedAction(type);
    //}

    //final JsonElement labels = obj.get("labels");
    //if (labels != null) {
      //if (!labels.isJsonObject()) {
        //throw new ValidationException("\"labels\" must be an object");
      //}
      //for (final Map.Entry<String, JsonElement> entry :
           //labels.getAsJsonObject().entrySet()) {
        //final ApprovalType approvalType =
            //approvalTypes.byLabel(entry.getKey());
        //if (approvalType == null) {
          //throw new ValidationException("\"" + entry.getKey()
              //+ "\" is not a valid approval type");
        //}
        //final ApprovalCategory.Id categoryId =
            //approvalType.getCategory().getId();

        //final JsonElement value = entry.getValue();
        //if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
          //throw new ValidationException("\"label\" must be a string");
        //}
        //final Short valueShort = Short.valueOf(value.toString());
        //if (valueShort == null) {
          //throw new ValidationException("\"" + value
              //+ "\" must represent an integer");
        //}
        //review.addLabel(new ApprovalCategoryValue.Id(categoryId, valueShort));
      //}
    //}

    //reviews.add(review);
  //}

  //protected void parseFields(final JsonObject obj)
      //throws ValidationException {
    //final JsonElement reviews = obj.get("reviews");
    //if (reviews == null) {
      //throw new ValidationException("\"reviews\" missing from obj");
    //}
    //if (!reviews.isJsonArray()) {
      //throw new ValidationException("\"reviews\" should be an array");
    //}

    //for (final JsonElement elem : obj.getAsJsonArray("reviews")) {
      //if (!reviews.isJsonObject()) {
        //throw new ValidationException(
            //"each element of \"reviews\" should be an object");
      //}
      //parseReview(elem.getAsJsonObject());
    //}
  //}

}
