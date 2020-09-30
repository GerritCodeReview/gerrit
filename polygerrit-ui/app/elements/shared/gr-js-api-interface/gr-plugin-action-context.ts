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

import {RevisionInfo, ChangeInfo, RequestPayload} from '../../../types/common';
import {PluginApi} from '../../plugins/gr-plugin-types';
import {UIActionInfo} from './gr-change-actions-js-api';

interface GrPopupInterface {
  close(): void;
}

interface ButtonCallBacks {
  onclick: (event: Event) => boolean;
}

export class GrPluginActionContext {
  private _popups: GrPopupInterface[] = [];

  constructor(
    public readonly plugin: PluginApi,
    public readonly action: UIActionInfo,
    public readonly change: ChangeInfo,
    public readonly revision: RevisionInfo
  ) {}

  popup(element: Node) {
    this.plugin.popup().then(popApi => {
      const popupEl = popApi._getElement();
      if (!popupEl) {
        throw new Error('Popup element not found');
      }
      popupEl.appendChild(element);
      this._popups.push(popApi);
    });
  }

  hide() {
    for (const popupApi of this._popups) {
      popupApi.close();
    }
    this._popups.splice(0);
  }

  refresh() {
    window.location.reload();
  }

  textfield(): HTMLElement {
    return document.createElement('paper-input');
  }

  br() {
    return document.createElement('br');
  }

  msg(text: string) {
    const label = document.createElement('gr-label');
    label.appendChild(document.createTextNode(text));
    return label;
  }

  div(...els: Node[]) {
    const div = document.createElement('div');
    for (const el of els) {
      div.appendChild(el);
    }
    return div;
  }

  button(label: string, callbacks: ButtonCallBacks | undefined) {
    const onClick = callbacks && callbacks.onclick;
    const button = document.createElement('gr-button');
    button.appendChild(document.createTextNode(label));
    if (onClick) {
      this.plugin.eventHelper(button).onTap(onClick);
    }
    return button;
  }

  checkbox() {
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    return checkbox;
  }

  label(checkbox: Node, title: string) {
    return this.div(checkbox, this.msg(title));
  }

  prependLabel(title: string, checkbox: Node) {
    return this.label(checkbox, title);
  }

  call(payload: RequestPayload, onSuccess: (result: unknown) => void) {
    if (!this.action.method) return;
    if (!this.action.__url) {
      console.warn(`Unable to ${this.action.method} to ${this.action.__key}!`);
      return;
    }
    this.plugin
      .restApi()
      .send(this.action.method, this.action.__url, payload)
      .then(onSuccess)
      .catch((error: unknown) => {
        document.dispatchEvent(
          new CustomEvent('show-alert', {
            detail: {
              message: `Plugin network error: ${error}`,
            },
          })
        );
      });
  }
}
