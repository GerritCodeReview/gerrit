/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileOverview This file contains interfaces shared between gr-repo-access
 * and nested elements (gr-access-section, gr-permission)
 */

import {
  AccessSectionInfo,
  GroupInfo,
  PermissionInfo,
  PermissionRuleInfo,
} from '../../../types/common';
import {PermissionArrayItem} from '../../../utils/access-util';

export type PrimitiveValue = string | boolean | number | undefined;

export interface PropertyTreeNode {
  [propName: string]: PropertyTreeNode | PrimitiveValue;
  deleted?: boolean;
  modified?: boolean;
  added?: boolean;
  updatedId?: string;
}

/**
 * EditableLocalAccessSectionInfo is exactly the same as LocalAccessSectionInfo,
 * but with additional properties: each nested object additionally implements
 * interface PropertyTreeNode
 */

export type EditableLocalAccessSectionInfo = {
  [ref: string]: EditableAccessSectionInfo;
};

export interface EditableAccessSectionInfo
  extends AccessSectionInfo,
    PropertyTreeNode {
  permissions: EditableAccessPermissionsMap;
}

export type EditableAccessPermissionsMap = {
  [permissionName: string]: EditablePermissionInfo;
};

export interface EditablePermissionInfo
  extends PermissionInfo,
    PropertyTreeNode {
  rules: EditablePermissionInfoRules;
}

export type EditablePermissionInfoRules = {
  [groupUUID: string]: EditablePermissionRuleInfo;
};

export interface EditablePermissionRuleInfo
  extends PermissionRuleInfo,
    PropertyTreeNode {}

export type PermissionAccessSection = PermissionArrayItem<EditableAccessSectionInfo>;

export interface NewlyAddedGroupInfo {
  name: string;
}
export type EditableProjectAccessGroups = {
  [uuid: string]: GroupInfo | NewlyAddedGroupInfo;
};
