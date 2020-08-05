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

import {GrPluginRestApi, HttpMethod} from './gr-plugin-rest-api';
import {GrEventHelper} from '../../plugins/gr-event-helper/gr-event-helper';

interface PluginApi {
  restApi(): GrPluginRestApi;
  deprecated: PluginDeprecatedApi;
  eventHelper(element: Node): GrEventHelper;
}
interface PluginDeprecatedApi {
  popup(element: Node): GrPopupInterface;
}

interface GrPopupInterface {
  close(): void;
}

interface ButtonCallBacks {
  onclick: (event: Event) => boolean;
}

interface ActionInterface {
  method: HttpMethod;
  __url: string;
  __key: string;
}

export class GrPluginActionContext {
  private _popups: GrPopupInterface[] = [];

  constructor(
    private readonly plugin: PluginApi,
    private readonly action: ActionInterface
  ) {}

  popup(element: Node) {
    this._popups.push(this.plugin.deprecated.popup(element));
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

  textfield() {
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

  call(payload: unknown, onSuccess: (result: unknown) => void) {
    if (!this.action.__url) {
      console.warn(`Unable to ${this.action.method} to ${this.action.__key}!`);
      return;
    }
    this.plugin
      .restApi()
      .send(this.action.method, this.action.__url, payload)
      .then(onSuccess)
      .catch(error => {
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
