/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ActionInfo,
  ChangeInfo,
  PatchSetNum,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {Finalizable} from '../../../services/registry';
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

export interface JsApiService extends Finalizable {
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
  getDiffLayers(path: string): DiffLayer[];
  disposeDiffLayers(path: string): void;
  getCoverageAnnotationApis(): Promise<GrAnnotationActionsInterface[]>;
  getAdminMenuLinks(): MenuLink[];
  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string): void;
  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null): boolean;
  getReviewPostRevert(change?: ChangeInfo): ReviewInput;
}
