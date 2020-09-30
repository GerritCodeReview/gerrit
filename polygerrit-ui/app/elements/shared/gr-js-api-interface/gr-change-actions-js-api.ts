/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
  ActionType,
  ActionPriority,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {JsApiService} from './gr-js-api-types';
import {TargetElement} from '../../plugins/gr-plugin-types';
import {ActionInfo, RequireProperties} from '../../../types/common';

interface Plugin {
  getPluginName(): string;
}

export enum ChangeActions {
  ABANDON = 'abandon',
  DELETE = '/',
  DELETE_EDIT = 'deleteEdit',
  EDIT = 'edit',
  FOLLOW_UP = 'followup',
  IGNORE = 'ignore',
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
  UNIGNORE = 'unignore',
  UNREVIEWED = 'unreviewed',
  WIP = 'wip',
}

export enum RevisionActions {
  CHERRYPICK = 'cherrypick',
  REBASE = 'rebase',
  SUBMIT = 'submit',
  DOWNLOAD = 'download',
}

export type PrimaryActionKey = ChangeActions | RevisionActions;

export interface UIActionInfo extends RequireProperties<ActionInfo, 'label'> {
  __key: string;
  __url?: string;
  __primary?: boolean;
  __type: ActionType;
  icon?: string;
}

// This interface is required to avoid circular dependencies between files;
export interface GrChangeActionsElement extends Element {
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
  setActionButtonProp<T extends keyof UIActionInfo>(
    key: string,
    prop: T,
    value: UIActionInfo[T]
  ): void;
  getActionDetails(actionName: string): ActionInfo | undefined;
}

export class GrChangeActionsInterface {
  private _el?: GrChangeActionsElement;

  RevisionActions = RevisionActions;

  ChangeActions = ChangeActions;

  ActionType = ActionType;

  constructor(public plugin: Plugin, el?: GrChangeActionsElement) {
    this.setEl(el);
  }

  /**
   * Set gr-change-actions element to a GrChangeActionsInterface instance.
   */
  private setEl(el?: GrChangeActionsElement) {
    if (!el) {
      console.warn('changeActions() is not ready');
      return;
    }
    this._el = el;
  }

  /**
   * Ensure GrChangeActionsInterface instance has access to gr-change-actions
   * element and retrieve if the interface was created before element.
   */
  private ensureEl(): GrChangeActionsElement {
    if (!this._el) {
      const sharedApiElement = (document.createElement(
        'gr-js-api-interface'
      ) as unknown) as JsApiService;
      this.setEl(
        (sharedApiElement.getElement(
          TargetElement.CHANGE_ACTIONS
        ) as unknown) as GrChangeActionsElement
      );
    }
    return this._el!;
  }

  addPrimaryActionKey(key: PrimaryActionKey) {
    const el = this.ensureEl();
    if (el.primaryActionKeys.includes(key)) {
      return;
    }

    el.push('primaryActionKeys', key);
  }

  removePrimaryActionKey(key: string) {
    const el = this.ensureEl();
    el.primaryActionKeys = el.primaryActionKeys.filter(k => k !== key);
  }

  hideQuickApproveAction() {
    this.ensureEl().hideQuickApproveAction();
  }

  setActionOverflow(type: ActionType, key: string, overflow: boolean) {
    // TODO(TS): remove return, unclear why it was written
    return this.ensureEl().setActionOverflow(type, key, overflow);
  }

  setActionPriority(type: ActionType, key: string, priority: ActionPriority) {
    // TODO(TS): remove return, unclear why it was written
    return this.ensureEl().setActionPriority(type, key, priority);
  }

  setActionHidden(type: ActionType, key: string, hidden: boolean) {
    // TODO(TS): remove return, unclear why it was written
    return this.ensureEl().setActionHidden(type, key, hidden);
  }

  add(type: ActionType, label: string): string {
    return this.ensureEl().addActionButton(type, label);
  }

  remove(key: string) {
    // TODO(TS): remove return, unclear why it was written
    return this.ensureEl().removeActionButton(key);
  }

  addTapListener(key: string, handler: EventListenerOrEventListenerObject) {
    this.ensureEl().addEventListener(key + '-tap', handler);
  }

  removeTapListener(key: string, handler: EventListenerOrEventListenerObject) {
    this.ensureEl().removeEventListener(key + '-tap', handler);
  }

  setLabel(key: string, text: string) {
    this.ensureEl().setActionButtonProp(key, 'label', text);
  }

  setTitle(key: string, text: string) {
    this.ensureEl().setActionButtonProp(key, 'title', text);
  }

  setEnabled(key: string, enabled: boolean) {
    this.ensureEl().setActionButtonProp(key, 'enabled', enabled);
  }

  setIcon(key: string, icon: string) {
    this.ensureEl().setActionButtonProp(key, 'icon', icon);
  }

  getActionDetails(action: string) {
    const el = this.ensureEl();
    return (
      el.getActionDetails(action) ||
      el.getActionDetails(this.plugin.getPluginName() + '~' + action)
    );
  }
}
