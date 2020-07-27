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

import '../../../test/common-test-setup-karma.js';
import './gr-related-changes-list.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {resetPlugins} from '../../../test/test-utils.js';

const pluginApi = _testOnly_initGerritPluginApi();

const basicFixture = fixtureFromElement('gr-related-changes-list');

suite('gr-related-changes-list tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('connected revisions', () => {
    const change = {
      revisions: {
        'e3c6d60783bfdec9ebae7dcfec4662360433449e': {
          _number: 1,
        },
        '26e5e4c9c7ae31cbd876271cca281ce22b413997': {
          _number: 2,
        },
        'bf7884d695296ca0c91702ba3e2bc8df0f69a907': {
          _number: 7,
        },
        'b5fc49f2e67d1889d5275cac04ad3648f2ec7fe3': {
          _number: 5,
        },
        'd6bcee67570859ccb684873a85cf50b1f0e96fda': {
          _number: 6,
        },
        'cc960918a7f90388f4a9e05753d0f7b90ad44546': {
          _number: 3,
        },
        '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6': {
          _number: 4,
        },
      },
    };
    let patchNum = 7;
    let relatedChanges = [
      {
        commit: {
          commit: '2cebeedfb1e80f4b872d0a13ade529e70652c0c8',
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
            },
          ],
        },
      },
      {
        commit: {
          commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
            },
          ],
        },
      },
      {
        commit: {
          commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae',
            },
          ],
        },
      },
      {
        commit: {
          commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae',
          parents: [
            {
              commit: 'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
            },
          ],
        },
      },
      {
        commit: {
          commit: 'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
          parents: [
            {
              commit: '613bc4f81741a559c6667ac08d71dcc3348f73ce',
            },
          ],
        },
      },
      {
        commit: {
          commit: '613bc4f81741a559c6667ac08d71dcc3348f73ce',
          parents: [
            {
              commit: '455ed9cd27a16bf6991f04dcc57ef575dc4d5e75',
            },
          ],
        },
      },
    ];

    let connectedChanges =
        element._computeConnectedRevisions(change, patchNum, relatedChanges);
    assert.deepEqual(connectedChanges, [
      '613bc4f81741a559c6667ac08d71dcc3348f73ce',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'b0ccb183494a8e340b8725a2dc553967d61e6dae',
      '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
      '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
      '2cebeedfb1e80f4b872d0a13ade529e70652c0c8',
    ]);

    patchNum = 4;
    relatedChanges = [
      {
        commit: {
          commit: '2cebeedfb1e80f4b872d0a13ade529e70652c0c8',
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
            },
          ],
        },
      },
      {
        commit: {
          commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
            },
          ],
        },
      },
      {
        commit: {
          commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae',
            },
          ],
        },
      },
      {
        commit: {
          commit: 'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b',
          parents: [
            {
              commit: '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
            },
          ],
        },
      },
      {
        commit: {
          commit: '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
          parents: [
            {
              commit: 'af815dac54318826b7f1fa468acc76349ffc588e',
            },
          ],
        },
      },
      {
        commit: {
          commit: 'af815dac54318826b7f1fa468acc76349ffc588e',
          parents: [
            {
              commit: '58f76e406e24cb8b0f5d64c7f5ac1e8616d0a22c',
            },
          ],
        },
      },
    ];

    connectedChanges =
        element._computeConnectedRevisions(change, patchNum, relatedChanges);
    assert.deepEqual(connectedChanges, [
      'af815dac54318826b7f1fa468acc76349ffc588e',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b',
    ]);
  });

  test('_computeChangeContainerClass', () => {
    const change1 = {change_id: 123, _number: 0};
    const change2 = {change_id: 456, _change_number: 1};
    const change3 = {change_id: 123, _number: 2};

    assert.notEqual(element._computeChangeContainerClass(
        change1, change1).indexOf('thisChange'), -1);
    assert.equal(element._computeChangeContainerClass(
        change1, change2).indexOf('thisChange'), -1);
    assert.equal(element._computeChangeContainerClass(
        change1, change3).indexOf('thisChange'), -1);
  });

  test('_changesEqual', () => {
    const change1 = {change_id: 123, _number: 0};
    const change2 = {change_id: 456, _number: 1};
    const change3 = {change_id: 123, _number: 2};
    const change4 = {change_id: 123, _change_number: 1};

    assert.isTrue(element._changesEqual(change1, change1));
    assert.isFalse(element._changesEqual(change1, change2));
    assert.isFalse(element._changesEqual(change1, change3));
    assert.isTrue(element._changesEqual(change2, change4));
  });

  test('_getChangeNumber', () => {
    const change1 = {change_id: 123, _number: 0};
    const change2 = {change_id: 456, _change_number: 1};
    assert.equal(element._getChangeNumber(change1), 0);
    assert.equal(element._getChangeNumber(change2), 1);
  });

  test('event for section loaded fires for each section ', () => {
    const loadedStub = sinon.stub();
    element.patchNum = 7;
    element.change = {
      change_id: 123,
      status: 'NEW',
    };
    element.mergeable = true;
    element.addEventListener('new-section-loaded', loadedStub);
    sinon.stub(element, '_getRelatedChanges')
        .returns(Promise.resolve({changes: []}));
    sinon.stub(element, '_getSubmittedTogether')
        .returns(Promise.resolve());
    sinon.stub(element, '_getCherryPicks')
        .returns(Promise.resolve());
    sinon.stub(element, '_getConflicts')
        .returns(Promise.resolve());

    return element.reload().then(() => {
      assert.equal(loadedStub.callCount, 4);
    });
  });

  suite('_getConflicts resolves undefined', () => {
    let element;

    setup(() => {
      element = basicFixture.instantiate();

      sinon.stub(element, '_getRelatedChanges')
          .returns(Promise.resolve({changes: []}));
      sinon.stub(element, '_getSubmittedTogether')
          .returns(Promise.resolve());
      sinon.stub(element, '_getCherryPicks')
          .returns(Promise.resolve());
      sinon.stub(element, '_getConflicts')
          .returns(Promise.resolve());
    });

    test('_conflicts are an empty array', () => {
      element.patchNum = 7;
      element.change = {
        change_id: 123,
        status: 'NEW',
      };
      element.mergeable = true;
      element.reload();
      assert.equal(element._conflicts.length, 0);
    });
  });

  suite('get conflicts tests', () => {
    let element;
    let conflictsStub;

    setup(() => {
      element = basicFixture.instantiate();

      sinon.stub(element, '_getRelatedChanges')
          .returns(Promise.resolve({changes: []}));
      sinon.stub(element, '_getSubmittedTogether')
          .returns(Promise.resolve());
      sinon.stub(element, '_getCherryPicks')
          .returns(Promise.resolve());
      conflictsStub = sinon.stub(element, '_getConflicts')
          .returns(Promise.resolve());
    });

    test('request conflicts if open and mergeable', () => {
      element.patchNum = 7;
      element.change = {
        change_id: 123,
        status: 'NEW',
      };
      element.mergeable = true;
      element.reload();
      assert.isTrue(conflictsStub.called);
    });

    test('does not request conflicts if closed and mergeable', () => {
      element.patchNum = 7;
      element.change = {
        change_id: 123,
        status: 'MERGED',
      };
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('does not request conflicts if open and not mergeable', () => {
      element.patchNum = 7;
      element.change = {
        change_id: 123,
        status: 'NEW',
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('doesnt request conflicts if closed and not mergeable', () => {
      element.patchNum = 7;
      element.change = {
        change_id: 123,
        status: 'MERGED',
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });
  });

  test('_calculateHasParent', () => {
    const changeId = 123;
    const relatedChanges = [];

    assert.equal(element._calculateHasParent(changeId, relatedChanges),
        false);

    relatedChanges.push({change_id: 123});
    assert.equal(element._calculateHasParent(changeId, relatedChanges),
        false);

    relatedChanges.push({change_id: 234});
    assert.equal(element._calculateHasParent(changeId, relatedChanges),
        true);
  });

  suite('hidden attribute and update event', () => {
    const changes = [{
      project: 'foo/bar',
      change_id: 'Ideadbeef',
      commit: {
        commit: 'deadbeef',
        parents: [{commit: 'abc123'}],
        author: {},
        subject: 'do that thing',
      },
      _change_number: 12345,
      _revision_number: 1,
      _current_revision_number: 1,
      status: 'NEW',
    }];

    test('clear and empties', () => {
      element._relatedResponse = {changes};
      element._submittedTogether = {changes};
      element._conflicts = changes;
      element._cherryPicks = changes;
      element._sameTopic = changes;

      element.hidden = false;
      element.clear();
      assert.isTrue(element.hidden);
      assert.equal(element._relatedResponse.changes.length, 0);
      assert.equal(element._submittedTogether.changes.length, 0);
      assert.equal(element._conflicts.length, 0);
      assert.equal(element._cherryPicks.length, 0);
      assert.equal(element._sameTopic.length, 0);
    });

    test('update fires', () => {
      const updateHandler = sinon.stub();
      element.addEventListener('update', updateHandler);

      element._resultsChanged({}, {}, [], [], []);
      assert.isTrue(element.hidden);
      assert.isFalse(updateHandler.called);

      element._resultsChanged({}, {}, [], [], ['test']);
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
      updateHandler.reset();

      element._resultsChanged(
          {}, {changes: [], non_visible_changes: 0}, [], [], []);
      assert.isTrue(element.hidden);
      assert.isFalse(updateHandler.called);

      element._resultsChanged(
          {}, {changes: ['test'], non_visible_changes: 0}, [], [], []);
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
      updateHandler.reset();

      element._resultsChanged(
          {}, {changes: [], non_visible_changes: 1}, [], [], []);
      assert.isFalse(element.hidden);
      assert.isTrue(updateHandler.called);
    });

    suite('hiding and unhiding', () => {
      test('related response', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged({changes}, {}, [], [], []);
        assert.isFalse(element.hidden);
      });

      test('submitted together', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged({}, {changes}, [], [], []);
        assert.isFalse(element.hidden);
      });

      test('conflicts', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged({}, {}, changes, [], []);
        assert.isFalse(element.hidden);
      });

      test('cherrypicks', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged({}, {}, [], changes, []);
        assert.isFalse(element.hidden);
      });

      test('same topic', () => {
        assert.isTrue(element.hidden);
        element._resultsChanged({}, {}, [], [], changes);
        assert.isFalse(element.hidden);
      });
    });
  });

  test('_computeChangeURL uses GerritNav', () => {
    const getUrlStub = sinon.stub(GerritNav, 'getUrlForChangeById');
    element._computeChangeURL(123, 'abc/def', 12);
    assert.isTrue(getUrlStub.called);
  });

  suite('submitted together changes', () => {
    const change = {
      project: 'foo/bar',
      change_id: 'Ideadbeef',
      commit: {
        commit: 'deadbeef',
        parents: [{commit: 'abc123'}],
        author: {},
        subject: 'do that thing',
      },
      _change_number: 12345,
      _revision_number: 1,
      _current_revision_number: 1,
      status: 'NEW',
    };

    test('_computeSubmittedTogetherClass', () => {
      assert.strictEqual(
          element._computeSubmittedTogetherClass(undefined),
          'hidden');
      assert.strictEqual(
          element._computeSubmittedTogetherClass({changes: []}),
          'hidden');
      assert.strictEqual(
          element._computeSubmittedTogetherClass({changes: [{}]}),
          '');
      assert.strictEqual(
          element._computeSubmittedTogetherClass({
            changes: [],
            non_visible_changes: 0,
          }),
          'hidden');
      assert.strictEqual(
          element._computeSubmittedTogetherClass({
            changes: [],
            non_visible_changes: 1,
          }),
          '');
      assert.strictEqual(
          element._computeSubmittedTogetherClass({
            changes: [{}],
            non_visible_changes: 1,
          }),
          '');
    });

    test('no submitted together changes', () => {
      flushAsynchronousOperations();
      assert.include(element.$.submittedTogether.className, 'hidden');
    });

    test('no non-visible submitted together changes', () => {
      element._submittedTogether = {changes: [change]};
      flushAsynchronousOperations();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.isNull(element.shadowRoot
          .querySelector('.note'));
    });

    test('no visible submitted together changes', () => {
      // Technically this should never happen, but worth asserting the logic.
      element._submittedTogether = {changes: [], non_visible_changes: 1};
      flushAsynchronousOperations();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.isNotNull(element.shadowRoot
          .querySelector('.note'));
      assert.strictEqual(
          element.shadowRoot
              .querySelector('.note').innerText.trim(),
          '(+ 1 non-visible change)');
    });

    test('visible and non-visible submitted together changes', () => {
      element._submittedTogether = {changes: [change], non_visible_changes: 2};
      flushAsynchronousOperations();
      assert.notInclude(element.$.submittedTogether.className, 'hidden');
      assert.isNotNull(element.shadowRoot
          .querySelector('.note'));
      assert.strictEqual(
          element.shadowRoot
              .querySelector('.note').innerText.trim(),
          '(+ 2 non-visible changes)');
    });
  });
});

suite('gr-related-changes-list plugin tests', () => {
  let element;
  let sandbox;

  setup(() => {
    resetPlugins();
    element = fixture('basic');
    sandbox = sinon.sandbox.create();
    stub('gr-endpoint-decorator', {
      _import: sandbox.stub().returns(Promise.resolve()),
    });
  });

  teardown(() => {
    sandbox.restore();
    resetPlugins();
  });

  test('endpoint params', done => {
    element.change = {labels: {}};
    let hookEl;
    let plugin;
    pluginApi.install(
        p => {
          plugin = p;
          plugin.hook('related-changes-section').getLastAttached()
              .then(el => hookEl = el);
        },
        '0.1',
        'http://some/plugins/url1.html');
    pluginLoader.loadPlugins([]);
    flush(() => {
      assert.strictEqual(hookEl.plugin, plugin);
      assert.strictEqual(hookEl.change, element.change);
      done();
    });
  });

  test('hiding and unhiding', done => {
    element.change = {labels: {}};
    let hookEl;
    let plugin;

    // No changes, and no plugin. The element is still hidden.
    element._resultsChanged({}, {}, [], [], []);
    assert.isTrue(element.hidden);
    pluginApi.install(
        p => {
          plugin = p;
          plugin.hook('related-changes-section').getLastAttached()
              .then(el => hookEl = el);
        },
        '0.1',
        'http://some/plugins/url2.html');
    pluginLoader.loadPlugins([]);
    flush(() => {
      // No changes, and plugin without hidden attribute. So it's visible.
      element._resultsChanged({}, {}, [], [], []);
      assert.isFalse(element.hidden);

      // No changes, but plugin with true hidden attribute. So it's invisible.
      hookEl.hidden = true;

      element._resultsChanged({}, {}, [], [], []);
      assert.isTrue(element.hidden);

      // No changes, and plugin with false hidden attribute. So it's visible.
      hookEl.hidden = false;
      element._resultsChanged({}, {}, [], [], []);
      assert.isFalse(element.hidden);

      // Hiding triggered by plugin itself
      hookEl.hidden = true;
      hookEl.dispatchEvent(new CustomEvent('new-section-loaded', {
        composed: true, bubbles: true,
      }));
      assert.isTrue(element.hidden);

      // Unhiding triggered by plugin itself
      hookEl.hidden = false;
      hookEl.dispatchEvent(new CustomEvent('new-section-loaded', {
        composed: true, bubbles: true,
      }));
      assert.isFalse(element.hidden);

      // Hiding plugin keeps list visible, if there are changes
      hookEl.hidden = false;
      element._sameTopic = ['test'];
      element._resultsChanged({}, {}, [], [], ['test']);
      assert.isFalse(element.hidden);
      hookEl.hidden = true;
      hookEl.dispatchEvent(new CustomEvent('new-section-loaded', {
        composed: true, bubbles: true,
      }));
      assert.isFalse(element.hidden);

      done();
    });
  });
});

