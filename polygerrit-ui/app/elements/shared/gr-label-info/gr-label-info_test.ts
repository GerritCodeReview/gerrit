/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-label-info';
import {
  isHidden,
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrLabelInfo} from './gr-label-info';
import {GrButton} from '../gr-button/gr-button';
import {
  createAccountWithIdNameAndEmail,
  createDetailedLabelInfo,
  createParsedChange,
} from '../../../test/test-data-generators';
import {ApprovalInfo, LabelInfo} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-label-info');

suite('gr-label-info tests', () => {
  let element: GrLabelInfo;
  const account = createAccountWithIdNameAndEmail(5);

  setup(async () => {
    element = basicFixture.instantiate();

    // Needed to trigger computed bindings.
    element.account = {};
    element.change = {
      ...createParsedChange(),
      labels: {},
      reviewers: {
        REVIEWER: [account],
        CC: [],
      },
    };
    const approval: ApprovalInfo = {
      value: 2,
      _account_id: account._account_id,
    };
    element.labelInfo = {
      ...createDetailedLabelInfo(),
      all: [approval],
    };
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div>
      <div class="reviewer-row">
        <gr-account-chip>
          <gr-vote-chip circle-shape="" slot="vote-chip"> </gr-vote-chip>
        </gr-account-chip>
        <gr-tooltip-content has-tooltip="" title="Remove vote">
          <gr-button
            aria-disabled="false"
            aria-label="Remove vote"
            class="deleteBtn hidden"
            data-account-id="5"
            link=""
            role="button"
            tabindex="0"
          >
            <iron-icon icon="gr-icons:delete"> </iron-icon>
          </gr-button>
        </gr-tooltip-content>
      </div>
    </div>`);
  });

  suite('remove reviewer votes', () => {
    const label: LabelInfo = {
      all: [{...account, value: 1}],
      default_value: 0,
      values: {},
    };

    setup(async () => {
      sinon.stub(element, '_computeValueTooltip').returns('');
      element.account = account;
      element.change = {
        ...createParsedChange(),
        labels: {'Code-Review': label},
        reviewers: {
          REVIEWER: [account],
          CC: [],
        },
      };
      element.labelInfo = label;
      element.label = 'Code-Review';

      await element.updateComplete;
    });

    test('_computeCanDeleteVote', async () => {
      element.mutable = false;
      await element.updateComplete;
      const removeButton = queryAndAssert<GrButton>(element, 'gr-button');
      assert.isTrue(isHidden(removeButton));
      element.change!.removable_reviewers = [account];
      element.mutable = true;
      await element.updateComplete;
      assert.isFalse(isHidden(removeButton));
    });

    test('deletes votes', async () => {
      const mock = mockPromise();
      const deleteResponse = mock.then(() => new Response(null, {status: 200}));
      const deleteStub = stubRestApi('deleteVote').returns(deleteResponse);
      element.change!.removable_reviewers = [account];
      element.change!.labels!['Code-Review'] = {
        ...label,
        recommended: account,
      };
      element.mutable = true;
      const removeButton = queryAndAssert<GrButton>(element, 'gr-button');

      MockInteractions.tap(removeButton);
      assert.isTrue(removeButton.disabled);
      mock.resolve();
      await deleteResponse;

      assert.isFalse(removeButton.disabled);
      assert.isTrue(
        deleteStub.calledWithExactly(
          element.change!._number,
          account._account_id!,
          'Code-Review'
        )
      );
    });
  });

  test('_computeValueTooltip', () => {
    // Existing label.
    let labelInfo: LabelInfo = {values: {0: 'Baz'}};
    let score = '0';
    assert.equal(element._computeValueTooltip(labelInfo, score), 'Baz');

    // Non-existent score.
    score = '2';
    assert.equal(element._computeValueTooltip(labelInfo, score), '');

    // No values on label.
    labelInfo = {values: {}};
    score = '0';
    assert.equal(element._computeValueTooltip(labelInfo, score), '');
  });
});
