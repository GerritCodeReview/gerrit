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
  createRelatedChangeAndCommitInfo,
  createRevision,
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
import {ParsedChangeInfo} from '../../../types/types';
import './gr-related-changes-list';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit';
import {
  query,
  queryAndAssert,
  resetPlugins,
  stubRestApi,
} from '../../../test/test-utils';
import {GrRelatedChangesList} from './gr-related-changes-list';
import {SinonStubbedMember} from 'sinon/pkg/sinon-esm';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
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

  test('connected revisions', () => {
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      revisions: {
        e3c6d60783bfdec9ebae7dcfec4662360433449e: createRevision(1),
        '26e5e4c9c7ae31cbd876271cca281ce22b413997': createRevision(2),
        bf7884d695296ca0c91702ba3e2bc8df0f69a907: createRevision(7),
        b5fc49f2e67d1889d5275cac04ad3648f2ec7fe3: createRevision(5),
        d6bcee67570859ccb684873a85cf50b1f0e96fda: createRevision(6),
        cc960918a7f90388f4a9e05753d0f7b90ad44546: createRevision(3),
        '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6': createRevision(4),
      },
    };
    let patchNum = 7 as PatchSetNum;
    let relatedChanges: RelatedChangeAndCommitInfo[] = [
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '2cebeedfb1e80f4b872d0a13ade529e70652c0c8'
          ),
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd' as CommitId,
              subject: 'subject1',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '87ed20b241576b620bbaa3dfd47715ce6782b7dd'
          ),
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb' as CommitId,
              subject: 'subject2',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb'
          ),
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae' as CommitId,
              subject: 'subject3',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'b0ccb183494a8e340b8725a2dc553967d61e6dae'
          ),
          parents: [
            {
              commit: 'bf7884d695296ca0c91702ba3e2bc8df0f69a907' as CommitId,
              subject: 'subject4',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'bf7884d695296ca0c91702ba3e2bc8df0f69a907'
          ),
          parents: [
            {
              commit: '613bc4f81741a559c6667ac08d71dcc3348f73ce' as CommitId,
              subject: 'subject5',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '613bc4f81741a559c6667ac08d71dcc3348f73ce'
          ),
          parents: [
            {
              commit: '455ed9cd27a16bf6991f04dcc57ef575dc4d5e75' as CommitId,
              subject: 'subject6',
            },
          ],
        },
      },
    ];

    let connectedChanges = element._computeConnectedRevisions(
      change,
      patchNum,
      relatedChanges
    );
    assert.deepEqual(connectedChanges, [
      '613bc4f81741a559c6667ac08d71dcc3348f73ce',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'b0ccb183494a8e340b8725a2dc553967d61e6dae',
      '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
      '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
      '2cebeedfb1e80f4b872d0a13ade529e70652c0c8',
    ]);

    patchNum = 4 as PatchSetNum;
    relatedChanges = [
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '2cebeedfb1e80f4b872d0a13ade529e70652c0c8'
          ),
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '87ed20b241576b620bbaa3dfd47715ce6782b7dd'
          ),
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb'
          ),
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b'
          ),
          parents: [
            {
              commit: '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6'
          ),
          parents: [
            {
              commit: 'af815dac54318826b7f1fa468acc76349ffc588e' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'af815dac54318826b7f1fa468acc76349ffc588e'
          ),
          parents: [
            {
              commit: '58f76e406e24cb8b0f5d64c7f5ac1e8616d0a22c' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
    ];

    connectedChanges = element._computeConnectedRevisions(
      change,
      patchNum,
      relatedChanges
    );
    assert.deepEqual(connectedChanges, [
      'af815dac54318826b7f1fa468acc76349ffc588e',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b',
    ]);
  });

  test('_changesEqual', () => {
    const change1: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 0 as NumericChangeId,
    };
    const change2: ChangeInfo = {
      ...createChange(),
      change_id: '456' as ChangeId,
      _number: 1 as NumericChangeId,
    };
    const change3: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 2 as NumericChangeId,
    };
    const change4: RelatedChangeAndCommitInfo = {
      ...createRelatedChangeAndCommitInfo(),
      change_id: '123' as ChangeId,
      _change_number: 1 as NumericChangeId,
    };

    assert.isTrue(element._changesEqual(change1, change1));
    assert.isFalse(element._changesEqual(change1, change2));
    assert.isFalse(element._changesEqual(change1, change3));
    assert.isTrue(element._changesEqual(change2, change4));
  });

  test('_getChangeNumber', () => {
    const change1: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 0 as NumericChangeId,
    };
    const change2: ChangeInfo = {
      ...createChange(),
      change_id: '456' as ChangeId,
      _number: 1 as NumericChangeId,
    };
    assert.equal(element._getChangeNumber(change1), 0);
    assert.equal(element._getChangeNumber(change2), 1);
  });

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

  suite('get conflicts tests', () => {
    let element: GrRelatedChangesList;
    let conflictsStub: SinonStubbedMember<RestApiService['getChangeConflicts']>;

    setup(() => {
      element = basicFixture.instantiate();
      conflictsStub = stubRestApi('getChangeConflicts').returns(
        Promise.resolve(undefined)
      );
    });

    test('request conflicts if open and mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = true;
      element.reload();
      assert.isTrue(conflictsStub.called);
    });

    test('does not request conflicts if closed and mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('does not request conflicts if open and not mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('doesnt request conflicts if closed and not mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });
  });

  test('_calculateHasParent', () => {
    const changeId = '123' as ChangeId;
    const relatedChanges: RelatedChangeAndCommitInfo[] = [];

    assert.equal(element._calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '123' as ChangeId,
    });
    assert.equal(element._calculateHasParent(changeId, relatedChanges), false);

    relatedChanges.push({
      ...createRelatedChangeAndCommitInfo(),
      change_id: '234' as ChangeId,
    });
    assert.equal(element._calculateHasParent(changeId, relatedChanges), true);
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

  test('_computeChangeURL uses GerritNav', () => {
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChangeById');
    element._computeChangeURL(
      123 as NumericChangeId,
      'abc/def' as RepoName,
      12 as PatchSetNum
    );
    assert.isTrue(getUrlStub.called);
  });

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

  suite('gr-related-changes-list plugin tests', () => {
    let element: GrRelatedChangesList;

    setup(() => {
      resetPlugins();
      element = basicFixture.instantiate();
    });

    teardown(() => {
      resetPlugins();
    });

    test('endpoint params', done => {
      element.change = {...createParsedChange(), labels: {}};
      interface RelatedChangesListGrEndpointDecorator
        extends GrEndpointDecorator {
        plugin: PluginApi;
        change: ParsedChangeInfo;
      }
      let hookEl: RelatedChangesListGrEndpointDecorator;
      let plugin: PluginApi;
      pluginApi.install(
        p => {
          plugin = p;
          plugin
            .hook('related-changes-section')
            .getLastAttached()
            .then(el => (hookEl = el as RelatedChangesListGrEndpointDecorator));
        },
        '0.1',
        'http://some/plugins/url1.js'
      );
      getPluginLoader().loadPlugins([]);
      flush(() => {
        assert.strictEqual(hookEl.plugin, plugin);
        assert.strictEqual(hookEl.change, element.change);
        done();
      });
    });
  });

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
