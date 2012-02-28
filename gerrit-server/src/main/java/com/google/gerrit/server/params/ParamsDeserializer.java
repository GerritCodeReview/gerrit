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

//import com.google.gerrit.common.data.ApprovalTypes;
//import com.google.gerrit.reviewdb.client.ApprovalCategory;
//import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//import com.google.inject.Inject;

//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
import java.util.Set;

/** Parameter class for all review related handlers */
public class ParamsDeserializer {

  public static Gson gson = null;

  public ParamsDeserializer() {
  }

  public static Gson getGson() {
    if (gson == null) {
      final GsonBuilder gsonBuilder = new GsonBuilder();
      gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
      gsonBuilder.registerTypeAdapter(ReviewParams.Review.Labels.class,
                                      new ReviewParams.Review.LabelsDeserializer());
      gson = gsonBuilder.create();
    }
    return gson;
  }
}
