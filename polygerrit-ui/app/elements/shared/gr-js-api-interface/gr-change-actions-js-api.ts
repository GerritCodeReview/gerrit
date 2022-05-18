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
import {PluginApi, TargetElement} from '../../../api/plugin';
import {ActionInfo, RequireProperties} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {
  ActionPriority,
  ActionType,
  ChangeActions,
  ChangeActionsPluginApi,
  PrimaryActionKey,
  RevisionActions,
} from '../../../api/change-actions';
import {PropertyDeclaration} from 'lit';

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
  requestUpdate(
    name?: PropertyKey,
    oldValue?: unknown,
    options?: PropertyDeclaration
  ): void;
}

export class GrChangeActionsInterface implements ChangeActionsPluginApi {
  private el?: GrChangeActionsElement;

  RevisionActions = RevisionActions;

  ChangeActions = ChangeActions;

  ActionType = ActionType;

  private readonly reporting = getAppContext().reportingService;

  private readonly jsApiService = getAppContext().jsApiService;

  constructor(public plugin: PluginApi, el?: GrChangeActionsElement) {
    this.reporting.trackApi(this.plugin, 'actions', 'constructor');
    this.setEl(el);
  }

  /**
   * Set gr-change-actions element to a GrChangeActionsInterface instance.
   */
  private setEl(el?: GrChangeActionsElement) {
    if (!el) {
      this.reporting.error(new Error('changeActions() API is not ready'));
      return;
    }
    this.el = el;
  }

  /**
   * Ensure GrChangeActionsInterface instance has access to gr-change-actions
   * element and retrieve if the interface was created before element.
   */
  ensureEl(): GrChangeActionsElement {
    if (!this.el) {
      const sharedApiElement = this.jsApiService;
      this.setEl(
        sharedApiElement.getElement(
          TargetElement.CHANGE_ACTIONS
        ) as unknown as GrChangeActionsElement
      );
    }
    return this.el!;
  }

  addPrimaryActionKey(key: PrimaryActionKey) {
    this.reporting.trackApi(this.plugin, 'actions', 'addPrimaryActionKey');
    const el = this.ensureEl();
    if (el.primaryActionKeys.includes(key)) {
      return;
    }

    el.primaryActionKeys.push(key);
    el.requestUpdate();
  }

  removePrimaryActionKey(key: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'removePrimaryActionKey');
    const el = this.ensureEl();
    el.primaryActionKeys = el.primaryActionKeys.filter(k => k !== key);
  }

  hideQuickApproveAction() {
    this.reporting.trackApi(this.plugin, 'actions', 'hideQuickApproveAction');
    this.ensureEl().hideQuickApproveAction();
  }

  setActionOverflow(type: ActionType, key: string, overflow: boolean) {
    this.reporting.trackApi(this.plugin, 'actions', 'setActionOverflow');
    this.ensureEl().setActionOverflow(type, key, overflow);
  }

  setActionPriority(type: ActionType, key: string, priority: ActionPriority) {
    this.reporting.trackApi(this.plugin, 'actions', 'setActionPriority');
    this.ensureEl().setActionPriority(type, key, priority);
  }

  setActionHidden(type: ActionType, key: string, hidden: boolean) {
    this.reporting.trackApi(this.plugin, 'actions', 'setActionHidden');
    this.ensureEl().setActionHidden(type, key, hidden);
  }

  add(type: ActionType, label: string): string {
    this.reporting.trackApi(this.plugin, 'actions', 'add');
    return this.ensureEl().addActionButton(type, label);
  }

  remove(key: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'remove');
    this.ensureEl().removeActionButton(key);
  }

  addTapListener(key: string, handler: EventListenerOrEventListenerObject) {
    this.reporting.trackApi(this.plugin, 'actions', 'addTapListener');
    this.ensureEl().addEventListener(key + '-tap', handler);
  }

  removeTapListener(key: string, handler: EventListenerOrEventListenerObject) {
    this.reporting.trackApi(this.plugin, 'actions', 'removeTapListener');
    this.ensureEl().removeEventListener(key + '-tap', handler);
  }

  setLabel(key: string, text: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'setLabel');
    this.ensureEl().setActionButtonProp(key, 'label', text);
  }

  setTitle(key: string, text: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'setTitle');
    this.ensureEl().setActionButtonProp(key, 'title', text);
  }

  setEnabled(key: string, enabled: boolean) {
    this.reporting.trackApi(this.plugin, 'actions', 'setEnabled');
    this.ensureEl().setActionButtonProp(key, 'enabled', enabled);
  }

  setIcon(key: string, icon: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'setIcon');
    this.ensureEl().setActionButtonProp(key, 'icon', icon);
  }

  getActionDetails(action: string) {
    this.reporting.trackApi(this.plugin, 'actions', 'getActionDetails');
    const el = this.ensureEl();
    return (
      el.getActionDetails(action) ||
      el.getActionDetails(this.plugin.getPluginName() + '~' + action)
    );
  }
}
