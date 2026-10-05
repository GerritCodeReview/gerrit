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
import {
  ChangeInfo,
  NumericChangeId,
  RepoName,
  Timestamp,
} from '../../../types/common';
import {visualDiffDarkTheme, waitUntil} from '../../../test/test-utils';

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

  for (const width of [390, 700, 801, 820, 900, 1000, 1100, 1400]) {
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
          project: (index === 0
            ? 'openstack/very-long-repository-name'
            : 'gerrit') as RepoName,
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
        const headerPositions = Array.from(
          section.shadowRoot!.querySelectorAll('.groupTitle .label')
        ).map(cell => Math.round(cell.getBoundingClientRect().left));
        assert.deepEqual(headerPositions, votePositions[0]);
        const repo = first.querySelector('.repo')!;
        assert.isAbove(repo.getBoundingClientRect().width, 0);
        const repoLink = repo.querySelector<HTMLElement>('.fullRepo')!;
        assert.isAbove(repoLink.getBoundingClientRect().width, 0);
        assert.equal(repoLink.textContent?.trim(), element.changes[0].project);
        assert.isAtMost(repoLink.scrollWidth, repoLink.clientWidth);
        assert.isAtLeast(
          repo.getBoundingClientRect().left,
          first.querySelector('.owner')!.getBoundingClientRect().right
        );
        const accountLabel = first.querySelector('gr-account-label')!;
        const name = accountLabel.shadowRoot!.querySelector('.name')!;
        assert.isAbove(name.clientWidth, 100);
        assert.isAtMost(name.scrollWidth, name.clientWidth);
        assert.isAtMost(element.scrollWidth, element.clientWidth);
      }
      if (width > 800) {
        for (const row of rows) {
          assert.isNull(row.shadowRoot!.querySelector('.change-header'));
          assert.isNull(row.shadowRoot!.querySelector('.change-metadata'));
          assert.isNull(row.shadowRoot!.querySelector('.votes'));
          for (const cell of row.shadowRoot!.querySelectorAll('td')) {
            assert.equal(cell.parentNode, row.shadowRoot);
          }
        }
      }
      await visualDiff(element, `gr-change-list-${width}px`);
      await visualDiffDarkTheme(element, `gr-change-list-${width}px`);
    });
  }

  for (const width of [390, 700, 1000]) {
    test(`30 labels at ${width}px`, async () => {
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
      const labels = Array.from(
        {length: 30},
        (_, index) =>
          `Check-${String.fromCharCode(
            65 + Math.floor(index / 26)
          )}-${String.fromCharCode(65 + (index % 26))}`
      );
      labels[28] = 'Code-Review';
      labels[29] = 'Verified';
      element.changes = createChanges(2).map((change, index) => {
        return {
          ...change,
          subject: 'A change with thirty submit requirements',
          owner: {...change.owner, name: 'Monty Taylor'},
          project: 'openstack/nova' as RepoName,
          submit_requirements: labels
            .filter((_, labelIndex) => index === 0 || labelIndex % 2 === 0)
            .map(name => {
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
      const headers = Array.from(
        section.shadowRoot!.querySelectorAll<HTMLElement>(
          '.groupTitle .label:not(.labelOverflow)'
        )
      );
      assert.lengthOf(headers, 30);
      for (const row of rows) {
        const votes = Array.from(
          row.shadowRoot!.querySelectorAll<HTMLElement>(
            '.label:not(.labelOverflow)'
          )
        );
        assert.lengthOf(votes, 30);
        if (width <= 800) {
          assert.deepEqual(
            votes
              .filter(cell => cell.getBoundingClientRect().width > 0)
              .map(cell => Math.round(cell.getBoundingClientRect().left)),
            headers
              .filter(cell => cell.getBoundingClientRect().width > 0)
              .map(cell => Math.round(cell.getBoundingClientRect().left))
          );
          assert.isAtMost(row.scrollWidth, row.clientWidth);
          const voteLines = new Set(
            votes
              .filter(cell => cell.getBoundingClientRect().width > 0)
              .map(cell => Math.round(cell.getBoundingClientRect().top))
          );
          const headerLines = new Set(
            headers
              .filter(cell => cell.getBoundingClientRect().width > 0)
              .map(cell => Math.round(cell.getBoundingClientRect().top))
          );
          assert.equal(voteLines.size, 1);
          const visibleHeaders = headers
            .filter(cell => cell.getBoundingClientRect().width > 0)
            .sort(
              (a, b) =>
                a.getBoundingClientRect().left - b.getBoundingClientRect().left
            );
          assert.equal(visibleHeaders[0].title, 'Code-Review');
          assert.equal(visibleHeaders[1].title, 'Verified');
          assert.lengthOf(visibleHeaders, width === 390 ? 13 : 25);
          assert.isAbove(
            row
              .shadowRoot!.querySelector('.labelOverflow')!
              .getBoundingClientRect().width,
            0
          );
          assert.equal(headerLines.size, voteLines.size);
        }
      }
      assert.isAtMost(element.getBoundingClientRect().right, width);
      if (width > 800) {
        assert.isAbove(element.scrollWidth, element.clientWidth);
      }
      await visualDiff(element, `gr-change-list-30-labels-${width}px`);
      await visualDiffDarkTheme(element, `gr-change-list-30-labels-${width}px`);
      if (width === 1000) {
        await setViewport({width: 700, height: 900});
        await waitUntil(() =>
          rows.every(row => row.shadowRoot!.querySelector('.votes'))
        );
        await setViewport({width: 1000, height: 900});
        await waitUntil(
          () =>
            rows.every(row => !row.shadowRoot!.querySelector('.votes')) &&
            section.shadowRoot!.querySelectorAll('.groupTitle .label')
              .length === 30
        );
        assert.lengthOf(
          section.shadowRoot!.querySelectorAll('.groupTitle .label'),
          30
        );
      }
      if (width === 700) {
        await setViewport({width: 390, height: 900});
        await waitUntil(
          () =>
            Array.from(
              section.shadowRoot!.querySelectorAll(
                '.groupTitle .label:not(.labelOverflow)'
              )
            ).filter(cell => cell.getBoundingClientRect().width > 0).length ===
            13
        );
        await section.updateComplete;
        await Promise.all(rows.map(row => row.updateComplete));
      }
    });
  }
});
