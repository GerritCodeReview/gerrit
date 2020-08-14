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
  NumericChangeId,
  ServerInfo,
  ProjectInfo,
  ActionInfo,
  AccountCapabilityInfo,
  SuggestedReviewerInfo,
  GroupNameToGroupInfoMap,
  ParsedJSON,
  RequestPayload,
  PreferencesInput,
  SshKeyInfo,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {HttpMethod} from '../../../constants/constants';

export type ErrorCallback = (response?: Response | null, err?: Error) => void;
export type CancelConditionCallback = () => boolean;

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

export interface JsApiService {
  getElement<K extends keyof RestApiTagNameMap>(
    elementKey: K
  ): RestApiTagNameMap[K];
}

export interface RestApiService {
  // TODO(TS): unclear what is a second parameter. Looks like it is a mistake
  // and it must be removed
  dispatchEvent(event: Event, detail?: unknown): boolean;
  getConfig(noCache?: boolean): Promise<ServerInfo | undefined>;
  getLoggedIn(): Promise<boolean>;
  getVersion(): Promise<string | undefined>;
  invalidateReposCache(): void;
  getAccount(): Promise<AccountDetailInfo | undefined>;
  getAccountCapabilities(
    params?: string[]
  ): Promise<AccountCapabilityInfo | undefined>;
  getRepos(
    filter: string,
    reposPerPage: number,
    offset?: number
  ): Promise<ProjectInfo | undefined>;

  send(
    method: HttpMethod,
    url: string,
    body?: RequestPayload,
    errFn?: null | undefined,
    contentType?: string,
    headers?: Record<string, string>
  ): Promise<Response>;

  send(
    method: HttpMethod,
    url: string,
    body?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string,
    headers?: Record<string, string>
  ): Promise<Response | void>;

  getResponseObject(response: Response): Promise<ParsedJSON>;

  getChangeSuggestedReviewers(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[] | undefined>;
  getChangeSuggestedCCs(
    changeNum: NumericChangeId,
    input: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[] | undefined>;
  getSuggestedAccounts(
    input: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | undefined>;
  getSuggestedGroups(
    input: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<GroupNameToGroupInfoMap | undefined>;

  getChangeDetail(
    changeNum: number | string,
    opt_errFn?: Function,
    opt_cancelCondition?: Function
  ): Promise<ParsedChangeInfo | null | undefined>;

  savePreferences(prefs: PreferencesInput): Promise<Response>;
  getAccountSSHKeys(): Promise<SshKeyInfo[]>;
  deleteAccountSSHKey(key: string): void;
  addAccountSSHKey(key: string): Promise<SshKeyInfo>;
}
