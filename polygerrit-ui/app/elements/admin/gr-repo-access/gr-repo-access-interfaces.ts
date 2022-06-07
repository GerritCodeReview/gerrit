/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * @fileoverview This file contains interfaces shared between gr-repo-access
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

export type PermissionAccessSection =
  PermissionArrayItem<EditableAccessSectionInfo>;

export interface NewlyAddedGroupInfo {
  name: string;
}
export type EditableProjectAccessGroups = {
  [uuid: string]: GroupInfo | NewlyAddedGroupInfo;
};
