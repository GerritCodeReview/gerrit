/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ActionInfo,
  BasePatchSetNum,
  ChangeInfo,
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
  /**
   * This method is called before handling a change action.
   * It allows plugins to conditionally block actions.
   * @param key The change action key.
   * @param change The relevant change.
   * @return A promise that resolves to true if the action should proceed.
   */
  handleBeforeChangeAction(key: string, change?: ChangeInfo): Promise<boolean>;
  /**
   * This method is called before publishing a change edit.
   * It allows plugins to conditionally block edits.
   * @param change The relevant change.
   * @return A promise that resolves to true if the action should proceed.
   */
  handleBeforePublishEdit(change: ChangeInfo): Promise<boolean>;
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
  /**
   * This method is called before handling a commit message edit.
   * It allows plugins to conditionally block edits.
   * @param change The relevant change.
   * @param msg The new commit message text.
   * @return A promise that resolves to true if the action should proceed.
   */
  handleBeforeCommitMessage(
    change: ChangeInfo | ParsedChangeInfo,
    msg: string
  ): Promise<boolean>;
  handleCommitMessage(change: ChangeInfo | ParsedChangeInfo, msg: string): void;
  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null): boolean;
  getReviewPostRevert(change?: ChangeInfo): ReviewInput;
  handleShowDiff(detail: ShowDiffDetail): void;
  /**
   * This method is called before a reply to a change is sent.
   * It allows plugins to modify the review input or to block sending.
   * @param change The change the reply is on.
   * @param reviewInput The review input that is about to be sent.
   * @return A promise that resolves to true if the reply should be sent.
   */
  handleBeforeReplySent(
    change: ChangeInfo | ParsedChangeInfo,
    reviewInput: ReviewInput
  ): Promise<boolean>;
  handleReplySent(): Promise<void>;
}
