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
import {ShowAlertEventDetail} from '../../../types/events';
import {PluginApi} from '../../../api/plugin';
import {UIActionInfo} from './gr-change-actions-js-api';
import {windowLocationReload} from '../../../utils/dom-util';
import {PopupPluginApi} from '../../../api/popup';
import {GrPopupInterface} from '../../plugins/gr-popup-interface/gr-popup-interface';
import {getAppContext} from '../../../services/app-context';

interface ButtonCallBacks {
  onclick: (event: Event) => boolean;
}

export class GrPluginActionContext {
  private popups: PopupPluginApi[] = [];

  private readonly reporting = getAppContext().reportingService;

  constructor(
    public readonly plugin: PluginApi,
    public readonly action: UIActionInfo,
    public readonly change: ChangeInfo,
    public readonly revision: RevisionInfo
  ) {}

  popup(element: Node) {
    this.plugin.popup().then(popApi => {
      const popupEl = (popApi as GrPopupInterface)._getElement();
      if (!popupEl) {
        throw new Error('Popup element not found');
      }
      popupEl.appendChild(element);
      this.popups.push(popApi);
    });
  }

  hide() {
    for (const popupApi of this.popups) {
      popupApi.close();
    }
    this.popups.splice(0);
  }

  refresh() {
    windowLocationReload();
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
      this.reporting.error(
        new Error(`Unable to ${this.action.method} to ${this.action.__key}!`)
      );
      return;
    }
    this.plugin
      .restApi()
      .send(this.action.method, this.action.__url, payload)
      .then(onSuccess)
      .catch((error: unknown) => {
        document.dispatchEvent(
          new CustomEvent<ShowAlertEventDetail>('show-alert', {
            detail: {
              message: `Plugin network error: ${error}`,
            },
          })
        );
      });
  }
}
