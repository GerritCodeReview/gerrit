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
import {ParsedChangeInfo} from '../../../types/types';
import {MenuLink} from '../../../api/admin';

export interface ShowChangeDetail {
  change?: ParsedChangeInfo;
  patchNum?: PatchSetNum;
  info: {mergeable: boolean | null};
}

export interface ShowRevisionActionsDetail {
  change: ChangeInfo;
  revisionActions: {[key: string]: ActionInfo | undefined};
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
  handleShowChange(detail: ShowChangeDetail): void;
  handleShowRevisionActions(detail: ShowRevisionActionsDetail): void;
  handleLabelChange(detail: {change?: ParsedChangeInfo}): void;
  modifyRevertMsg(
    change: ChangeInfo,
    revertMsg: string,
    origMsg: string
  ): string;
  addElement(key: TargetElement, el: HTMLElement): void;
  getAdminMenuLinks(): MenuLink[];
  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string): void;
  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null): boolean;
  getReviewPostRevert(change?: ChangeInfo): ReviewInput;
}
