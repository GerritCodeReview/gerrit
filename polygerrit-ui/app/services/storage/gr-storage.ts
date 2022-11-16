/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {NumericChangeId} from '../../types/common';
import {Finalizable} from '../registry';

export interface StorageObject {
  message?: string;
  updated: number;
}

export interface StorageService extends Finalizable {
  getEditableContentItem(key: string): StorageObject | null;

  setEditableContentItem(key: string, message: string): void;

  eraseEditableContentItem(key: string): void;

  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId): void;
}
