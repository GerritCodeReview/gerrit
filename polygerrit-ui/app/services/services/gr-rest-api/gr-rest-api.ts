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
  AccountDetailInfo,
  AccountInfo,
  CapabilityInfo,
  GroupBaseInfo,
  NumericChangeId,
  ServerInfo,
  ProjectInfo,
  ActionInfo,
  GroupInfo,
  ChangeInfo,
} from '../../../types/common';

export type ErrorCallback = (response?: Response | null, err?: Error) => void;
export type CancelConditionCallback = () => boolean;

/**
 * Contains information about an account that can be added to a change
 */
export interface SuggestedReviewerAccountInfo {
  account: AccountInfo;
  /**
   * The total number of accounts in the suggestion - always 1
   */
  count: 1;
}

/**
 * Contains information about a group that can be added to a change
 */
export interface SuggestedReviewerGroupInfo {
  group: GroupBaseInfo;
  /**
   * The total number of accounts that are members of the group is returned
   * (this count includes members of nested groups)
   */
  count: number;
  /**
   * True if group is present and count is above the threshold where the
   * confirmed flag must be passed to add the group as a reviewer
   */
  confirm?: boolean;
}

/**
 * Contains information about a reviewer that can be added to a change
 */
export type SuggestedReviewerInfo =
  | SuggestedReviewerAccountInfo
  | SuggestedReviewerGroupInfo;

export enum ApiElement {
  CHANGE_ACTIONS = 'changeactions',
  REPLY_DIALOG = 'replydialog',
}

// TODO(TS): remove when GrReplyDialog converted to typescript
export interface GrReplyDialog {
  getLabelValue(label: string): string;
  setLabelValue(label: string, value: string): void;
  send(includeComments?: boolean, startReview?: boolean): Promise<unknown>;
  setPluginMessage(message: string): void;
}

// Copied from gr-change-actions.js
export enum ActionType {
  CHANGE = 'change',
  REVISION = 'revision',
}

// Copied from gr-change-actions.js
export enum ActionPriority {
  CHANGE = 2,
  DEFAULT = 0,
  PRIMARY = 3,
  REVIEW = -3,
  REVISION = 1,
}

// TODO(TS) remove interface when GrChangeActions is converted to typescript
export interface GrChangeActions extends Element {
  RevisionActions?: Record<string, string>;
  ChangeActions: Record<string, string>;
  ActionType: Record<string, string>;
  primaryActionKeys: string[];
  push(propName: 'primaryActionKeys', value: string): void;
  hideQuickApproveAction(): void;
  setActionOverflow(type: ActionType, key: string, overflow: boolean): void;
  setActionPriority(
    type: ActionType,
    key: string,
    overflow: ActionPriority
  ): void;
  setActionHidden(type: ActionType, key: string, hidden: boolean): void;
  addActionButton(type: ActionType, label: string): string;
  removeActionButton(key: string): void;
  setActionButtonProp(key: string, prop: string, value: string): void;
  getActionDetails(actionName: string): ActionInfo;
}

export interface RestApiTagNameMap {
  [ApiElement.REPLY_DIALOG]: GrReplyDialog;
  [ApiElement.CHANGE_ACTIONS]: GrChangeActions;
}

export interface RestApiService {
  // TODO(TS): unclear what is a second parameter. Looks like it is a mistake
  // and it must be removed
  dispatchEvent(event: Event, detail?: unknown): boolean;
  getConfig(): Promise<ServerInfo>;
  getLoggedIn(): Promise<boolean>;
  getVersion(): Promise<string>;
  invalidateReposCache(): void;
  getAccount(): Promise<AccountDetailInfo>;
  getAccountCapabilities(params?: string[]): Promise<CapabilityInfo>;
  getRepos(
    filter: string,
    reposPerPage: number,
    offset?: number
  ): Promise<ProjectInfo>;
  send(
    method: string,
    url: string,
    body?: unknown,
    errFn?: ErrorCallback,
    contentType?: string,
    headers?: unknown
  ): Promise<Response>;

  getResponseObject(response: Response): null | unknown;

  getChangeSuggestedReviewers(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[]>;
  getChangeSuggestedCCs(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[]>;
  getSuggestedAccounts(
    input: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[]>;
  getSuggestedGroups(
    input: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<Record<string, GroupInfo>>;

  getElement<K extends keyof RestApiTagNameMap>(
    elementKey: K
  ): RestApiTagNameMap[K];
  getChangeDetail(
    changeNum: number | string,
    opt_errFn?: Function,
    opt_cancelCondition?: Function
  ): Promise<ChangeInfo>;
}
