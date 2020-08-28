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
  GrChangeActions,
  ActionType,
  ActionPriority,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {ApiElement, JsApiService} from '../../plugins/gr-plugin-types';

interface Plugin {
  getPluginName(): string;
}

export class GrChangeActionsInterface {
  private _el?: GrChangeActions;
  // TODO(TS): define correct types when gr-change-actions is converted to ts

  RevisionActions?: Record<string, string>;

  ChangeActions?: Record<string, string>;

  ActionType?: Record<string, string>;

  constructor(public plugin: Plugin, el?: GrChangeActions) {
    this.setEl(el);
  }

  /**
   * Set gr-change-actions element to a GrChangeActionsInterface instance.
   */
  private setEl(el?: GrChangeActions) {
    if (!el) {
      console.warn('changeActions() is not ready');
      return;
    }
    this._el = el;
    this.RevisionActions = el.RevisionActions;
    this.ChangeActions = el.ChangeActions;
    this.ActionType = el.ActionType;
  }

  /**
   * Ensure GrChangeActionsInterface instance has access to gr-change-actions
   * element and retrieve if the interface was created before element.
   */
  private ensureEl(): GrChangeActions {
    if (!this._el) {
      const sharedApiElement = (document.createElement(
        'gr-js-api-interface'
      ) as unknown) as JsApiService;
      this.setEl(sharedApiElement.getElement(ApiElement.CHANGE_ACTIONS));
    }
    return this._el!;
  }

  addPrimaryActionKey(key: string) {
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

  setEnabled(key: string, enabled: string) {
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
