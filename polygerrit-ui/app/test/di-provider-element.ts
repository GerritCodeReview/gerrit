/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {DependencyToken, provide} from '../models/dependency';

export type ProviderPair<T = unknown> = [DependencyToken<T>, T];

/**
 * usage:
 *   wrapInProvider(
 *     html`<my-element-to-test></my-element-to-test>`,
 *     [myModelToken, () => new MyModel()])
 */
export function wrapInProvider(
  template: TemplateResult,
  ...provide: ProviderPair[]
) {
  return html`
    <di-provider-element .toProvide=${provide}>${template}</di-provider-element>
  `;
}

/** */
@customElement('di-provider-element')
export class DIProviderElement extends LitElement {
  @property({type: Array})
  toProvide: ProviderPair[] = [];

  override connectedCallback(): void {
    super.connectedCallback();
    for (const [token, value] of this.toProvide) {
      provide(this, token, () => value);
    }
  }

  override render() {
    return html`<slot></slot>`;
  }
}
