/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, html} from 'lit';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {pluginViewModelToken} from '../../../models/views/plugin';
import {customElement, state} from 'lit/decorators.js';
import {keyed} from 'lit/directives/keyed.js';

@customElement('gr-plugin-screen')
export class GrPluginScreen extends LitElement {
  @state() private screen?: string;

  @state() private screenName?: string;

  private readonly getPluginViewModel = resolve(this, pluginViewModelToken);

  constructor() {
    super();

    subscribe(
      this,
      () => this.getPluginViewModel().state$,
      state => {
        this.screen = state.screen;
      }
    );
    subscribe(
      this,
      () => this.getPluginViewModel().screenName$,
      screenName => {
        this.screenName = screenName;
      }
    );
  }

  override render() {
    return keyed(
      this.screenName,
      html`
        <gr-endpoint-decorator .name=${this.screenName}>
          <gr-endpoint-param
            name="token"
            .value=${this.screen}
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-screen': GrPluginScreen;
  }
}
