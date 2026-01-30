/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {PerformanceMixin} from './performance-mixin';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {getAppContext} from '../services/app-context';
import {fixture, html as testHtml, assert} from '@open-wc/testing';
import {SinonStub} from 'sinon';
import {waitUntil} from '../test/test-utils';

@customElement('performance-mixin-test-element')
class PerformanceMixinTestElement extends PerformanceMixin(LitElement) {
  @property({type: String})
  foo = 'bar';

  @property({type: Number})
  heavyRenderDuration = 0;

  override render() {
    if (this.heavyRenderDuration > 0) {
      const start = performance.now();
      while (performance.now() - start < this.heavyRenderDuration) {
        // block main thread
      }
    }
    return html`<div>${this.foo}</div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'performance-mixin-test-element': PerformanceMixinTestElement;
  }
}

suite('performance-mixin tests', () => {
  let element: PerformanceMixinTestElement;
  let reporterStub: SinonStub;

  setup(async () => {
    reporterStub = sinon.stub(getAppContext().reportingService, 'reporter');
    element = await fixture(
      testHtml`<performance-mixin-test-element></performance-mixin-test-element>`
    );
  });

  test('reports slow render', async () => {
    // Trigger a slow render
    element.heavyRenderDuration = 30; // > 20ms threshold
    await element.updateComplete;

    await waitUntil(() => reporterStub.called);

    assert.isTrue(reporterStub.calledWith(
      'timing-report',
      'UI Latency',
      'ComponentRender'
    ));
    const args = reporterStub.lastCall.args;
    assert.operator(args[3], '>=', 30); // duration
    assert.equal(args[4].tagName, 'PERFORMANCE-MIXIN-TEST-ELEMENT');
  });

  test('does not report fast render', async () => {
    // Trigger a fast render
    element.heavyRenderDuration = 0;
    element.foo = 'baz';
    await element.updateComplete;

    // We can't easily wait for "not called", so we wait a bit and check
    await new Promise(resolve => setTimeout(resolve, 10));
    
    // reset history from initial render if any (initial render might be fast or slow depending on machine)
    // actually initial render was separate.
    
    // Let's check call count. Initial render might report if machine is super slow, 
    // but typically it shouldn't for 0ms work.
    // If it did report, we'd see it. 
    // Let's filter calls by 'ComponentRender'
  // Let's filter calls by 'ComponentRender'

    
    // If our test setup (fixture) triggered one, we ignore it.
    // The *update* we just triggered (foo='baz') should be fast.
    
    // Let's be explicit:
    reporterStub.resetHistory();
    element.foo = 'qux';
    await element.updateComplete;
    
    // Wait a tick
    await new Promise(resolve => setTimeout(resolve, 10));
    
    const newCalls = reporterStub.getCalls().filter(c => c.args[2] === 'ComponentRender');
    assert.equal(newCalls.length, 0);
  });
});
