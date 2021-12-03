/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {define, provide, resolve, DIPolymerElement} from './dependency';
import {html, LitElement} from 'lit';
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

@customElement('test-alpha')
export class AlphaElement extends LitElement {
  @query('test-beta')
  beta?: BetaElement;

  @property({type: Boolean})
  public showBeta = true;

  constructor() {
    super();
    provide(this, fooToken, () => new FooImpl('foo'));
  }

  override render() {
    if (this.showBeta) {
      return html`<test-beta></test-beta>`;
    } else {
      return undefined;
    }
  }
}

@customElement('test-beta')
export class BetaElement extends LitElement {
  @query('test-gamma')
  gamma?: GammaElement;

  @query('test-kappa')
  kappa?: KappaElement;

  @property({type: Boolean})
  public showGamma = true;

  override connectedCallback() {
    super.connectedCallback();
    provide(this, barToken, () => this.create());
  }

  private create() {
    const fooRef = resolve(this, fooToken);
    assert.isDefined(fooRef.get());
    return new BarImpl(fooRef.get());
  }

  override render() {
    if (this.showGamma) {
      return html`<test-gamma></test-gamma>`;
    } else {
      return html`<test-kappa></test-kappa>`;
    }
  }
}

@customElement('test-gamma')
export class GammaElement extends LitElement {
  readonly barRef = resolve(this, barToken);

  override connectedCallback() {
    super.connectedCallback();
    assert.isDefined(this.barRef.get());
  }

  override render() {
    return html`${this.barRef.get().value}`;
  }
}

@customElement('test-kappa')
export class KappaElement extends DIPolymerElement {
  readonly barRef = resolve(this, barToken);

  override connectedCallback() {
    super.connectedCallback();
    assert.isDefined(this.barRef.get());
  }

  static get template() {
    return polyHtml`Hello`;
  }
}

suite('Dependency', () => {
  test('It instantiates', async () => {
    const fixture = fixtureFromElement('test-alpha');
    const element = fixture.instantiate();
    await element.updateComplete;
    assert.isDefined(element.beta?.gamma?.barRef.get());
  });

  test('It works by connecting and reconnecting', async () => {
    const fixture = fixtureFromElement('test-alpha');
    const element = fixture.instantiate();
    await element.updateComplete;
    assert.isDefined(element.beta?.gamma?.barRef.get());

    element.showBeta = false;
    await element.updateComplete;
    assert.isNull(element.beta);

    element.showBeta = true;
    await element.updateComplete;
    assert.isDefined(element.beta?.gamma?.barRef.get());
  });

  test('It works by connecting and reconnecting Polymer', async () => {
    const fixture = fixtureFromElement('test-alpha');
    const element = fixture.instantiate();
    await element.updateComplete;

    const beta = element.beta;
    assert.isDefined(beta);
    assert.isNotNull(beta);
    assert.isDefined(element.beta?.gamma?.barRef.get());

    beta!.showGamma = false;
    await element.updateComplete;
    assert.isDefined(element.beta?.kappa?.barRef.get());
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-alpha': AlphaElement;
    'test-beta': BetaElement;
    'test-gamma': GammaElement;
    'test-kappa': KappaElement;
  }
}
