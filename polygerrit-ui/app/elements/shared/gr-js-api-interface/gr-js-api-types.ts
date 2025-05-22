/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ActionInfo,
  ChangeInfo,
  BasePatchSetNum,
  PatchSetNum,
  ReviewInput,
  RevisionInfo,
} from '../../../types/common';
import {EventType, TargetElement} from '../../../api/plugin';
import {Finalizable, ParsedChangeInfo} from '../../../types/types';
import {MenuLink} from '../../../api/admin';
import {FileRange, PatchRange} from '../../../api/diff';
import {EmojiSuggestion} from '../gr-suggestion-textarea/gr-suggestion-textarea';

export interface ShowChangeDetail {
  change?: ParsedChangeInfo;
  basePatchNum?: BasePatchSetNum;
  patchNum?: PatchSetNum;
  info: {mergeable: boolean | null};
}

export interface ShowDiffDetail {
  change: ChangeInfo;
  patchRange: PatchRange;
  fileRange: FileRange;
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
  handlePublishEdit(change: ChangeInfo, revision?: RevisionInfo | null): void;
  handleShowChange(detail: ShowChangeDetail): Promise<void>;
  handleShowRevisionActions(detail: ShowRevisionActionsDetail): void;
  handleLabelChange(detail: {change?: ParsedChangeInfo}): void;
  handleViewChange(view?: string): void;
  modifyEmojis(emojis?: EmojiSuggestion[]): EmojiSuggestion[] | undefined;
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
  handleShowDiff(detail: ShowDiffDetail): void;
  handleReplySent(): Promise<void>;
}
