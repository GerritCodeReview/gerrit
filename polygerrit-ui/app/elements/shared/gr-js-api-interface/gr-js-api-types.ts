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
import {
  ActionInfo,
  ChangeInfo,
  PatchSetNum,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {EventType, TargetElement} from '../../../api/plugin';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api';
import {MenuLink} from '../../../api/admin';

export interface ShowChangeDetail {
  change: ChangeInfo;
  patchNum: PatchSetNum;
  info: {mergeable: boolean};
}

export interface ShowRevisionActionsDetail {
  change: ChangeInfo;
  revisionActions: {[key: string]: ActionInfo};
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type EventCallback = (...args: any[]) => any;

export interface JsApiService {
  getElement(key: TargetElement): HTMLElement;
  addEventCallback(eventName: EventType, callback: EventCallback): void;
  modifyRevertSubmissionMsg(
    change: ChangeInfo,
    revertSubmissionMsg: string,
    origMsg: string
  ): string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleEvent(eventName: EventType, detail: any): void;
  modifyRevertMsg(
    change: ChangeInfo,
    revertMsg: string,
    origMsg: string
  ): string;
  addElement(key: TargetElement, el: HTMLElement): void;
  getDiffLayers(path: string, changeNum: number): DiffLayer[];
  disposeDiffLayers(path: string): void;
  getCoverageAnnotationApis(): Promise<GrAnnotationActionsInterface[]>;
  getAdminMenuLinks(): MenuLink[];
  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string): void;
  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null): boolean;
  getReviewPostRevert(change?: ChangeInfo): ReviewInput;
}
