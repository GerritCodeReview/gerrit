/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HttpMethod} from './rest';
import {ChangeInfo} from './rest-api';

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

// This is used for sorting the actions, BUT:
// * For showing up as a dedicated button the action must not be hidden and not
//   be an overflow action. See setActionOverflow() and setActionHidden().
// * All primary actions are shown left of all secondary actions. By default
//   the primary actions are: "Submit" and "Mark as active".
//
// Also note that a LOWER value means HIGHER priority!
export enum ActionPriority {
  CHANGE = 3,
  // Only "Submit" and "Code-Review" buttons should show before "Chat".
  CHAT = 1,
  DEFAULT = 0,
  // This is a bit confusing, because this is the LOWEST priority in the list.
  // But it does not matter much, because the `primary` property is evaluated
  // first, and then the `priority` does not matter anymore.
  PRIMARY = 4,
  // This means that the "Code-Review" voting button is the left most button,
  // if there are no primary actions.
  REVIEW = -3,
  REVISION = 2,
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

  /** Notify BEFORE_CHANGE_ACTION event handlers.
   *
   * If a plugin replaces any default change actions (e.g., the quick
   * approve action), it should call this method so that any event
   * handlers for that action still trigger.
   *
   * The returned value is true if the action should proceed.
   */
  notifyBeforeChangeAction(key: string, change?: ChangeInfo): Promise<boolean>;
}
