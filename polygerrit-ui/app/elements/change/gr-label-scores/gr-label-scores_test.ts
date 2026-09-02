/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-label-scores';
import {
  isHidden,
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {GrLabelScores} from './gr-label-scores';
import {AccountId} from '../../../types/common';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  createAccountWithId,
  createChange,
} from '../../../test/test-data-generators';
import {ChangeStatus} from '../../../constants/constants';
import {getVoteForAccount} from '../../../utils/label-util';
import {assert, fixture, html} from '@open-wc/testing';

suite('gr-label-scores tests', () => {
  const accountId = 123 as AccountId;
  let element: GrLabelScores;

  setup(async () => {
    stubRestApi('getLoggedIn').resolves(false);
    element = await fixture(html`<gr-label-scores></gr-label-scores>`);
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
          value: 1,
          all: [
            {
              _account_id: accountId,
              value: 1,
            },
          ],
        },
        Verified: {
          values: {
            '0': 'No score',
            '+1': 'good',
            '+2': 'excellent',
            '-1': 'bad',
            '-2': 'terrible',
          },
          default_value: 0,
          value: 1,
          all: [
            {
              _account_id: accountId,
              value: 1,
            },
          ],
        },
      },
    };

    element.account = createAccountWithId(accountId);

    element.permittedLabels = {
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      Verified: ['-1', ' 0', '+1'],
    };
    await element.updateComplete;
  });

  test('render', () => {
    const mergedMessage =
      'Because this change has been merged, votes may not be decreased. You can still reply to comments without changing your vote.';
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="sectionHeaderRow">
          <h3 class="heading-4">Trigger Votes</h3>
        </div>
        <gr-label-score-row name="Code-Review"> </gr-label-score-row>
        <gr-label-score-row name="Verified"> </gr-label-score-row>
        <div class="mergedMessage" hidden="">${mergedMessage}</div>
        <div class="abandonedMessage" hidden="">
          Because this change has been abandoned, you cannot vote.
        </div>
      `
    );
  });

  test('get and set label scores', async () => {
    for (const label of Object.keys(element.permittedLabels!)) {
      const row = queryAndAssert<GrLabelScoreRow>(
        element,
        'gr-label-score-row[name="' + label + '"]'
      );
      row.setSelectedValue('-1');
    }
    await element.updateComplete;
    assert.deepEqual(element.getLabelValues(), {
      'Code-Review': -1,
      Verified: -1,
    });
  });

  test('getLabelValues includeDefaults', async () => {
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {'0': 'meh', '+1': 'good', '-1': 'bad'},
          default_value: 0,
        },
      },
    };
    await element.updateComplete;

    assert.deepEqual(element.getLabelValues(true), {'Code-Review': 0});
    assert.deepEqual(element.getLabelValues(false), {});
  });

  test('getLabelValues with previous vote and includeDefaults=false', async () => {
    // Setup gives account +1 on Code-Review and +1 on Verified.
    const row = queryAndAssert<GrLabelScoreRow>(
      element,
      'gr-label-score-row[name="Code-Review"]'
    );
    // User changes their vote to +2
    row.setSelectedValue('+2');
    await element.updateComplete;

    // includeDefaults=false should OMIT Verified (since it is unchanged at
    // +1) but should INCLUDE Code-Review (since it changed to +2).
    assert.deepEqual(element.getLabelValues(false), {'Code-Review': 2});

    // Changing back to +1 (original vote) makes it unchanged again, so it's
    // omitted.
    row.setSelectedValue('+1');
    await element.updateComplete;
    assert.deepEqual(element.getLabelValues(false), {});
  });

  test('getVoteForAccount', () => {
    const labelName = 'Code-Review';
    assert.strictEqual(
      getVoteForAccount(labelName, element.account, element.change),
      '+1'
    );
  });

  suite('message', () => {
    test('shown when change is abandoned', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.ABANDONED,
      };
      await waitEventLoop();
      assert.isFalse(isHidden(queryAndAssert(element, '.abandonedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.mergedMessage')));
    });
    test('shown when change is merged', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.MERGED,
      };
      await waitEventLoop();
      assert.isFalse(isHidden(queryAndAssert(element, '.mergedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.abandonedMessage')));
    });
    test('do not show for new', async () => {
      element.change = {
        ...createChange(),
        status: ChangeStatus.NEW,
      };
      await waitEventLoop();
      assert.isTrue(isHidden(queryAndAssert(element, '.mergedMessage')));
      assert.isTrue(isHidden(queryAndAssert(element, '.abandonedMessage')));
    });
  });
});
