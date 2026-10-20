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
  createApproval,
  createChange,
  createServerInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {createDefaultPreferences} from '../../../constants/constants';
import {userModelToken} from '../../../models/user/user-model';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {
  BranchName,
  ChangeInfo,
  CommitId,
  NumericChangeId,
  RepoName,
  Timestamp,
} from '../../../types/common';
import {SubmitRequirementStatus} from '../../../api/rest-api';
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
    await setViewport({width: 800, height: 600});
    element = await fixture(html`<gr-change-list></gr-change-list>`);
    element.changes = createChanges(5);
    await element.updateComplete;
    testResolver(userModelToken).setPreferences({
      ...createDefaultPreferences(),
      threaded_changes: true,
    });
    await element.updateComplete;
  });

  function threadedChanges() {
    const changes = createChanges(8);
    const subjects = [
      'Introduce protocol support',
      'Add client bindings',
      'Document the new protocol',
      'Exercise the client end to end',
      'Update unrelated release notes',
      'Add an independent feature',
      'Prepare shared infrastructure',
      'Follow up after the protocol update',
    ];
    changes.forEach((change, index) => {
      change.subject = subjects[index];
      change.current_revision = `revision-${index}` as CommitId;
      const parentIndex =
        index === 0
          ? 6
          : index === 7
          ? 3
          : index === 1 || index === 2
          ? 0
          : index === 3
          ? 1
          : undefined;
      change.indexed_dependencies = {
        revision: change.current_revision,
        patch_set_number: 1,
        parents:
          parentIndex === undefined
            ? []
            : [
                {
                  change_number: changes[parentIndex]._number,
                  patch_set_number: 1,
                  commit_id: `revision-${parentIndex}` as CommitId,
                },
              ],
      };
    });
    return [
      changes[3],
      changes[4],
      changes[2],
      changes[0],
      changes[1],
      changes[5],
      changes[6],
      changes[7],
    ];
  }

  test('threaded dependencies', async () => {
    await setViewport({width: 1400, height: 900});
    element.changes = threadedChanges();
    await element.updateComplete;
    await visualDiff(element, 'gr-change-list-threaded');
    await visualDiffDarkTheme(element, 'gr-change-list-threaded');
  });

  test('threaded dependencies on mobile', async () => {
    await setViewport({width: 390, height: 900});
    element.changes = threadedChanges();
    await element.updateComplete;
    await visualDiff(element, 'gr-change-list-threaded-mobile');
  });

  test('deep threads retain their connectors as the subject column narrows', async () => {
    const changes = createChanges(24);
    changes.forEach((change, index) => {
      change.subject = `Stack member ${index + 1}: keep the connector visible`;
      change.current_revision = `deep-${index}` as CommitId;
      change.indexed_dependencies = {
        revision: change.current_revision,
        patch_set_number: 1,
        parents: index
          ? [
              {
                change_number: changes[index - 1]._number,
                patch_set_number: 1,
                commit_id: `deep-${index - 1}` as CommitId,
              },
            ]
          : [],
      };
    });
    element.changes = [...changes].reverse();
    for (const width of [1400, 1000, 801, 390]) {
      await setViewport({width, height: 1200});
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
      const threads = rows.flatMap(row =>
        Array.from(row.shadowRoot!.querySelectorAll('gr-change-thread'))
      );
      assert.lengthOf(threads, 23);
      let trackWidth: number | undefined;
      for (const thread of threads) {
        await thread.updateComplete;
        const bounds = thread.getBoundingClientRect();
        const rails = Array.from(thread.shadowRoot!.querySelectorAll('.rail'));
        for (const rail of rails) {
          const rect = rail.getBoundingClientRect();
          trackWidth ??= rect.width;
          assert.closeTo(rect.width, trackWidth, 0.5);
          assert.isAbove(rect.width, 1);
          assert.isAtLeast(rect.left, bounds.left - 0.5);
          assert.isAtMost(rect.right, bounds.right + 0.5);
        }
      }
      if (width === 1000 || width === 390) {
        await visualDiff(element, `gr-change-list-deep-thread-${width}px`);
      }
    }
  });

  test('cross-project dependency edges', async () => {
    await setViewport({width: 1400, height: 900});
    element.changes = threadedChanges().map(change => {
      return {
        ...change,
        project: (change._number === 1
          ? 'example/protocol'
          : 'example/client') as RepoName,
        indexed_dependencies: undefined,
      };
    });
    element.dependencyEdges = [
      {from: 1, to: 7, type: 'depends-on'},
      {from: 8, to: 4, type: 'depends-on'},
      {from: 2, to: 1, type: 'depends-on'},
      {from: 3, to: 1, type: 'depends-on'},
      {from: 4, to: 2, type: 'git'},
    ];
    await element.updateComplete;
    await visualDiff(element, 'gr-change-list-cross-project-threaded');
  });

  test('basic list', async () => {
    await visualDiff(element, 'gr-change-list');
    await visualDiffDarkTheme(element, 'gr-change-list');
  });

  test('self-contained mobile votes', async () => {
    await setViewport({width: 390, height: 900});
    element.changes = createChanges(5).map((change, index) => {
      const value = [-2, -1, 1, 2, 0][index];
      return {
        ...change,
        project: 'openstack/nova' as RepoName,
        subject: [
          'Blocked change',
          'Needs improvement',
          'Recommended change',
          'Approved change',
          'Waiting for votes',
        ][index],
        owner: {...change.owner, name: 'Monty Taylor'},
        labels: {
          'Code-Review': {
            values: {
              '-2': 'Block',
              '-1': 'Dislike',
              '0': 'Neutral',
              '+1': 'Recommend',
              '+2': 'Approve',
            },
            all: [{...createApproval(), value}],
          },
        },
        submit_requirements: [
          {
            ...createSubmitRequirementResultInfo('label:Code-Review=MAX'),
            name: 'Code-Review',
            status:
              value === 2
                ? SubmitRequirementStatus.SATISFIED
                : SubmitRequirementStatus.UNSATISFIED,
          },
        ],
      };
    });
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
    await visualDiff(element, 'gr-change-list-mobile-vote-badges');
    await visualDiffDarkTheme(element, 'gr-change-list-mobile-vote-badges');
  });

  for (const width of [390, 700, 801, 1000, 1400]) {
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
          ...(width <= 800 && index === 0
            ? {
                attention_set: {
                  [change.owner._account_id!]: {account: change.owner},
                },
                branch: 'stable/very-long-branch-name' as BranchName,
                reviewers: {
                  REVIEWER: [
                    {
                      ...createAccountDetailWithIdNameAndEmail(2),
                      name: 'Josh',
                    },
                  ],
                },
              }
            : {}),
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
        assert.isAtLeast(
          subject.getBoundingClientRect().top,
          first.querySelector('.change-header')!.getBoundingClientRect().bottom
        );
        assert.equal(
          first.querySelector('.change-header')!.getBoundingClientRect().left,
          subject.getBoundingClientRect().left
        );
        assert.isAtLeast(
          first.querySelector('.change-metadata')!.getBoundingClientRect().top,
          subject.getBoundingClientRect().bottom
        );
        const headerStyle = getComputedStyle(
          first.querySelector('.change-header')!
        );
        assert.isBelow(
          parseFloat(headerStyle.fontSize),
          parseFloat(getComputedStyle(subject).fontSize)
        );
        assert.isEmpty(
          Array.from(
            section.shadowRoot!.querySelectorAll('.groupTitle .label')
          ).filter(cell => cell.getBoundingClientRect().width > 0)
        );
        assert.isTrue(
          Array.from(
            first.querySelectorAll('gr-change-list-column-requirement')
          ).every(badge => badge.compact)
        );
        const repo = first.querySelector('.repo')!;
        assert.isAbove(repo.getBoundingClientRect().width, 0);
        const repoLink = repo.querySelector<HTMLElement>('.fullRepo')!;
        assert.isAbove(repoLink.getBoundingClientRect().width, 0);
        assert.equal(repoLink.textContent?.trim(), element.changes[0].project);
        assert.isAtMost(repoLink.scrollWidth, repoLink.clientWidth);
        assert.isAtLeast(
          repo.getBoundingClientRect().left,
          number.getBoundingClientRect().right
        );
        assert.isAtMost(
          repo.getBoundingClientRect().bottom,
          subject.getBoundingClientRect().top
        );
        const branch = first.querySelector('.change-header .branch')!;
        assert.equal(branch.previousElementSibling, repo);
        assert.isNull(first.querySelector('.change-metadata .branch'));
        const branchLink = branch.querySelector<HTMLElement>('a')!;
        assert.equal(branchLink.textContent?.trim(), element.changes[0].branch);
        assert.isAbove(branchLink.clientWidth, 0);
        assert.isAtMost(branchLink.scrollWidth, branchLink.clientWidth);
        assert.isAtLeast(
          branch.getBoundingClientRect().left,
          repo.getBoundingClientRect().right
        );
        assert.isAtMost(
          branch.getBoundingClientRect().bottom,
          subject.getBoundingClientRect().top
        );
        const accountLabel = first.querySelector('gr-account-label')!;
        const name = accountLabel.shadowRoot!.querySelector('.name')!;
        assert.isBelow(
          parseFloat(getComputedStyle(name).fontSize),
          parseFloat(getComputedStyle(subject).fontSize)
        );
        assert.equal(getComputedStyle(name).fontWeight, '500');
        assert.isAbove(name.clientWidth, 100);
        assert.isAtMost(name.scrollWidth, name.clientWidth);
        assert.isAtMost(element.scrollWidth, element.clientWidth);
      }
      if (width > 800) {
        for (const row of rows) {
          assert.isNull(row.shadowRoot!.querySelector('.change-header'));
          assert.isNull(row.shadowRoot!.querySelector('.change-subject'));
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
        assert.lengthOf(votes, width <= 800 && row === rows[1] ? 15 : 30);
        if (width <= 800) {
          assert.isAtMost(row.scrollWidth, row.clientWidth);
          const visibleVotes = votes
            .filter(cell => cell.getBoundingClientRect().width > 0)
            .sort(
              (a, b) =>
                a.getBoundingClientRect().left - b.getBoundingClientRect().left
            );
          assert.lengthOf(visibleVotes, 5);
          assert.equal(
            visibleVotes[0].querySelector('gr-change-list-column-requirement')!
              .labelName,
            'Code-Review'
          );
          assert.equal(
            visibleVotes[1].querySelector('gr-change-list-column-requirement')!
              .labelName,
            row === rows[0] ? 'Verified' : 'Check-A-A'
          );
          assert.isEmpty(
            headers.filter(cell => cell.getBoundingClientRect().width > 0)
          );
          assert.isAbove(
            row
              .shadowRoot!.querySelector('.labelOverflow')!
              .getBoundingClientRect().width,
            0
          );
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
        await waitUntil(() =>
          rows.every(
            row =>
              Array.from(
                row.shadowRoot!.querySelectorAll('.label:not(.labelOverflow)')
              ).filter(cell => cell.getBoundingClientRect().width > 0)
                .length === 5
          )
        );
        await section.updateComplete;
        await Promise.all(rows.map(row => row.updateComplete));
      }
    });
  }
});
