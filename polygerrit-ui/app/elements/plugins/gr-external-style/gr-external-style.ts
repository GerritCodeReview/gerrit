/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {updateStyles} from '@polymer/polymer/lib/mixins/element-mixin';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {LitElement, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';

@customElement('gr-external-style')
export class GrExternalStyle extends LitElement {
  // This is a required value for this component.
  @property({type: String})
  name!: string;

  // private but used in test
  @state() stylesApplied: string[] = [];

  override render() {
    return html`<slot></slot>`;
  }

  // private but used in test
  applyStyle(name: string) {
    if (this.stylesApplied.includes(name)) {
      return;
    }
    this.stylesApplied.push(name);

    const s = document.createElement('style');
    s.setAttribute('include', name);
    const cs = document.createElement('custom-style');
    cs.appendChild(s);
    // When using Shadow DOM <custom-style> must be added to the <body>.
    // Within <gr-external-style> itself the styles would have no effect.
    const topEl = document.getElementsByTagName('body')[0];
    topEl.insertBefore(cs, topEl.firstChild);
    updateStyles();
  }

  private importAndApply() {
    const moduleNames = getPluginEndpoints().getModules(this.name);
    for (const name of moduleNames) {
      this.applyStyle(name);
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    this.importAndApply();
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => this.importAndApply());
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-external-style': GrExternalStyle;
  }
}
