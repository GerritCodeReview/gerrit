/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import {ChangeStatus} from '../../../constants/constants';
import '../../../test/common-test-setup-karma';
import {
  createChange,
  createCommit,
  createCommitInfoWithRequiredCommit,
  createParsedChange,
} from '../../../test/test-data-generators';
import {
  ChangeId,
  ChangeInfo,
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
  RepoName,
} from '../../../types/common';
import './gr-related-changes-list';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit';
import {query, queryAndAssert} from '../../../test/test-utils';
import {GrRelatedChangesList} from './gr-related-changes-list';
import {_testOnly_resetEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';

const pluginApi = _testOnly_initGerritPluginApi();

const basicFixture = fixtureFromElement('gr-related-changes-list');

suite('gr-related-changes-list tests', () => {
  let element: GrRelatedChangesList;

  setup(() => {
    // Since pluginEndpoints are global, must reset state.
    _testOnly_resetEndpoints();
    element = basicFixture.instantiate();
  });

  // obsolete
  test('event for section loaded fires for each section ', () => {
    const loadedStub = sinon.stub();
    element.patchNum = 7 as PatchSetNum;
    element.change = {
      ...createParsedChange(),
      change_id: '123' as ChangeId,
      status: ChangeStatus.NEW,
    };
    element.mergeable = true;
    element.addEventListener('new-section-loaded', loadedStub);

    return element.reload().then(() => {
      assert.equal(loadedStub.callCount, 4);
    });
  });

  // trivial
  suite('getChangeConflicts resolves undefined', () => {
    let element: GrRelatedChangesList;

    setup(() => {
      element = basicFixture.instantiate();
    });

    test('_conflicts are an empty array', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = true;
      element.reload();
      assert.equal(element._conflicts.length, 0);
    });
  });

  suite('hidden attribute and update event', () => {
    const changes: ChangeInfo[] = [
      {
        ...createChange(),
        project: 'foo/bar' as RepoName,
        change_id: 'Ideadbeef' as ChangeId,
        status: ChangeStatus.NEW,
      },
    ];
    const relatedChanges: RelatedChangeAndCommitInfo[] = [
      {
        ...createCommitInfoWithRequiredCommit(),
        project: 'foo/bar' as RepoName,
        change_id: 'Ideadbeef' as ChangeId,
        commit: {
          ...createCommit(),
          commit: 'deadbeef' as CommitId,
          parents: [
            {
              commit: 'abc123' as CommitId,
              subject: 'abc123',
            },
          ],
          subject: 'do that thing',
        },
        _change_number: 12345 as NumericChangeId,
        _revision_number: 1,
        _current_revision_number: 1,
        status: ChangeStatus.NEW,
      },
    ];

    // obsolete
    test('clear and empties', () => {
      element._relatedResponse = {changes: relatedChanges};
      element._submittedTogether = {
        changes,
        non_visible_changes: 0,
      };
      element._conflicts = changes;
      element._cherryPicks = changes;
      element._sameTopic = changes;

      element.hidden = false;
      element.clear();
      assert.isTrue(element.hidden);
      assert.equal(element._relatedResponse.changes.length, 0);
      assert.equal(element._submittedTogether?.changes.length, 0);
      assert.equal(element._conflicts.length, 0);
      assert.equal(element._cherryPicks.length, 0);
      assert.equal(element._sameTopic?.length, 0);
    });

    // obsolete
    test('update fires', () => {
      const updateHandler = sinon.stub();
      element.addEventListener('update', updateHandler);

      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isTrue(element.hidden);
      assert.isFalse(updateHandler.called);

      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        changes
      );
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
      updateHandler.reset();

      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isTrue(element.hidden);
      assert.isFalse(updateHandler.called);

      element._resultsChanged(
        {changes: []},
        {changes, non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
      updateHandler.reset();

      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 1},
        [],
        [],
        []
      );
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
    });

    suite('hiding and unhiding', () => {
      test('related response', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged(
          {changes: relatedChanges},
          {changes: [], non_visible_changes: 0},
          [],
          [],
          []
        );
        assert.isFalse(element.hidden);
      });

      test('submitted together', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged(
          {changes: []},
          {changes, non_visible_changes: 0},
          [],
          [],
          []
        );
        assert.isFalse(element.hidden);
      });

      test('conflicts', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged(
          {changes: []},
          {changes: [], non_visible_changes: 0},
          changes,
          [],
          []
        );
        assert.isFalse(element.hidden);
      });

      test('cherrypicks', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged(
          {changes: []},
          {changes: [], non_visible_changes: 0},
          [],
          changes,
          []
        );
        assert.isFalse(element.hidden);
      });

      test('same topic', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged(
          {changes: []},
          {changes: [], non_visible_changes: 0},
          [],
          [],
          changes
        );
        assert.isFalse(element.hidden);
      });
    });
  });

  // trivial
  test('_computeChangeURL uses GerritNav', () => {
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChangeById');
    element._computeChangeURL(
      123 as NumericChangeId,
      'abc/def' as RepoName,
      12 as PatchSetNum
    );
    assert.isTrue(getUrlStub.called);
  });

  // trivial
  suite('submitted together changes', () => {
    const change: ChangeInfo = {
      ...createChange(),
      project: 'foo/bar' as RepoName,
      change_id: 'Ideadbeef' as ChangeId,
      status: ChangeStatus.NEW,
    };

    test('_computeSubmittedTogetherClass', () => {
      assert.strictEqual(
        element._computeSubmittedTogetherClass(undefined),
        'hidden'
      );
      assert.strictEqual(
        element._computeSubmittedTogetherClass({
          changes: [],
          non_visible_changes: 0,
        }),
        'hidden'
      );
      assert.strictEqual(
        element._computeSubmittedTogetherClass({
          changes: [change],
          non_visible_changes: 0,
        }),
        ''
      );
      assert.strictEqual(
        element._computeSubmittedTogetherClass({
          changes: [],
          non_visible_changes: 0,
        }),
        'hidden'
      );
      assert.strictEqual(
        element._computeSubmittedTogetherClass({
          changes: [],
          non_visible_changes: 1,
        }),
        ''
      );
      assert.strictEqual(
        element._computeSubmittedTogetherClass({
          changes: [],
          non_visible_changes: 1,
        }),
        ''
      );
    });

    test('no submitted together changes', () => {
      flush();
      assert.include(element.$.submittedTogether.className, 'hidden');
    });

    test('no non-visible submitted together changes', () => {
      element._submittedTogether = {changes: [change], non_visible_changes: 0};
      flush();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.isUndefined(query(element, '.note'));
    });

    test('no visible submitted together changes', () => {
      // Technically this should never happen, but worth asserting the logic.
      element._submittedTogether = {changes: [], non_visible_changes: 1};
      flush();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.strictEqual(
        queryAndAssert<HTMLDivElement>(element, '.note').innerText.trim(),
        '(+ 1 non-visible change)'
      );
    });

    test('visible and non-visible submitted together changes', () => {
      element._submittedTogether = {changes: [change], non_visible_changes: 2};
      flush();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.strictEqual(
        queryAndAssert<HTMLDivElement>(element, '.note').innerText.trim(),
        '(+ 2 non-visible changes)'
      );
    });
  });

  // obsolete
  test('hiding and unhiding', done => {
    element.change = {...createParsedChange(), labels: {}};
    let hookEl: HTMLElement;
    let plugin;

    // No changes, and no plugin. The element is still hidden.
    element._resultsChanged(
      {changes: []},
      {changes: [], non_visible_changes: 0},
      [],
      [],
      []
    );
    assert.isTrue(element.hidden);
    pluginApi.install(
      p => {
        plugin = p;
        plugin
          .hook('related-changes-section')
          .getLastAttached()
          .then(el => (hookEl = el));
      },
      '0.1',
      'http://some/plugins/url2.js'
    );
    getPluginLoader().loadPlugins([]);
    flush(() => {
      // No changes, and plugin without hidden attribute. So it's visible.
      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isFalse(element.hidden);

      // No changes, but plugin with true hidden attribute. So it's invisible.
      hookEl.hidden = true;

      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isTrue(element.hidden);

      // No changes, and plugin with false hidden attribute. So it's visible.
      hookEl.hidden = false;
      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        []
      );
      assert.isFalse(element.hidden);

      // Hiding triggered by plugin itself
      hookEl.hidden = true;
      hookEl.dispatchEvent(
        new CustomEvent('new-section-loaded', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(element.hidden);

      // Unhiding triggered by plugin itself
      hookEl.hidden = false;
      hookEl.dispatchEvent(
        new CustomEvent('new-section-loaded', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isFalse(element.hidden);

      // Hiding plugin keeps list visible, if there are changes
      hookEl.hidden = false;
      const change = createChange();
      element._sameTopic = [change];
      element._resultsChanged(
        {changes: []},
        {changes: [], non_visible_changes: 0},
        [],
        [],
        [change]
      );
      assert.isFalse(element.hidden);
      hookEl.hidden = true;
      hookEl.dispatchEvent(
        new CustomEvent('new-section-loaded', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isFalse(element.hidden);

      done();
    });
  });
});
