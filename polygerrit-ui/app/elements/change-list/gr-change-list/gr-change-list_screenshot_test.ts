/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {testResolver} from '../../../test/common-test-setup';
import './gr-change-list';
import {assert, fixture, html, nextFrame} from '@open-wc/testing';
import {setViewport} from '@web/test-runner-commands';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeList} from './gr-change-list';
import {
  createAccountDetailWithIdNameAndEmail,
  createChange,
  createServerInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {createDefaultPreferences} from '../../../constants/constants';
import {userModelToken} from '../../../models/user/user-model';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {ChangeInfo, NumericChangeId, Timestamp} from '../../../types/common';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-change-list screenshot tests', () => {
  let element: GrChangeList;

  function createChanges(count: number): ChangeInfo[] {
    return Array.from(Array(count).keys()).map(index => {
      return {
        ...createChange(),
        _number: (index + 1) as NumericChangeId,
        subject: `Change subject ${index + 1}`,
        updated: `2020-01-${String(index + 1).padStart(
          2,
          '0'
        )} 10:00:00.000000000` as Timestamp,
      };
    });
  }

  setup(async () => {
    element = await fixture(html`<gr-change-list></gr-change-list>`);
    element.changes = createChanges(5);
    await element.updateComplete;
  });

  test('basic list', async () => {
    await visualDiff(element, 'gr-change-list');
    await visualDiffDarkTheme(element, 'gr-change-list');
  });

  for (const width of [390, 700, 1000, 1400]) {
    test(`responsive list at ${width}px`, async () => {
      await setViewport({width, height: 900});
      const userModel = testResolver(userModelToken);
      const account = createAccountDetailWithIdNameAndEmail();
      userModel.setAccount(account);
      element.loggedInUser = account;
      element.config = createServerInfo();
      userModel.setPreferences({
        ...createDefaultPreferences(),
        legacycid_in_change_table: true,
      });
      element.changes = createChanges(2).map((change, index) => {
        return {
          ...change,
          _number: (630301 + index) as NumericChangeId,
          subject:
            'Show label votes and configurable columns on narrow screens',
          owner: {...change.owner, name: 'Monty Taylor Sword Nimi'},
          submit_requirements: (index === 0
            ? ['Code-Review', 'Verified', 'Frontend-Verified', 'Code-Style']
            : ['Code-Review', 'Code-Style']
          ).map(name => {
            return {...createSubmitRequirementResultInfo(), name};
          }),
        };
      });
      await element.updateComplete;
      await nextFrame();
      element.showNumber = true;
      await element.updateComplete;
      await nextFrame();
      const section = element.shadowRoot!.querySelector<GrChangeListSection>(
        'gr-change-list-section'
      )!;
      await section.updateComplete;
      const rows = Array.from(
        section.shadowRoot!.querySelectorAll<GrChangeListItem>(
          'gr-change-list-item'
        )
      );
      await Promise.all(rows.map(row => row.updateComplete));
      await nextFrame();
      assert.isAtMost(element.getBoundingClientRect().right, width);
      if (width <= 800) {
        const first = rows[0].shadowRoot!;
        const number = first.querySelector('.number')!;
        const subject = first.querySelector('.subject')!;
        assert.equal(
          Math.round(number.getBoundingClientRect().top),
          Math.round(subject.getBoundingClientRect().top)
        );
        assert.isAtLeast(
          number.getBoundingClientRect().left,
          subject.getBoundingClientRect().right
        );
        const votePositions = rows.map(row =>
          Array.from(row.shadowRoot!.querySelectorAll('.label')).map(cell =>
            Math.round(cell.getBoundingClientRect().left)
          )
        );
        assert.deepEqual(votePositions[0], votePositions[1]);
        const accountLabel = first.querySelector('gr-account-label')!;
        const name = accountLabel.shadowRoot!.querySelector('.name')!;
        assert.isAbove(name.clientWidth, 100);
        assert.isAtMost(name.scrollWidth, name.clientWidth);
        assert.isAtMost(element.scrollWidth, element.clientWidth);
      }
      await visualDiff(element, `gr-change-list-${width}px`);
      await visualDiffDarkTheme(element, `gr-change-list-${width}px`);
    });
  }
});
