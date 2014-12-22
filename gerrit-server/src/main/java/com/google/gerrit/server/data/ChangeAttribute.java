// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.data;

import com.google.gerrit.reviewdb.client.Change;

import java.util.List;

public class ChangeAttribute {
    public String project;
    public String branch;
    public String topic;
    public String id;
    public String number;
    public String subject;
    public AccountAttribute owner;
    public String url;
    public String commitMessage;

    public Long createdOn;
    public Long lastUpdated;
    public Boolean open;
    public Change.Status status;
    public List<MessageAttribute> comments;

    public List<TrackingIdAttribute> trackingIds;
    public PatchSetAttribute currentPatchSet;
    public List<PatchSetAttribute> patchSets;

    public List<DependencyAttribute> dependsOn;
    public List<DependencyAttribute> neededBy;
    public List<SubmitRecordAttribute> submitRecords;
    public List<AccountAttribute> allReviewers;
}
