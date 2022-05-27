/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
