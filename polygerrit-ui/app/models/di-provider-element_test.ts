/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, state} from 'lit/decorators';
import {define, resolve} from './dependency';
import '../test/common-test-setup-karma.js';
import {fixture} from '@open-wc/testing-helpers';
import {DIProviderElement, wrapInProvider} from './di-provider-element';
import {BehaviorSubject} from 'rxjs';
import {waitUntilObserved} from '../test/test-utils';
import {subscribe} from '../elements/lit/subscription-controller';

const modelToken = define<BehaviorSubject<string>>('token');

/**
 * This is an example element-under-test. It is expecting a model injected
 * using a token, and then always displaying the value from that model.
 */
@customElement('consumer-element')
class ConsumerElement extends LitElement {
  readonly getModel = resolve(this, modelToken);

  @state()
  private injectedValue = '';

  constructor() {
    super();
    subscribe(
      this,
      () => this.getModel(),
      value => (this.injectedValue = value)
    );
  }

  override render() {
    return html`<div>${this.injectedValue}</div>`;
  }
}

suite('di-provider-element', () => {
  let injectedModel: BehaviorSubject<string>;
  let element: ConsumerElement;

  setup(async () => {
    // The injected value and fixture are created inside `setup` to prevent
    // tests from leaking into each other.
    injectedModel = new BehaviorSubject('foo');
    element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<consumer-element></consumer-element>`,
          modelToken,
          injectedModel
        )
      )
    ).element as ConsumerElement;
  });

  test('provides values to the wrapped element', () => {
    expect(element).shadowDom.to.equal('<div>foo</div>');
  });

  test('enables the test to control the injected dependency', async () => {
    injectedModel.next('bar');
    await waitUntilObserved(injectedModel, value => value === 'bar');

    expect(element).shadowDom.to.equal('<div>bar</div>');
  });
});
