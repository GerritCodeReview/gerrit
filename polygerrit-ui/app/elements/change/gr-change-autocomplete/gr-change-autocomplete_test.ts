/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-change-autocomplete';
import {GrChangeAutocomplete} from './gr-change-autocomplete';
import {stubRestApi} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {createChangeViewChange} from '../../../test/test-data-generators';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-change-autocomplete tests', () => {
  let element: GrChangeAutocomplete;
  let getChangesStub: sinon.SinonStub;

  setup(async () => {
    getChangesStub = stubRestApi('getChanges').returns(
      Promise.resolve([
        {
          ...createChangeViewChange(),
          _number: 123 as NumericChangeId,
          subject: 'my first awesome change',
        },
        {
          ...createChangeViewChange(),
          _number: 124 as NumericChangeId,
          subject: 'my second awesome change',
        },
        {
          ...createChangeViewChange(),
          _number: 245 as NumericChangeId,
          subject: 'my third awesome change',
        },
      ])
    );

    element = await fixture<GrChangeAutocomplete>(
      html`<gr-change-autocomplete></gr-change-autocomplete>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-autocomplete
          allow-non-suggested-values=""
          placeholder="Change number or subject"
        >
        </gr-autocomplete>
      `
    );
  });

  test('fetches recent changes on connectedCallback', () => {
    assert.isTrue(getChangesStub.calledOnce);
  });

  test('suggestions are filtered by input', async () => {
    const query = element['query'];
    let suggestions = await query('123');
    assert.equal(suggestions.length, 1);
    assert.equal(suggestions[0].name, '123: my first awesome change');

    suggestions = await query('12');
    assert.equal(suggestions.length, 2);

    suggestions = await query('awesome');
    assert.equal(suggestions.length, 3);

    suggestions = await query('third');
    assert.equal(suggestions.length, 1);
  });

  test('suggestions do not include excluded change', async () => {
    element.excludeChangeNum = 123 as NumericChangeId;
    await element.updateComplete;

    const query = element['query'];
    let suggestions = await query('123');
    assert.equal(suggestions.length, 0);

    suggestions = await query('awesome');
    assert.equal(suggestions.length, 2);
  });
});
