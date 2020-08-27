/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {LabelName} from '../types/common';

export enum AccessPermissionId {
  ABANDON = 'abandon',
  ADD_PATCH_SET = 'addPatchSet',
  CREATE = 'create',
  CREATE_TAG = 'createTag',
  CREATE_SIGNED_TAG = 'createSignedTag',
  DELETE = 'delete',
  DELETE_CHANGES = 'deleteChanges',
  DELETE_OWN_CHANGES = 'deleteOwnChanges',
  EDIT_ASSIGNEE = 'editAssignee',
  EDIT_HASHTAGS = 'editHashtags',
  EDIT_TOPIC_NAME = 'editTopicName',
  FORGE_AUTHOR = 'forgeAuthor',
  FORGE_COMMITTER = 'forgeCommitter',
  FORGE_SERVER_AS_COMMITTER = 'forgeServerAsCommitter',
  OWNER = 'owner',
  PUBLISH_DRAFTS = 'publishDrafts',
  PUSH = 'push',
  PUSH_MERGE = 'pushMerge',
  READ = 'read',
  REBASE = 'rebase',
  REVERT = 'revert',
  REMOVE_REVIEWER = 'removeReviewer',
  SUBMIT = 'submit',
  SUBMIT_AS = 'submitAs',
  TOGGLE_WIP_STATE = 'toggleWipState',
  VIEW_PRIVATE_CHANGES = 'viewPrivateChanges',

  PRIORITY = 'priority',
}

export const AccessPermissions = {
  abandon: {
    id: AccessPermissionId.ABANDON,
    name: 'Abandon',
  },
  addPatchSet: {
    id: AccessPermissionId.ADD_PATCH_SET,
    name: 'Add Patch Set',
  },
  create: {
    id: AccessPermissionId.CREATE,
    name: 'Create Reference',
  },
  createTag: {
    id: AccessPermissionId.CREATE_TAG,
    name: 'Create Annotated Tag',
  },
  createSignedTag: {
    id: AccessPermissionId.CREATE_SIGNED_TAG,
    name: 'Create Signed Tag',
  },
  delete: {
    id: AccessPermissionId.DELETE,
    name: 'Delete Reference',
  },
  deleteChanges: {
    id: AccessPermissionId.DELETE_CHANGES,
    name: 'Delete Changes',
  },
  deleteOwnChanges: {
    id: AccessPermissionId.DELETE_OWN_CHANGES,
    name: 'Delete Own Changes',
  },
  editAssignee: {
    id: AccessPermissionId.EDIT_ASSIGNEE,
    name: 'Edit Assignee',
  },
  editHashtags: {
    id: AccessPermissionId.EDIT_HASHTAGS,
    name: 'Edit Hashtags',
  },
  editTopicName: {
    id: AccessPermissionId.EDIT_TOPIC_NAME,
    name: 'Edit Topic Name',
  },
  forgeAuthor: {
    id: AccessPermissionId.FORGE_AUTHOR,
    name: 'Forge Author Identity',
  },
  forgeCommitter: {
    id: AccessPermissionId.FORGE_COMMITTER,
    name: 'Forge Committer Identity',
  },
  forgeServerAsCommitter: {
    id: AccessPermissionId.FORGE_SERVER_AS_COMMITTER,
    name: 'Forge Server Identity',
  },
  owner: {
    id: AccessPermissionId.OWNER,
    name: 'Owner',
  },
  publishDrafts: {
    id: AccessPermissionId.PUBLISH_DRAFTS,
    name: 'Publish Drafts',
  },
  push: {
    id: AccessPermissionId.PUSH,
    name: 'Push',
  },
  pushMerge: {
    id: AccessPermissionId.PUSH_MERGE,
    name: 'Push Merge Commit',
  },
  read: {
    id: AccessPermissionId.READ,
    name: 'Read',
  },
  rebase: {
    id: AccessPermissionId.REBASE,
    name: 'Rebase',
  },
  revert: {
    id: AccessPermissionId.REVERT,
    name: 'Revert',
  },
  removeReviewer: {
    id: AccessPermissionId.REMOVE_REVIEWER,
    name: 'Remove Reviewer',
  },
  submit: {
    id: AccessPermissionId.SUBMIT,
    name: 'Submit',
  },
  submitAs: {
    id: AccessPermissionId.SUBMIT_AS,
    name: 'Submit (On Behalf Of)',
  },
  toggleWipState: {
    id: AccessPermissionId.TOGGLE_WIP_STATE,
    name: 'Toggle Work In Progress State',
  },
  viewPrivateChanges: {
    id: AccessPermissionId.VIEW_PRIVATE_CHANGES,
    name: 'View Private Changes',
  },
};

export interface AccessPermission {
  id: AccessPermissionId;
  name: string;
  label?: LabelName;
}

export interface PermissionArrayItem<T> {
  id: string;
  value: T;
}

export type PermissionArray<T> = Array<PermissionArrayItem<T>>;

/**
 * @return a sorted array sorted by the id of the original
 *    object.
 */
export function toSortedPermissionsArray<T>(obj?: {
  [permissionId: string]: T;
}): PermissionArray<T> {
  if (!obj) {
    return [];
  }
  return Object.keys(obj)
    .map(key => {
      return {
        id: key,
        value: obj[key],
      };
    })
    .sort((a, b) =>
      // Since IDs are strings, use localeCompare.
      a.id.localeCompare(b.id)
    );
}
