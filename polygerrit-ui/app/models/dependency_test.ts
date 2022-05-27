/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {define, provide, resolve, DIPolymerElement} from './dependency';
import {html, LitElement} from 'lit';
import {customElement as polyCustomElement} from '@polymer/decorators';
import {html as polyHtml} from '@polymer/polymer/lib/utils/html-tag';
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

@polyCustomElement('polymer-foo-provider')
export class PolymerFooProviderElement extends DIPolymerElement {
  bar() {
    return this.$.bar as BarProviderElement;
  }

  override connectedCallback() {
    provide(this, fooToken, () => new FooImpl('foo'));
    super.connectedCallback();
  }

  static get template() {
    return polyHtml`<bar-provider id="bar"></bar-provider>`;
  }
}

@customElement('bar-provider')
export class BarProviderElement extends LitElement {
  @query('leaf-lit-element')
  litChild?: LeafLitElement;

  @query('leaf-polymer-element')
  polymerChild?: LeafPolymerElement;

  @property({type: Boolean})
  public showLit = true;

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
    if (this.showLit) {
      return html`<leaf-lit-element></leaf-lit-element>`;
    } else {
      return html`<leaf-polymer-element></leaf-polymer-element>`;
    }
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

@polyCustomElement('leaf-polymer-element')
export class LeafPolymerElement extends DIPolymerElement {
  readonly barRef = resolve(this, barToken);

  override connectedCallback() {
    super.connectedCallback();
    assert.isDefined(this.barRef());
  }

  static get template() {
    return polyHtml`Hello`;
  }
}

suite('Dependency', () => {
  test('It instantiates', async () => {
    const fixture = fixtureFromElement('lit-foo-provider');
    const element = fixture.instantiate();
    await element.updateComplete;
    assert.isDefined(element.bar?.litChild?.barRef());
  });

  test('It instantiates in polymer', async () => {
    const fixture = fixtureFromElement('polymer-foo-provider');
    const element = fixture.instantiate();
    await element.bar().updateComplete;
    assert.isDefined(element.bar().litChild?.barRef());
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

  test('It works by connecting and reconnecting Polymer', async () => {
    const fixture = fixtureFromElement('lit-foo-provider');
    const element = fixture.instantiate();
    await element.updateComplete;

    const beta = element.bar;
    assert.isDefined(beta);
    assert.isNotNull(beta);
    assert.isDefined(element.bar?.litChild?.barRef());

    beta!.showLit = false;
    await element.updateComplete;
    assert.isDefined(element.bar?.polymerChild?.barRef());
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'lit-foo-provider': LitFooProviderElement;
    'polymer-foo-provider': PolymerFooProviderElement;
    'bar-provider': BarProviderElement;
    'leaf-lit-element': LeafLitElement;
    'leaf-polymer-element': LeafPolymerElement;
  }
}
