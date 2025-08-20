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
import {stubRestApi} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';

suite('gr-flows tests', () => {
  let element: GrFlows;

  setup(async () => {
    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);
  });

  test('renders no flows message', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <p>No flows found for this change.</p> '
    );
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
    element['changeNum'] = 123 as NumericChangeId;
    await element['loadFlows']();
    await element.updateComplete;

    const flowElements = element.shadowRoot?.querySelectorAll('.flow');
    assert.lengthOf(flowElements!, 2);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
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
  });
});
