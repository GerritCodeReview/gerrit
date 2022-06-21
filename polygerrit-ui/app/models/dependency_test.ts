/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {define, provide, resolve} from './dependency';
import {html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import '../test/common-test-setup-karma.js';

interface FooService {
  value: string;
}
const fooToken = define<FooService>('foo');

interface BarService {
  value: string;
}

const barToken = define<BarService>('bar');

class FooImpl implements FooService {
  constructor(public readonly value: string) {}
}

class BarImpl implements BarService {
  constructor(private readonly foo: FooService) {}

  get value() {
    return this.foo.value;
  }
}

@customElement('lit-foo-provider')
export class LitFooProviderElement extends LitElement {
  @query('bar-provider')
  bar?: BarProviderElement;

  @property({type: Boolean})
  public showBarProvider = true;

  constructor() {
    super();
    provide(this, fooToken, () => new FooImpl('foo'));
  }

  override render() {
    if (this.showBarProvider) {
      return html`<bar-provider></bar-provider>`;
    } else {
      return undefined;
    }
  }
}

@customElement('bar-provider')
export class BarProviderElement extends LitElement {
  @query('leaf-lit-element')
  litChild?: LeafLitElement;

  override connectedCallback() {
    super.connectedCallback();
    provide(this, barToken, () => this.create());
  }

  private create() {
    const fooRef = resolve(this, fooToken);
    assert.isDefined(fooRef());
    return new BarImpl(fooRef());
  }

  override render() {
    return html`<leaf-lit-element></leaf-lit-element>`;
  }
}

@customElement('leaf-lit-element')
export class LeafLitElement extends LitElement {
  readonly barRef = resolve(this, barToken);

  override connectedCallback() {
    super.connectedCallback();
    assert.isDefined(this.barRef());
  }

  override render() {
    return html`${this.barRef().value}`;
  }
}

suite('Dependency', () => {
  test('It instantiates', async () => {
    const fixture = fixtureFromElement('lit-foo-provider');
    const element = fixture.instantiate();
    await element.updateComplete;
    assert.isDefined(element.bar?.litChild?.barRef());
  });

  test('It works by connecting and reconnecting', async () => {
    const fixture = fixtureFromElement('lit-foo-provider');
    const element = fixture.instantiate();
    await element.updateComplete;
    assert.isDefined(element.bar?.litChild?.barRef());

    element.showBarProvider = false;
    await element.updateComplete;
    assert.isNull(element.bar);

    element.showBarProvider = true;
    await element.updateComplete;
    assert.isDefined(element.bar?.litChild?.barRef());
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'lit-foo-provider': LitFooProviderElement;
    'bar-provider': BarProviderElement;
    'leaf-lit-element': LeafLitElement;
  }
}
