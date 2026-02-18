/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GitRef, LabelName} from '../types/common';

export enum AccessPermissionId {
  ABANDON = 'abandon',
  ADD_PATCH_SET = 'addPatchSet',
  CREATE = 'create',
  CREATE_TAG = 'createTag',
  CREATE_SIGNED_TAG = 'createSignedTag',
  DELETE = 'delete',
  DELETE_CHANGES = 'deleteChanges',
  DELETE_OWN_CHANGES = 'deleteOwnChanges',
  EDIT_HASHTAGS = 'editHashtags',
  EDIT_TOPIC_NAME = 'editTopicName',
  FORGE_AUTHOR = 'forgeAuthor',
  FORGE_COMMITTER = 'forgeCommitter',
  FORGE_SERVER_AS_COMMITTER = 'forgeServerAsCommitter',
  OWNER = 'owner',
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
  AI_REVIEW = 'aiReview',

  PRIORITY = 'priority',
}

export const AccessPermissions: {[id: string]: AccessPermission} = {
  [AccessPermissionId.ABANDON]: {
    id: AccessPermissionId.ABANDON,
    name: 'Abandon',
  },
  [AccessPermissionId.ADD_PATCH_SET]: {
    id: AccessPermissionId.ADD_PATCH_SET,
    name: 'Add Patch Set',
  },
  [AccessPermissionId.CREATE]: {
    id: AccessPermissionId.CREATE,
    name: 'Create Reference',
  },
  [AccessPermissionId.CREATE_TAG]: {
    id: AccessPermissionId.CREATE_TAG,
    name: 'Create Annotated Tag',
  },
  [AccessPermissionId.CREATE_SIGNED_TAG]: {
    id: AccessPermissionId.CREATE_SIGNED_TAG,
    name: 'Create Signed Tag',
  },
  [AccessPermissionId.DELETE]: {
    id: AccessPermissionId.DELETE,
    name: 'Delete Reference',
  },
  [AccessPermissionId.DELETE_CHANGES]: {
    id: AccessPermissionId.DELETE_CHANGES,
    name: 'Delete Changes',
  },
  [AccessPermissionId.DELETE_OWN_CHANGES]: {
    id: AccessPermissionId.DELETE_OWN_CHANGES,
    name: 'Delete Own Changes',
  },
  [AccessPermissionId.EDIT_HASHTAGS]: {
    id: AccessPermissionId.EDIT_HASHTAGS,
    name: 'Edit Hashtags',
  },
  [AccessPermissionId.EDIT_TOPIC_NAME]: {
    id: AccessPermissionId.EDIT_TOPIC_NAME,
    name: 'Edit Topic Name',
  },
  [AccessPermissionId.FORGE_AUTHOR]: {
    id: AccessPermissionId.FORGE_AUTHOR,
    name: 'Forge Author Identity',
  },
  [AccessPermissionId.FORGE_COMMITTER]: {
    id: AccessPermissionId.FORGE_COMMITTER,
    name: 'Forge Committer Identity',
  },
  [AccessPermissionId.FORGE_SERVER_AS_COMMITTER]: {
    id: AccessPermissionId.FORGE_SERVER_AS_COMMITTER,
    name: 'Forge Server Identity',
  },
  [AccessPermissionId.OWNER]: {
    id: AccessPermissionId.OWNER,
    name: 'Owner',
  },
  [AccessPermissionId.PUSH]: {
    id: AccessPermissionId.PUSH,
    name: 'Push',
  },
  [AccessPermissionId.PUSH_MERGE]: {
    id: AccessPermissionId.PUSH_MERGE,
    name: 'Push Merge Commit',
  },
  [AccessPermissionId.READ]: {
    id: AccessPermissionId.READ,
    name: 'Read',
  },
  [AccessPermissionId.REBASE]: {
    id: AccessPermissionId.REBASE,
    name: 'Rebase',
  },
  [AccessPermissionId.REVERT]: {
    id: AccessPermissionId.REVERT,
    name: 'Revert',
  },
  [AccessPermissionId.REMOVE_REVIEWER]: {
    id: AccessPermissionId.REMOVE_REVIEWER,
    name: 'Remove Reviewer',
  },
  [AccessPermissionId.SUBMIT]: {
    id: AccessPermissionId.SUBMIT,
    name: 'Submit',
  },
  [AccessPermissionId.SUBMIT_AS]: {
    id: AccessPermissionId.SUBMIT_AS,
    name: 'Submit (On Behalf Of)',
  },
  [AccessPermissionId.TOGGLE_WIP_STATE]: {
    id: AccessPermissionId.TOGGLE_WIP_STATE,
    name: 'Toggle Work In Progress State',
  },
  [AccessPermissionId.VIEW_PRIVATE_CHANGES]: {
    id: AccessPermissionId.VIEW_PRIVATE_CHANGES,
    name: 'View Private Changes',
  },
  [AccessPermissionId.AI_REVIEW]: {
    id: AccessPermissionId.AI_REVIEW,
    name: 'AI Review',
  },
};

export interface AccessPermission {
  id: AccessPermissionId;
  name: string;
  label?: LabelName;
}

export interface PermissionArrayItem<T> {
  id: GitRef;
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
        id: key as GitRef,
        value: obj[key],
      };
    })
    .sort((a, b) =>
      // Since IDs are strings, use localeCompare.
      a.id.localeCompare(b.id)
    );
}

/**
 * The category names are defined in `gerrit/Documentation/access-control.txt`.
 */
const DocsDocAnchors: Record<string, string> = {
  [AccessPermissionId.ABANDON]: 'category_abandon',
  [AccessPermissionId.ADD_PATCH_SET]: 'category_add_patch_set',
  [AccessPermissionId.CREATE]: 'category_create',
  [AccessPermissionId.CREATE_TAG]: 'category_create_annotated',
  [AccessPermissionId.CREATE_SIGNED_TAG]: 'category_create_signed',
  [AccessPermissionId.DELETE]: 'category_delete',
  [AccessPermissionId.DELETE_CHANGES]: 'category_delete_changes',
  [AccessPermissionId.DELETE_OWN_CHANGES]: 'category_delete_own_changes',
  [AccessPermissionId.EDIT_HASHTAGS]: 'category_edit_hashtags',
  [AccessPermissionId.EDIT_TOPIC_NAME]: 'category_edit_topic_name',
  [AccessPermissionId.FORGE_AUTHOR]: 'category_forge_author',
  [AccessPermissionId.FORGE_COMMITTER]: 'category_forge_committer',
  [AccessPermissionId.FORGE_SERVER_AS_COMMITTER]: 'category_forge_server',
  [AccessPermissionId.OWNER]: 'category_owner',
  [AccessPermissionId.PUSH]: 'category_push',
  [AccessPermissionId.PUSH_MERGE]: 'category_push_merge',
  [AccessPermissionId.READ]: 'category_read',
  [AccessPermissionId.REBASE]: 'category_rebase',
  [AccessPermissionId.REVERT]: 'category_revert',
  [AccessPermissionId.REMOVE_REVIEWER]: 'category_remove_reviewer',
  [AccessPermissionId.SUBMIT]: 'category_submit',
  [AccessPermissionId.SUBMIT_AS]: 'category_submit_on_behalf_of',
  [AccessPermissionId.TOGGLE_WIP_STATE]:
    'category_toggle_work_in_progress_state',
  [AccessPermissionId.VIEW_PRIVATE_CHANGES]: 'category_view_private_changes',
  [AccessPermissionId.AI_REVIEW]: 'category_ai_review',
};

export function getAccessDocsAnchor(permissionId: string): string | undefined {
  if (permissionId in DocsDocAnchors) {
    return DocsDocAnchors[permissionId];
  }
  if (
    permissionId.startsWith('label-') ||
    permissionId.startsWith('labelAs-')
  ) {
    return 'category_review_labels';
  }
  if (permissionId.startsWith('removeLabel-')) {
    return 'category_remove_label';
  }
  return undefined;
}
