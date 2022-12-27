/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import {
  SubmitRequirementResultInfo,
  NumericChangeId,
} from '../../../api/rest-api';
import '../../../test/common-test-setup';
import {
  createAccountWithId,
  createChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createServerInfo,
} from '../../../test/test-data-generators';
import {
  query,
  queryAndAssert,
  stubRestApi,
  waitUntilObserved,
} from '../../../test/test-utils';
import {
  AccountId,
  BranchName,
  ChangeInfo,
  RepoName,
  TopicName,
} from '../../../types/common';
import {StandardLabels} from '../../../utils/label-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import './gr-change-list-item';
import {GrChangeListItem} from './gr-change-list-item';
import {
  DIProviderElement,
  wrapInProvider,
} from '../../../models/di-provider-element';
import {
  bulkActionsModelToken,
  BulkActionsModel,
} from '../../../models/bulk-actions/bulk-actions-model';
import {createTestAppContext} from '../../../test/test-app-context-init';
import {ColumnNames} from '../../../constants/constants';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-change-list-item tests', () => {
  const account = createAccountWithId();
  const change: ChangeInfo = {
    ...createChange(),
    project: 'a/test/repo' as RepoName,
    topic: 'test-topic' as TopicName,
    branch: 'test-branch' as BranchName,
  };

  let element: GrChangeListItem;
  let bulkActionsModel: BulkActionsModel;

  setup(async () => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));

    bulkActionsModel = new BulkActionsModel(
      createTestAppContext().restApiService
    );
    element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<gr-change-list-item></gr-change-list-item>`,
          bulkActionsModelToken,
          bulkActionsModel
        )
      )
    ).element as GrChangeListItem;
    await element.updateComplete;
  });

  test('no hidden columns', async () => {
    element.visibleChangeTableColumns = [
      ColumnNames.SUBJECT,
      ColumnNames.STATUS,
      ColumnNames.OWNER,
      ColumnNames.REVIEWERS,
      ColumnNames.COMMENTS,
      ColumnNames.REPO,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
      ColumnNames.SIZE,
      ColumnNames.STATUS2,
    ];

    await element.updateComplete;

    for (const column of Object.values(ColumnNames)) {
      const elementClass = '.' + column.trim().toLowerCase();
      assert.isFalse(
        queryAndAssert(element, elementClass).hasAttribute('hidden')
      );
    }
  });

  suite('checkbox', () => {
    test('bulk actions checkboxes', async () => {
      element.change = {...createChange(), _number: 1 as NumericChangeId};
      bulkActionsModel.sync([element.change]);
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > .selectionLabel > input'
      );
      checkbox.click();
      let selectedChangeNums = await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 1
      );

      assert.deepEqual(selectedChangeNums, [1]);

      checkbox.click();
      selectedChangeNums = await waitUntilObserved(
        bulkActionsModel.selectedChangeNums$,
        s => s.length === 0
      );

      assert.deepEqual(selectedChangeNums, []);
    });

    test('checkbox click calls list selection callback', async () => {
      const selectionCallback = sinon.stub();
      element.triggerSelectionCallback = selectionCallback;
      element.globalIndex = 5;
      element.change = {...createChange(), _number: 1 as NumericChangeId};
      bulkActionsModel.sync([element.change]);
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > .selectionLabel > input'
      );
      checkbox.click();
      await element.updateComplete;

      assert.isTrue(selectionCallback.calledWith(5));
    });

    test('checkbox state updates with model updates', async () => {
      element.requestUpdate();
      await element.updateComplete;

      element.change = {...createChange(), _number: 1 as NumericChangeId};
      bulkActionsModel.sync([element.change]);
      bulkActionsModel.addSelectedChangeNum(element.change._number);
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > .selectionLabel > input'
      );
      assert.isTrue(checkbox.checked);

      bulkActionsModel.removeSelectedChangeNum(element.change._number);
      await element.updateComplete;

      assert.isFalse(checkbox.checked);
    });

    test('checkbox state updates with change id update', async () => {
      element.requestUpdate();
      await element.updateComplete;

      const changes = [
        {...createChange(), _number: 1 as NumericChangeId},
        {...createChange(), _number: 2 as NumericChangeId},
      ];
      element.change = changes[0];
      bulkActionsModel.sync(changes);
      bulkActionsModel.addSelectedChangeNum(element.change._number);
      await element.updateComplete;

      const checkbox = queryAndAssert<HTMLInputElement>(
        element,
        '.selection > .selectionLabel > input'
      );
      assert.isTrue(checkbox.checked);

      element.change = changes[1];
      await element.updateComplete;

      assert.isFalse(checkbox.checked);
    });
  });

  test('repo column hidden', async () => {
    element.visibleChangeTableColumns = [
      ColumnNames.SUBJECT,
      ColumnNames.STATUS,
      ColumnNames.OWNER,
      ColumnNames.REVIEWERS,
      ColumnNames.COMMENTS,
      ColumnNames.BRANCH,
      ColumnNames.UPDATED,
      ColumnNames.SIZE,
      ColumnNames.STATUS2,
    ];

    await element.updateComplete;

    for (const column of Object.values(ColumnNames)) {
      const elementClass = '.' + column.trim().toLowerCase();
      if (column === 'Repo') {
        assert.isNotOk(query(element, elementClass));
      } else {
        assert.isOk(query(element, elementClass));
      }
    }
  });

  function checkComputeReviewers(
    userId: number | undefined,
    reviewerIds: number[],
    reviewerNames: (string | undefined)[],
    attSetIds: number[],
    expected: number[]
  ) {
    element.account = userId ? {_account_id: userId as AccountId} : null;
    element.change = {
      ...change,
      owner: {
        _account_id: 99 as AccountId,
      },
      reviewers: {
        REVIEWER: [],
      },
      attention_set: {},
    };
    for (let i = 0; i < reviewerIds.length; i++) {
      element.change.reviewers.REVIEWER!.push({
        _account_id: reviewerIds[i] as AccountId,
        name: reviewerNames[i],
      });
    }
    attSetIds.forEach(id => (element.change!.attention_set![id] = {account}));

    const actual = element.computeReviewers().map(r => r._account_id);
    assert.deepEqual(actual, expected as AccountId[]);
  }

  test('compute reviewers', () => {
    checkComputeReviewers(undefined, [], [], [], []);
    checkComputeReviewers(1, [], [], [], []);
    checkComputeReviewers(1, [2], ['a'], [], [2]);
    checkComputeReviewers(1, [2, 3], [undefined, 'a'], [], [2, 3]);
    checkComputeReviewers(1, [2, 3], ['a', undefined], [], [3, 2]);
    checkComputeReviewers(1, [99], ['owner'], [], []);
    checkComputeReviewers(
      1,
      [2, 3, 4, 5],
      ['b', 'a', 'd', 'c'],
      [3, 4],
      [3, 4, 2, 5]
    );
    checkComputeReviewers(
      1,
      [2, 3, 1, 4, 5],
      ['b', 'a', 'x', 'd', 'c'],
      [3, 4],
      [1, 3, 4, 2, 5]
    );
  });

  test('random column does not exist', async () => {
    element.visibleChangeTableColumns = ['Bad'];

    await element.updateComplete;
    const elementClass = '.bad';
    assert.isNotOk(query(element, elementClass));
  });

  test('TShirt sizing tooltip', () => {
    element.change = {
      ...change,
      insertions: NaN,
      deletions: NaN,
    };
    assert.equal(element.computeSizeTooltip(), 'Size unknown');
    element.change = {
      ...change,
      insertions: 0,
      deletions: 0,
    };
    assert.equal(element.computeSizeTooltip(), 'Size unknown');
    element.change = {
      ...change,
      insertions: 1,
      deletions: 2,
    };
    assert.equal(element.computeSizeTooltip(), 'added 1, removed 2 lines');
  });

  test('TShirt sizing', () => {
    element.change = {
      ...change,
      insertions: NaN,
      deletions: NaN,
    };
    assert.equal(element.computeChangeSize(), null);

    element.change = {
      ...change,
      insertions: 1,
      deletions: 1,
    };
    assert.equal(element.computeChangeSize(), 'XS');

    element.change = {
      ...change,
      insertions: 9,
      deletions: 1,
    };
    assert.equal(element.computeChangeSize(), 'S');

    element.change = {
      ...change,
      insertions: 10,
      deletions: 200,
    };
    assert.equal(element.computeChangeSize(), 'M');

    element.change = {
      ...change,
      insertions: 99,
      deletions: 900,
    };
    assert.equal(element.computeChangeSize(), 'L');

    element.change = {
      ...change,
      insertions: 99,
      deletions: 999,
    };
    assert.equal(element.computeChangeSize(), 'XL');
  });

  test('clicking item navigates to change', async () => {
    const setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');

    element.change = change;
    await element.updateComplete;

    element.click();
    await element.updateComplete;

    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/a/test/repo/+/42');
  });

  test('renders', async () => {
    const change = createChange();
    bulkActionsModel.sync([change]);
    bulkActionsModel.addSelectedChangeNum(change._number);
    element.showStar = true;
    element.showNumber = true;
    element.account = createAccountWithId(1);
    element.config = createServerInfo();
    element.change = change;
    await element.updateComplete;
    assert.isTrue(element.hasAttribute('checked'));

    // TODO: Check table elements. The shadowDom helper does not understand
    // tables interacting with display: contents, even wrapping the element in a
    // table, does not help.
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <label class="selectionLabel">
          <input type="checkbox" />
        </label>
        <gr-change-star></gr-change-star>
        <a href="/c/test-project/+/42">42</a>
        <a href="/c/test-project/+/42" title="Test subject">
          <div class="container">
            <div class="content">Test subject</div>
            <div class="spacer">Test subject</div>
            <span></span>
          </div>
        </a>
        <span class="placeholder"> -- </span>
        <gr-account-label
          deselected=""
          clickable=""
          highlightattention=""
        ></gr-account-label>
        <div></div>
        <span></span>
        <a class="fullRepo" href="/q/project:test-project+status:open">
          test-project
        </a>
        <a
          class="truncatedRepo"
          href="/q/project:test-project+status:open"
          title="test-project"
        >
          test-project
        </a>
        <a href="/q/project:test-project+branch:test-branch"> test-branch </a>
        <gr-date-formatter withtooltip=""></gr-date-formatter>
        <gr-date-formatter withtooltip=""></gr-date-formatter>
        <gr-date-formatter
          forcerelative=""
          relativeoptionnoago=""
          withtooltip=""
        >
        </gr-date-formatter>
        <gr-tooltip-content has-tooltip="" title="Size unknown">
          <span class="placeholder"> -- </span>
        </gr-tooltip-content>
        <gr-change-list-column-requirements-summary>
        </gr-change-list-column-requirements-summary>
      `
    );
  });

  test('renders requirement with new submit requirements', async () => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: StandardLabels.CODE_REVIEW,
      submittability_expression_result: createSubmitRequirementExpressionInfo(),
    };
    const change: ChangeInfo = {
      ...createChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
      unresolved_comment_count: 1,
    };
    const element = (
      await fixture<DIProviderElement>(
        wrapInProvider(
          html`<gr-change-list-item
            .change=${change}
            .labelNames=${[StandardLabels.CODE_REVIEW]}
          ></gr-change-list-item>`,
          bulkActionsModelToken,
          bulkActionsModel
        )
      )
    ).element as GrChangeListItem;

    const requirement = queryAndAssert(element, '.requirement');
    assert.dom.equal(
      requirement,
      /* HTML */ ` <gr-change-list-column-requirement>
      </gr-change-list-column-requirement>`
    );
  });
});
