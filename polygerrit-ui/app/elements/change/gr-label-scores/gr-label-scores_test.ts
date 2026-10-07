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
import {
  executeServerCommand,
  sendMouse,
  setViewport,
} from '@web/test-runner-commands';
import {GrButton} from '../../shared/gr-button/gr-button';
import {MdTextButton} from '@material/web/button/text-button';

suite('gr-label-scores tests', () => {
  const input = navigator.maxTouchPoints > 0 ? 'touch' : 'mouse';
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

  for (const width of [1200, 700, 390]) {
    test(`${input} near a vote button cannot change another label at ${width}px`, async () => {
      await setViewport({width, height: 800});
      assert.equal(window.innerWidth, width);
      let pointerType = '';
      element.addEventListener('pointerdown', e => {
        pointerType = e.pointerType;
      });
      const rows = ['Code-Review', 'Verified'].map(name =>
        queryAndAssert<GrLabelScoreRow>(
          element,
          `gr-label-score-row[name="${name}"]`
        )
      );
      await Promise.all(rows.map(row => row.updateComplete));
      for (const row of rows) row.setSelectedValue('-1');
      await Promise.all(rows.map(row => row.updateComplete));
      const buttons = rows.map(row =>
        queryAndAssert<GrButton>(row, 'gr-button[data-value=" 0"]')
      );
      await Promise.all(buttons.map(button => button.updateComplete));
      await Promise.all(
        buttons.map(
          button =>
            queryAndAssert<MdTextButton>(button, 'md-text-button')
              .updateComplete
        )
      );
      const rects = buttons.map(button => button.getBoundingClientRect());
      const click = async (rect: DOMRect, y: number) => {
        const position: [number, number] = [
          Math.round(rect.x + rect.width / 2),
          Math.round(y),
        ];
        if (input === 'touch') {
          await executeServerCommand('send-touch-tap', {position});
        } else {
          await sendMouse({type: 'click', position});
        }
        await Promise.all(rows.map(row => row.updateComplete));
      };

      // Round away from fractional edges so the points are fully outside
      // the buttons even with different fonts or device pixel rounding.
      // Tap adjustment may select the nearby button, but never the other label.
      await click(rects[0], Math.ceil(rects[0].bottom) + 1);
      assert.equal(element.getLabelValues().Verified, -1);
      if (input === 'mouse') {
        assert.equal(element.getLabelValues()['Code-Review'], -1);
      }
      for (const row of rows) row.setSelectedValue('-1');
      await Promise.all(rows.map(row => row.updateComplete));
      await click(rects[1], Math.floor(rects[1].top) - 1);
      assert.equal(element.getLabelValues()['Code-Review'], -1);
      if (input === 'mouse') {
        assert.equal(element.getLabelValues().Verified, -1);
      }
      for (const row of rows) row.setSelectedValue('-1');
      await Promise.all(rows.map(row => row.updateComplete));

      await click(rects[0], rects[0].y + rects[0].height / 2);
      assert.equal(pointerType, input);
      assert.deepEqual(element.getLabelValues(), {
        'Code-Review': 0,
        Verified: -1,
      });
      await click(rects[1], rects[1].y + rects[1].height / 2);
      assert.deepEqual(element.getLabelValues(), {
        'Code-Review': 0,
        Verified: 0,
      });
    });
  }

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
