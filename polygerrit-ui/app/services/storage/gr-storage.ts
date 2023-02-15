/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {NumericChangeId, PatchSetNumber} from '../../types/common';
import {Finalizable} from '../registry';

export interface StorageItem {
  updated: number;
}
export interface StoredContentItem extends StorageItem {
  message?: string;
}

export interface LatestPatchsetNumberSeenItem extends StorageItem {
  changeId: NumericChangeId;
  patchsetNumber: PatchSetNumber;
}

export interface StorageService extends Finalizable {
  getEditableContentItem(key: string): StoredContentItem | null;

  setEditableContentItem(key: string, message: string): void;

  eraseEditableContentItem(key: string): void;

  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId): void;

  setLatestPatchsetNumberSeen(
    changeId: NumericChangeId,
    patchsetNumber: PatchSetNumber
  ): void;

  getLatestPatchsetNumberSeen(
    changeId: NumericChangeId
  ): LatestPatchsetNumberSeenItem | null;
}
