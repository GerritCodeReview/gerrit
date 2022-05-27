/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CommentRange, NumericChangeId, PatchSetNum} from '../../types/common';
import {Finalizable} from '../registry';

export interface StorageLocation {
  changeNum: number;
  patchNum: PatchSetNum | '@change';
  path?: string;
  line?: number;
  range?: CommentRange;
}

export interface StorageObject {
  message?: string;
  updated: number;
}

export interface StorageService extends Finalizable {
  getDraftComment(location: StorageLocation): StorageObject | null;

  setDraftComment(location: StorageLocation, message: string): void;

  eraseDraftComment(location: StorageLocation): void;

  getEditableContentItem(key: string): StorageObject | null;

  setEditableContentItem(key: string, message: string): void;

  eraseEditableContentItem(key: string): void;

  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId): void;
}
