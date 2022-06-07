/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface GrEditAction {
  label: string;
  id: string;
}

export const GrEditConstants = {
  // Order corresponds to order in the UI.
  Actions: {
    OPEN: {label: 'Add/Open/Upload', id: 'open'},
    DELETE: {label: 'Delete', id: 'delete'},
    RENAME: {label: 'Rename', id: 'rename'},
    RESTORE: {label: 'Restore', id: 'restore'},
  },
};
