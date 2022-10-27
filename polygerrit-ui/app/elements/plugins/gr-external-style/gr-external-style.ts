/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {updateStyles} from '@polymer/polymer/lib/mixins/element-mixin';
import {LitElement, html, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {resolve} from '../../../models/dependency';

@customElement('gr-external-style')
export class GrExternalStyle extends LitElement {
  // This is a required value for this component.
  @property({type: String, reflect: true})
  name!: string;

  // private but used in test
  stylesApplied: string[] = [];

  stylesElements: HTMLElement[] = [];

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  override render() {
    return html`<slot></slot>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('name')) {
      // We remove all styles defined for different name.
      this.removeStyles();
      this.importAndApply();
      this.getPluginLoader()
        .awaitPluginsLoaded()
        .then(() => this.importAndApply());
    }
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
    this.stylesElements.push(cs);
    cs.appendChild(s);
    // When using Shadow DOM <custom-style> must be added to the <body>.
    // Within <gr-external-style> itself the styles would have no effect.
    const topEl = document.getElementsByTagName('body')[0];
    topEl.insertBefore(cs, topEl.firstChild);
    updateStyles();
  }

  removeStyles() {
    this.stylesElements.forEach(el => el.remove());
    this.stylesElements = [];
    this.stylesApplied = [];
  }

  private importAndApply() {
    const moduleNames = this.getPluginLoader().pluginEndPoints.getModules(
      this.name
    );
    for (const name of moduleNames) {
      this.applyStyle(name);
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-external-style': GrExternalStyle;
  }
}
