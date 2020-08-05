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
  AccountInfo,
  GroupBaseInfo,
  NumericChangeId,
  ServerInfo,
} from '../../../types/common';

export type ErrorCallback = (response?: Response | null, err?: Error) => void;

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

export interface RestApiService {
  // TODO(TS): unclear what is a second parameter. Looks like it is a mistake
  // and it must be removed
  dispatchEvent(event: Event, detail?: unknown): boolean;
  getConfig(): Promise<ServerInfo>;
  getLoggedIn(): Promise<boolean>;
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
}
