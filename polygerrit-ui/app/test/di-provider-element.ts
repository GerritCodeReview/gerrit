/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {DependencyToken, provide, Provider} from '../models/dependency';

/**
 * usage:
 *   wrapInProvider(
 *     html`<my-element-to-test></my-element-to-test>`,
 *     myModelToken,
 *     () => new Model(),
 *   );
 */
export function wrapInProvider<T>(
  template: TemplateResult,
  token: DependencyToken<T>,
  provider: Provider<T>
) {
  return html`
    <di-provider-element .token=${token} .provider=${provider}
      >${template}</di-provider-element
    >
  `;
}

/** */
@customElement('di-provider-element')
export class DIProviderElement<T> extends LitElement {
  @property({type: Object})
  token?: DependencyToken<T>;

  @property({type: Object})
  provider?: Provider<T>;

  value?: T;

  @query('slot')
  private slotElement?: HTMLSlotElement;

  get elements() {
    return this.slotElement?.assignedElements;
  }

  static override get styles() {
    return css`
      :host() {
        display: contents;
      }
    `;
  }

  override connectedCallback(): void {
    super.connectedCallback();
    if (this.token !== undefined && this.provider !== undefined) {
      this.value = this.provider();
      provide(this, this.token, () => this.value);
    }
  }

  override render() {
    return html`<slot></slot>`;
  }
}
