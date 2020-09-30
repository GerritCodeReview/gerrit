/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-search-bar.js';
import '../../../scripts/util.js';
import {TestKeyboardShortcutBinder} from '../../../test/test-utils.js';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {_testOnly_clearDocsBaseUrlCache} from '../../../utils/url-util.js';

const basicFixture = fixtureFromElement('gr-search-bar');

suite('gr-search-bar tests', () => {
  let element;

  suiteSetup(() => {
    const kb = TestKeyboardShortcutBinder.push();
    kb.bindShortcut(Shortcut.SEARCH, '/');
  });

  suiteTeardown(() => {
    TestKeyboardShortcutBinder.pop();
  });

  setup(done => {
    element = basicFixture.instantiate();
    flush(done);
  });

  test('value is propagated to _inputVal', () => {
    element.value = 'foo';
    assert.equal(element._inputVal, 'foo');
  });

  const getActiveElement = () => {
    return (document.activeElement.shadowRoot ?
      document.activeElement.shadowRoot.activeElement :
      document.activeElement);
  };

  test('enter in search input fires event', done => {
    element.addEventListener('handle-search', () => {
      assert.notEqual(getActiveElement(), element.$.searchInput);
      assert.notEqual(getActiveElement(), element.$.searchButton);
      done();
    });
    element.value = 'test';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
  });

  test('input blurred after commit', () => {
    const blurSpy = sinon.spy(element.$.searchInput.$.input, 'blur');
    element.$.searchInput.text = 'fate/stay';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isTrue(blurSpy.called);
  });

  test('empty search query does not trigger nav', () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = '';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isFalse(searchSpy.called);
  });

  test('Predefined query op with no predication doesnt trigger nav', () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'added:';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isFalse(searchSpy.called);
  });

  test('predefined predicate query triggers nav', () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'age:1week';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isTrue(searchSpy.called);
  });

  test('undefined predicate query triggers nav', () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'random:1week';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isTrue(searchSpy.called);
  });

  test('empty undefined predicate query triggers nav', () => {
    const searchSpy = sinon.spy();
    element.addEventListener('handle-search', searchSpy);
    element.value = 'random:';
    MockInteractions.pressAndReleaseKeyOn(element.$.searchInput.$.input, 13,
        null, 'enter');
    assert.isTrue(searchSpy.called);
  });

  test('keyboard shortcuts', () => {
    const focusSpy = sinon.spy(element.$.searchInput, 'focus');
    const selectAllSpy = sinon.spy(element.$.searchInput, 'selectAll');
    MockInteractions.pressAndReleaseKeyOn(document.body, 191, null, '/');
    assert.isTrue(focusSpy.called);
    assert.isTrue(selectAllSpy.called);
  });

  suite('_getSearchSuggestions', () => {
    test('Autocompletes accounts', () => {
      sinon.stub(element, 'accountSuggestions').callsFake(() => { return Promise.resolve([{text: 'owner:fred@goog.co'}]); }
      );
      return element._getSearchSuggestions('owner:fr').then(s => {
        assert.equal(s[0].value, 'owner:fred@goog.co');
      });
    });

    test('Autocompletes groups', done => {
      sinon.stub(element, 'groupSuggestions').callsFake(() => {
        return Promise.resolve([
          {text: 'ownerin:Polygerrit'},
          {text: 'ownerin:gerrit'},
        ]);
      }
      );
      element._getSearchSuggestions('ownerin:pol').then(s => {
        assert.equal(s[0].value, 'ownerin:Polygerrit');
        done();
      });
    });

    test('Autocompletes projects', done => {
      sinon.stub(element, 'projectSuggestions').callsFake(() => {
        return Promise.resolve([
          {text: 'project:Polygerrit'},
          {text: 'project:gerrit'},
          {text: 'project:gerrittest'},
        ]);
      }
      );
      element._getSearchSuggestions('project:pol').then(s => {
        assert.equal(s[0].value, 'project:Polygerrit');
        done();
      });
    });

    test('Autocompletes simple searches', done => {
      element._getSearchSuggestions('is:o').then(s => {
        assert.equal(s[0].name, 'is:open');
        assert.equal(s[0].value, 'is:open');
        assert.equal(s[1].name, 'is:owner');
        assert.equal(s[1].value, 'is:owner');
        done();
      });
    });

    test('Does not autocomplete with no match', done => {
      element._getSearchSuggestions('asdasdasdasd').then(s => {
        assert.equal(s.length, 0);
        done();
      });
    });

    test('Autocompltes without is:mergable when disabled', done => {
      element._getSearchSuggestions('is:mergeab').then(s => {
        assert.equal(s.length, 0);
        done();
      });
    });
  });

  [
    'API_REF_UPDATED_AND_CHANGE_REINDEX',
    'REF_UPDATED_AND_CHANGE_REINDEX',
  ].forEach(mergeability => {
    suite(`mergeability as ${mergeability}`, () => {
      setup(done => {
        stub('gr-rest-api-interface', {
          getConfig() {
            return Promise.resolve({
              change: {
                mergeability_computation_behavior: mergeability,
              },
            });
          },
        });

        element = basicFixture.instantiate();
        flush(done);
      });

      test('Autocompltes with is:mergable when enabled', done => {
        element._getSearchSuggestions('is:mergeab').then(s => {
          assert.equal(s.length, 2);
          assert.equal(s[0].name, 'is:mergeable');
          assert.equal(s[0].value, 'is:mergeable');
          assert.equal(s[1].name, '-is:mergeable');
          assert.equal(s[1].value, '-is:mergeable');
          done();
        });
      });
    });
  });

  suite('doc url', () => {
    setup(done => {
      stub('gr-rest-api-interface', {
        getConfig() {
          return Promise.resolve({
            gerrit: {
              doc_url: 'https://doc.com/',
            },
          });
        },
      });

      _testOnly_clearDocsBaseUrlCache();
      element = basicFixture.instantiate();
      flush(done);
    });

    test('compute help doc url with correct path', () => {
      assert.equal(element.docBaseUrl, 'https://doc.com/');
      assert.equal(
          element._computeHelpDocLink(element.docBaseUrl),
          'https://doc.com/user-search.html'
      );
    });

    test('compute help doc url fallback to gerrit url', () => {
      assert.equal(
          element._computeHelpDocLink(),
          'https://gerrit-review.googlesource.com/documentation/' +
          'user-search.html'
      );
    });
  });
});

