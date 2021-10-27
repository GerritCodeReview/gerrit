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
import {HttpMethod} from './rest';

export declare interface ActionInfo {
  method?: HttpMethod;
  label?: string;
  title?: string;
  enabled?: boolean;
}

export enum ActionType {
  CHANGE = 'change',
  REVISION = 'revision',
}

export enum ActionPriority {
  CHANGE = 2,
  DEFAULT = 0,
  PRIMARY = 3,
  REVIEW = -3,
  REVISION = 1,
}

export enum ChangeActions {
  ABANDON = 'abandon',
  DELETE = '/',
  DELETE_EDIT = 'deleteEdit',
  EDIT = 'edit',
  FOLLOW_UP = 'followup',
  MOVE = 'move',
  PRIVATE = 'private',
  PRIVATE_DELETE = 'private.delete',
  PUBLISH_EDIT = 'publishEdit',
  REBASE = 'rebase',
  REBASE_EDIT = 'rebaseEdit',
  READY = 'ready',
  RESTORE = 'restore',
  REVERT = 'revert',
  REVERT_SUBMISSION = 'revert_submission',
  REVIEWED = 'reviewed',
  STOP_EDIT = 'stopEdit',
  SUBMIT = 'submit',
  UNREVIEWED = 'unreviewed',
  WIP = 'wip',
  INCLUDED_IN = 'includedIn',
}

export enum RevisionActions {
  CHERRYPICK = 'cherrypick',
  REBASE = 'rebase',
  SUBMIT = 'submit',
  DOWNLOAD = 'download',
}

export type PrimaryActionKey = ChangeActions | RevisionActions;

export declare interface ChangeActionsPluginApi {
  // Deprecated. This API method will be removed.
  ensureEl(): Element;

  addPrimaryActionKey(key: PrimaryActionKey): void;

  removePrimaryActionKey(key: string): void;

  hideQuickApproveAction(): void;

  setActionOverflow(type: ActionType, key: string, overflow: boolean): void;

  setActionPriority(
    type: ActionType,
    key: string,
    priority: ActionPriority
  ): void;

  setActionHidden(type: ActionType, key: string, hidden: boolean): void;

  add(type: ActionType, label: string): string;

  remove(key: string): void;

  addTapListener(
    key: string,
    handler: EventListenerOrEventListenerObject
  ): void;

  removeTapListener(
    key: string,
    handler: EventListenerOrEventListenerObject
  ): void;

  setLabel(key: string, text: string): void;

  setTitle(key: string, text: string): void;

  setEnabled(key: string, enabled: boolean): void;

  setIcon(key: string, icon: string): void;

  getActionDetails(action: string): ActionInfo | undefined;
}
