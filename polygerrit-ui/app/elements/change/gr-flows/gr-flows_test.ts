/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {assert, fixture, html} from '@open-wc/testing';
import {GrFlows} from './gr-flows';
import {FlowInfo, Timestamp} from '../../../api/rest-api';
import {
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-flows tests', () => {
  let element: GrFlows;

  setup(async () => {
    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
    element['changeNum'] = 123 as NumericChangeId;
    await element.updateComplete;
  });

  test('renders initially loading', () => {
    assert.isTrue(element['loading']);
    const button = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isTrue(button.hasAttribute('disabled'));
  });

  test('renders no flows message', async () => {
    stubRestApi('listFlows').returns(Promise.resolve([]));
    await element['loadFlows']();
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button> Create Flow </gr-button>
        <p>No flows found for this change.</p>
      `
    );
    const createButton = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isFalse(createButton.hasAttribute('disabled'));
  });

  test('renders flows', async () => {
    const flows: FlowInfo[] = [
      {
        uuid: 'flow1',
        owner: {name: 'owner1'},
        created: '2025-01-01' as Timestamp,
        stages: [],
      },
      {
        uuid: 'flow2',
        owner: {name: 'owner2'},
        created: '2025-01-02' as Timestamp,
        stages: [],
      },
    ];
    stubRestApi('listFlows').returns(Promise.resolve(flows));
    await element['loadFlows']();
    await element.updateComplete;

    const flowElements = element.shadowRoot?.querySelectorAll('.flow');
    assert.lengthOf(flowElements!, 2);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button> Create Flow </gr-button>
        <div>
          <div class="flow">
            <div class="flow-id">Flow flow1</div>
            <div>Owner: owner1</div>
            <div>Created: 2025-01-01</div>
          </div>
          <div class="flow">
            <div class="flow-id">Flow flow2</div>
            <div>Owner: owner2</div>
            <div>Created: 2025-01-02</div>
          </div>
        </div>
      `
    );
    const createButton = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isFalse(createButton.hasAttribute('disabled'));
  });

  test('create flow button calls createFlow and reloads', async () => {
    const promise = mockPromise<FlowInfo>();
    const createFlowStub = stubRestApi('createFlow').returns(promise);
    const listFlowsStub = stubRestApi('listFlows').returns(Promise.resolve([]));

    // Initial load
    await element['loadFlows']();
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(element, 'gr-button');
    assert.isFalse(createButton.hasAttribute('disabled'));

    createButton.click();
    await element.updateComplete;

    assert.isTrue(createButton.hasAttribute('disabled'));

    promise.resolve({} as FlowInfo);

    // Wait for all the async operations to finish.
    await new Promise(resolve => setTimeout(resolve, 0));
    await element.updateComplete;

    assert.isFalse(createButton.hasAttribute('disabled'));

    assert.isTrue(createFlowStub.calledOnce);
    assert.isTrue(listFlowsStub.calledTwice);
  });
});
