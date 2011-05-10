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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface RefRightAccess extends Access<RefRight, RefRight.Key> {
  @PrimaryKey("key")
  RefRight get(RefRight.Key refRight) throws OrmException;

  @Query("WHERE key.projectName = ?")
  ResultSet<RefRight> byProject(Project.NameKey project) throws OrmException;

  @Query("WHERE key.groupId = ?")
  ResultSet<RefRight> byGroup(AccountGroup.Id group) throws OrmException;

  @Query("WHERE key.categoryId = ? AND key.groupId = ?")
  ResultSet<RefRight> byCategoryGroup(ApprovalCategory.Id cat,
      AccountGroup.Id group) throws OrmException;
}
