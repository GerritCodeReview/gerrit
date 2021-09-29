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
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrLabelInfo} from './gr-label-info';
import {GrButton} from '../gr-button/gr-button';
import {GrLabel} from '../gr-label/gr-label';
import {GrAccountLink} from '../gr-account-link/gr-account-link';
import {
  createAccountWithIdNameAndEmail,
  createParsedChange,
} from '../../../test/test-data-generators';
import {LabelInfo} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-label-info');

suite('gr-label-info tests', () => {
  let element: GrLabelInfo;
  const account = createAccountWithIdNameAndEmail(5);

  setup(() => {
    element = basicFixture.instantiate();

    // Needed to trigger computed bindings.
    element.account = {};
    element.change = {...createParsedChange(), labels: {}};
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
      };
      element.labelInfo = label;
      element.label = 'Code-Review';

      await flush();
    });

    test('_computeCanDeleteVote', async () => {
      element.mutable = false;
      await flush();
      const removeButton = queryAndAssert<GrButton>(element, 'gr-button');
      assert.isTrue(isHidden(removeButton));
      element.change!.removable_reviewers = [account];
      element.mutable = true;
      await flush();
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

  suite('label color and order', () => {
    test('valueless label rejected', async () => {
      element.labelInfo = {rejected: {name: 'someone'}};
      await flush();
      const labels = queryAll<GrLabel>(element, 'gr-label');
      assert.isTrue(labels[0].classList.contains('negative'));
    });

    test('valueless label approved', async () => {
      element.labelInfo = {approved: {name: 'someone'}};
      await flush();
      const labels = queryAll<GrLabel>(element, 'gr-label');
      assert.isTrue(labels[0].classList.contains('positive'));
    });

    test('-2 to +2', async () => {
      element.labelInfo = {
        all: [
          {value: 2, name: 'user 2'},
          {value: 1, name: 'user 1'},
          {value: -1, name: 'user 3'},
          {value: -2, name: 'user 4'},
        ],
        values: {
          '-2': 'Awful',
          '-1': "Don't submit as-is",
          ' 0': 'No score',
          '+1': 'Looks good to me',
          '+2': 'Ready to submit',
        },
      };
      await flush();
      const labels = queryAll<GrLabel>(element, 'gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('positive'));
      assert.isTrue(labels[2].classList.contains('negative'));
      assert.isTrue(labels[3].classList.contains('min'));
    });

    test('-1 to +1', async () => {
      element.labelInfo = {
        all: [
          {value: 1, name: 'user 1'},
          {value: -1, name: 'user 2'},
        ],
        values: {
          '-1': "Don't submit as-is",
          ' 0': 'No score',
          '+1': 'Looks good to me',
        },
      };
      await flush();
      const labels = queryAll<GrLabel>(element, 'gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('min'));
    });

    test('0 to +2', async () => {
      element.labelInfo = {
        all: [
          {value: 1, name: 'user 2'},
          {value: 2, name: 'user '},
        ],
        values: {
          ' 0': "Don't submit as-is",
          '+1': 'No score',
          '+2': 'Looks good to me',
        },
      };
      await flush();
      const labels = queryAll<GrLabel>(element, 'gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('positive'));
    });

    test('self votes at top', async () => {
      const otherAccount = createAccountWithIdNameAndEmail(8);
      element.account = account;
      element.labelInfo = {
        all: [
          {...otherAccount, value: 1},
          {...account, value: -1},
        ],
        values: {
          '-1': "Don't submit as-is",
          ' 0': 'No score',
          '+1': 'Looks good to me',
        },
      };
      await flush();
      const chips = queryAll<GrAccountLink>(element, 'gr-account-link');
      assert.equal(chips[0].account!._account_id, element.account._account_id);
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

  test('placeholder', async () => {
    const values = {
      '0': 'No score',
      '+1': 'good',
      '+2': 'excellent',
      '-1': 'bad',
      '-2': 'terrible',
    };
    element.labelInfo = {};
    await flush();
    assert.isFalse(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {all: [], values};
    await flush();
    assert.isFalse(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {all: [{value: 1}], values};
    await flush();
    assert.isTrue(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {rejected: account};
    await flush();
    assert.isTrue(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {rejected: account, all: [{value: 1}], values};
    await flush();
    assert.isTrue(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {approved: account};
    await flush();
    assert.isTrue(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
    element.labelInfo = {approved: account, all: [{value: 1}], values};
    await flush();
    assert.isTrue(
      isHidden(queryAndAssert<HTMLParagraphElement>(element, '.placeholder'))
    );
  });
});
