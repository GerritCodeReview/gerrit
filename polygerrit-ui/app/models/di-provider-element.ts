/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {DependencyToken, provide} from './dependency';

/**
 * Example usage:
 *
 *   const providerElement = await fixture<DIProviderElement>(
 *     wrapInProvider(
 *       html`<my-element-to-test></my-element-to-test>`,
 *       myModelToken,
 *       myModel,
 *     )
 *   );
 *   const element = providerElement.element as MyElementToTest;
 *
 * For injecting multiple tokens, make nested calls to `wrapInProvider` such as:
 *
 *   wrapInProvider(wrapInProvider(html`...`, token1, value1), token2, value2);
 */
export function wrapInProvider<T>(
  template: TemplateResult,
  token: DependencyToken<T>,
  value: T
) {
  return html`
    <di-provider-element .token=${token} .value=${value}
      >${template}</di-provider-element
    >
  `;
}

/**
 * Use `.element` to get the wrapped element for assertions.
 */
@customElement('di-provider-element')
export class DIProviderElement extends LitElement {
  @property({type: Object})
  token?: DependencyToken<unknown>;

  @property({type: Object})
  value?: unknown;

  get element() {
    return this.slotElement.assignedElements()[0];
  }

  private isProvided = false;

  @query('slot')
  private slotElement!: HTMLSlotElement;

  static override get styles() {
    return css`
      :host() {
        display: contents;
      }
    `;
  }

  /** Only calls `provide` even after reconnection. */
  override connectedCallback(): void {
    super.connectedCallback();
    if (!this.token || this.isProvided) return;
    this.isProvided = true;
    provide(this, this.token, () => this.value);
  }

  override render() {
    return html`<slot></slot>`;
  }
}
