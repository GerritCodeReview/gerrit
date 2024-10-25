/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-label-info';
import {
  isHidden,
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {GrLabelInfo} from './gr-label-info';
import {GrButton} from '../gr-button/gr-button';
import {
  createAccountWithIdNameAndEmail,
  createDetailedLabelInfo,
  createParsedChange,
} from '../../../test/test-data-generators';
import {ApprovalInfo, LabelInfo} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-label-info tests', () => {
  let element: GrLabelInfo;
  const account = createAccountWithIdNameAndEmail(5);

  setup(async () => {
    element = await fixture(html`<gr-label-info></gr-label-info>`);

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
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div>
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
              <gr-icon icon="delete" filled></gr-icon>
            </gr-button>
          </gr-tooltip-content>
        </div>
      </div>`
    );
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

      removeButton.click();
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

  suite('computeVoters', () => {
    const account2 = createAccountWithIdNameAndEmail(7);
    test('show reviewer who voted', () => {
      element.change = {
        ...createParsedChange(),
        labels: {},
        reviewers: {
          REVIEWER: [account],
          CC: [account2],
        },
      };
      const approval: ApprovalInfo = {
        value: 2,
        _account_id: account._account_id,
      };
      const labelInfo = {
        ...createDetailedLabelInfo(),
        all: [approval],
      };

      assert.deepEqual(element.computeVoters(labelInfo), [account]);
    });

    test('show CC who voted', () => {
      element.change = {
        ...createParsedChange(),
        labels: {},
        reviewers: {
          REVIEWER: [account2],
          CC: [account],
        },
      };
      const approval: ApprovalInfo = {
        value: 2,
        _account_id: account._account_id,
      };
      const labelInfo = {
        ...createDetailedLabelInfo(),
        all: [approval],
      };

      assert.deepEqual(element.computeVoters(labelInfo), [account]);
    });

    test('show all reviewers who can vote, we ignore CC who can vote', () => {
      element.change = {
        ...createParsedChange(),
        labels: {},
        reviewers: {
          REVIEWER: [account],
          CC: [account2],
        },
      };
      element.showAllReviewers = true;
      const approval: ApprovalInfo = {
        value: 0,
        _account_id: account._account_id,
      };
      // do not show CC who can vote
      const approval2: ApprovalInfo = {
        value: 0,
        _account_id: account2._account_id,
      };
      const labelInfo = {
        ...createDetailedLabelInfo(),
        all: [approval, approval2],
      };

      assert.deepEqual(element.computeVoters(labelInfo), [account]);
    });

    test('show all reviewers who can vote and CC who voted', () => {
      element.change = {
        ...createParsedChange(),
        labels: {},
        reviewers: {
          REVIEWER: [account],
          CC: [account2],
        },
      };
      element.showAllReviewers = true;
      const approval: ApprovalInfo = {
        value: 0,
        _account_id: account._account_id,
      };
      // do not show CC who can vote
      const approval2: ApprovalInfo = {
        value: 1,
        _account_id: account2._account_id,
      };
      const labelInfo = {
        ...createDetailedLabelInfo(),
        all: [approval, approval2],
      };

      assert.deepEqual(element.computeVoters(labelInfo), [account, account2]);
    });
  });
});
