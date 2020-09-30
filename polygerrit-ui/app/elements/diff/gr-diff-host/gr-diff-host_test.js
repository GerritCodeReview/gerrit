/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import './gr-diff-host.js';
import {GrDiffBuilderImage} from '../gr-diff-builder/gr-diff-builder-image.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {sortComments} from '../gr-comment-api/gr-comment-api.js';
import {Side} from '../../../constants/constants.js';

const basicFixture = fixtureFromElement('gr-diff-host');

suite('gr-diff-host tests', () => {
  let element;

  let getLoggedIn;

  setup(() => {
    getLoggedIn = false;
    stub('gr-rest-api-interface', {
      async getLoggedIn() { return getLoggedIn; },
    });
    element = basicFixture.instantiate();
    element.changeNum = 123;
    element.path = 'some/path';
    sinon.stub(element.reporting, 'time');
    sinon.stub(element.reporting, 'timeEnd');
  });

  suite('plugin layers', () => {
    const pluginLayers = [{annotate: () => {}}, {annotate: () => {}}];
    setup(() => {
      stub('gr-js-api-interface', {
        getDiffLayers() { return pluginLayers; },
      });
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'some/path';
    });
    test('plugin layers requested', () => {
      element.patchRange = {};
      element.reload();
      assert(element.$.jsAPI.getDiffLayers.called);
    });
  });

  suite('handle comment-update', () => {
    setup(() => {
      sinon.stub(element, '_commentsChanged');
      element.comments = {
        meta: {
          changeNum: '42',
          patchRange: {
            basePatchNum: 'PARENT',
            patchNum: 3,
          },
          path: '/path/to/foo',
          projectConfig: {foo: 'bar'},
        },
        left: [
          {id: 'bc1', side: 'PARENT', __commentSide: 'left'},
          {id: 'bc2', side: 'PARENT', __commentSide: 'left'},
          {id: 'bd1', __draft: true, side: 'PARENT', __commentSide: 'left'},
          {id: 'bd2', __draft: true, side: 'PARENT', __commentSide: 'left'},
        ],
        right: [
          {id: 'c1', __commentSide: 'right'},
          {id: 'c2', __commentSide: 'right'},
          {id: 'd1', __draft: true, __commentSide: 'right'},
          {id: 'd2', __draft: true, __commentSide: 'right'},
        ],
      };
    });

    test('creating a draft', () => {
      const comment = {__draft: true, __draftID: 'tempID', side: 'PARENT',
        __commentSide: 'left'};
      element.dispatchEvent(
          new CustomEvent('comment-update', {
            detail: {comment},
            composed: true, bubbles: true,
          }));
      assert.include(element.comments.left, comment);
    });

    test('discarding a draft', () => {
      const draftID = 'tempID';
      const id = 'savedID';
      const comment = {
        __draft: true,
        __draftID: draftID,
        side: 'PARENT',
        __commentSide: 'left',
      };
      const diffCommentsModifiedStub = sinon.stub();
      element.addEventListener('diff-comments-modified',
          diffCommentsModifiedStub);
      element.comments.left.push(comment);
      comment.id = id;
      element.dispatchEvent(
          new CustomEvent('comment-discard', {
            detail: {comment},
            composed: true, bubbles: true,
          }));
      const drafts = element.comments.left
          .filter(item => item.__draftID === draftID);
      assert.equal(drafts.length, 0);
      assert.isTrue(diffCommentsModifiedStub.called);
    });

    test('saving a draft', () => {
      const draftID = 'tempID';
      const id = 'savedID';
      const comment = {
        __draft: true,
        __draftID: draftID,
        side: 'PARENT',
        __commentSide: 'left',
      };
      const diffCommentsModifiedStub = sinon.stub();
      element.addEventListener('diff-comments-modified',
          diffCommentsModifiedStub);
      element.comments.left.push(comment);
      comment.id = id;
      element.dispatchEvent(
          new CustomEvent('comment-save', {
            detail: {comment},
            composed: true, bubbles: true,
          }));
      const drafts = element.comments.left
          .filter(item => item.__draftID === draftID);
      assert.equal(drafts.length, 1);
      assert.equal(drafts[0].id, id);
      assert.isTrue(diffCommentsModifiedStub.called);
    });
  });

  test('remove comment', () => {
    sinon.stub(element, '_commentsChanged');
    element.comments = {
      meta: {
        changeNum: '42',
        patchRange: {
          basePatchNum: 'PARENT',
          patchNum: 3,
        },
        path: '/path/to/foo',
        projectConfig: {foo: 'bar'},
      },
      left: [
        {id: 'bc1', side: 'PARENT', __commentSide: 'left'},
        {id: 'bc2', side: 'PARENT', __commentSide: 'left'},
        {id: 'bd1', __draft: true, side: 'PARENT', __commentSide: 'left'},
        {id: 'bd2', __draft: true, side: 'PARENT', __commentSide: 'left'},
      ],
      right: [
        {id: 'c1', __commentSide: 'right'},
        {id: 'c2', __commentSide: 'right'},
        {id: 'd1', __draft: true, __commentSide: 'right'},
        {id: 'd2', __draft: true, __commentSide: 'right'},
      ],
    };

    // Using JSON.stringify because Safari 9.1 (11601.5.17.1) doesn’t seem
    // to believe that one object deepEquals another even when they do :-/.
    assert.equal(JSON.stringify(element.comments), JSON.stringify({
      meta: {
        changeNum: '42',
        patchRange: {
          basePatchNum: 'PARENT',
          patchNum: 3,
        },
        path: '/path/to/foo',
        projectConfig: {foo: 'bar'},
      },
      left: [
        {id: 'bc1', side: 'PARENT', __commentSide: 'left'},
        {id: 'bc2', side: 'PARENT', __commentSide: 'left'},
        {id: 'bd1', __draft: true, side: 'PARENT', __commentSide: 'left'},
        {id: 'bd2', __draft: true, side: 'PARENT', __commentSide: 'left'},
      ],
      right: [
        {id: 'c1', __commentSide: 'right'},
        {id: 'c2', __commentSide: 'right'},
        {id: 'd1', __draft: true, __commentSide: 'right'},
        {id: 'd2', __draft: true, __commentSide: 'right'},
      ],
    }));

    element._removeComment({id: 'bc2', side: 'PARENT',
      __commentSide: 'left'});
    assert.deepEqual(element.comments, {
      meta: {
        changeNum: '42',
        patchRange: {
          basePatchNum: 'PARENT',
          patchNum: 3,
        },
        path: '/path/to/foo',
        projectConfig: {foo: 'bar'},
      },
      left: [
        {id: 'bc1', side: 'PARENT', __commentSide: 'left'},
        {id: 'bd1', __draft: true, side: 'PARENT', __commentSide: 'left'},
        {id: 'bd2', __draft: true, side: 'PARENT', __commentSide: 'left'},
      ],
      right: [
        {id: 'c1', __commentSide: 'right'},
        {id: 'c2', __commentSide: 'right'},
        {id: 'd1', __draft: true, __commentSide: 'right'},
        {id: 'd2', __draft: true, __commentSide: 'right'},
      ],
    });

    element._removeComment({id: 'd2', __commentSide: 'right'});
    assert.deepEqual(element.comments, {
      meta: {
        changeNum: '42',
        patchRange: {
          basePatchNum: 'PARENT',
          patchNum: 3,
        },
        path: '/path/to/foo',
        projectConfig: {foo: 'bar'},
      },
      left: [
        {id: 'bc1', side: 'PARENT', __commentSide: 'left'},
        {id: 'bd1', __draft: true, side: 'PARENT', __commentSide: 'left'},
        {id: 'bd2', __draft: true, side: 'PARENT', __commentSide: 'left'},
      ],
      right: [
        {id: 'c1', __commentSide: 'right'},
        {id: 'c2', __commentSide: 'right'},
        {id: 'd1', __draft: true, __commentSide: 'right'},
      ],
    });
  });

  test('thread-discard handling', () => {
    const threads = element._createThreads([
      {
        id: 4711,
        __commentSide: 'left',
        updated: '2015-12-20 15:01:20.396000000',
      },
      {
        id: 42,
        __commentSide: 'left',
        updated: '2017-12-20 15:01:20.396000000',
      },
    ]);
    element._parentIndex = 1;
    element.changeNum = 2;
    element.path = 'some/path';
    element.projectName = 'Some project';
    const threadEls = threads.map(
        thread => {
          const threadEl = element._createThreadElement(thread);
          // Polymer 2 doesn't fire ready events and doesn't execute
          // observers if element is not added to the Dom.
          // See https://github.com/Polymer/old-docs-site/issues/2322
          // and https://github.com/Polymer/polymer/issues/4526
          element._attachThreadElement(threadEl);
          return threadEl;
        });
    assert.equal(threadEls.length, 2);
    assert.equal(threadEls[0].comments[0].id, 4711);
    assert.equal(threadEls[1].comments[0].id, 42);
    for (const threadEl of threadEls) {
      element.appendChild(threadEl);
    }

    threadEls[0].dispatchEvent(
        new CustomEvent('thread-discard', {detail: {rootId: 4711}}));
    const attachedThreads = element.queryAllEffectiveChildren(
        'gr-comment-thread');
    assert.equal(attachedThreads.length, 1);
    assert.equal(attachedThreads[0].comments[0].id, 42);
  });

  suite('render reporting', () => {
    test('starts total and content timer on render-start', done => {
      element.dispatchEvent(
          new CustomEvent('render-start', {bubbles: true, composed: true}));
      assert.isTrue(element.reporting.time.calledWithExactly(
          'Diff Total Render'));
      assert.isTrue(element.reporting.time.calledWithExactly(
          'Diff Content Render'));
      done();
    });

    test('ends content timer on render-content', () => {
      element.dispatchEvent(
          new CustomEvent('render-content', {bubbles: true, composed: true}));
      assert.isTrue(element.reporting.timeEnd.calledWithExactly(
          'Diff Content Render'));
    });

    test('ends total and syntax timer after syntax layer processing', done => {
      sinon.stub(element.reporting, 'diffViewContentDisplayed');
      let notifySyntaxProcessed;
      sinon.stub(element.$.syntaxLayer, 'process').returns(new Promise(
          resolve => {
            notifySyntaxProcessed = resolve;
          }));
      sinon.stub(element.$.restAPI, 'getDiff').returns(
          Promise.resolve({content: []}));
      element.patchRange = {};
      element.$.restAPI.getDiffPreferences().then(prefs => {
        element.prefs = prefs;
        return element.reload(true);
      });
      // Multiple cascading microtasks are scheduled.
      setTimeout(() => {
        notifySyntaxProcessed();
        // Assert after the notification task is processed.
        Promise.resolve().then(() => {
          assert.isTrue(element.reporting.timeEnd.calledWithExactly(
              'Diff Total Render'));
          assert.isTrue(element.reporting.timeEnd.calledWithExactly(
              'Diff Syntax Render'));
          assert.isTrue(element.reporting.diffViewContentDisplayed.called);
          done();
        });
      });
    });

    test('ends total timer w/ no syntax layer processing', done => {
      sinon.stub(element.$.restAPI, 'getDiff').returns(
          Promise.resolve({content: []}));
      element.patchRange = {};
      element.reload();
      // Multiple cascading microtasks are scheduled.
      setTimeout(() => {
        // Reporting can be called with other parameters (ex. PluginsLoaded),
        // but only 'Diff Total Render' is important in this test.
        assert.equal(
            element.reporting.timeEnd.getCalls()
                .filter(call => call.calledWithExactly('Diff Total Render'))
                .length,
            1);
        done();
      });
    });

    test('completes reload promise after syntax layer processing', done => {
      let notifySyntaxProcessed;
      sinon.stub(element.$.syntaxLayer, 'process').returns(new Promise(
          resolve => {
            notifySyntaxProcessed = resolve;
          }));
      sinon.stub(element.$.restAPI, 'getDiff').returns(
          Promise.resolve({content: []}));
      element.patchRange = {};
      let reloadComplete = false;
      element.$.restAPI.getDiffPreferences()
          .then(prefs => {
            element.prefs = prefs;
            return element.reload();
          })
          .then(() => {
            reloadComplete = true;
          });
      // Multiple cascading microtasks are scheduled.
      setTimeout(() => {
        assert.isFalse(reloadComplete);
        notifySyntaxProcessed();
        // Assert after the notification task is processed.
        setTimeout(() => {
          assert.isTrue(reloadComplete);
          done();
        });
      });
    });
  });

  test('reload() cancels before network resolves', () => {
    const cancelStub = sinon.stub(element.$.diff, 'cancel');

    // Stub the network calls into requests that never resolve.
    sinon.stub(element, '_getDiff').callsFake(() => new Promise(() => {}));
    element.patchRange = {};

    element.reload();
    assert.isTrue(cancelStub.called);
  });

  suite('not logged in', () => {
    setup(() => {
      getLoggedIn = false;
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'some/path';
    });

    test('reload() loads files weblinks', () => {
      const weblinksStub = sinon.stub(GerritNav, '_generateWeblinks')
          .returns({name: 'stubb', url: '#s'});
      sinon.stub(element.$.restAPI, 'getDiff').returns(Promise.resolve({
        content: [],
      }));
      element.projectName = 'test-project';
      element.path = 'test-path';
      element.commitRange = {baseCommit: 'test-base', commit: 'test-commit'};
      element.patchRange = {};
      return element.reload().then(() => {
        assert.isTrue(weblinksStub.calledTwice);
        assert.isTrue(weblinksStub.firstCall.calledWith({
          commit: 'test-base',
          file: 'test-path',
          options: {
            weblinks: undefined,
          },
          repo: 'test-project',
          type: GerritNav.WeblinkType.FILE}));
        assert.isTrue(weblinksStub.secondCall.calledWith({
          commit: 'test-commit',
          file: 'test-path',
          options: {
            weblinks: undefined,
          },
          repo: 'test-project',
          type: GerritNav.WeblinkType.FILE}));
        assert.deepEqual(element.filesWeblinks, {
          meta_a: [{name: 'stubb', url: '#s'}],
          meta_b: [{name: 'stubb', url: '#s'}],
        });
      });
    });

    test('prefetch getDiff', done => {
      const diffRestApiStub = sinon.stub(element.$.restAPI, 'getDiff')
          .returns(Promise.resolve({content: []}));
      element.changeNum = 123;
      element.patchRange = {basePatchNum: 1, patchNum: 2};
      element.path = 'file.txt';
      element.prefetchDiff();
      element._getDiff().then(() =>{
        assert.isTrue(diffRestApiStub.calledOnce);
        done();
      });
    });

    test('_getDiff handles null diff responses', done => {
      stub('gr-rest-api-interface', {
        getDiff() { return Promise.resolve(null); },
      });
      element.changeNum = 123;
      element.patchRange = {basePatchNum: 1, patchNum: 2};
      element.path = 'file.txt';
      element._getDiff().then(done);
    });

    test('reload resolves on error', () => {
      const onErrStub = sinon.stub(element, '_handleGetDiffError');
      const error = {ok: false, status: 500};
      sinon.stub(element.$.restAPI, 'getDiff').callsFake(
          (changeNum, basePatchNum, patchNum, path, onErr) => {
            onErr(error);
          });
      element.patchRange = {};
      return element.reload().then(() => {
        assert.isTrue(onErrStub.calledOnce);
      });
    });

    suite('_handleGetDiffError', () => {
      let serverErrorStub;
      let pageErrorStub;

      setup(() => {
        serverErrorStub = sinon.stub();
        element.addEventListener('server-error', serverErrorStub);
        pageErrorStub = sinon.stub();
        element.addEventListener('page-error', pageErrorStub);
      });

      test('page error on HTTP-409', () => {
        element._handleGetDiffError({status: 409});
        assert.isTrue(serverErrorStub.calledOnce);
        assert.isFalse(pageErrorStub.called);
        assert.isNotOk(element._errorMessage);
      });

      test('server error on non-HTTP-409', () => {
        element._handleGetDiffError({status: 500});
        assert.isFalse(serverErrorStub.called);
        assert.isTrue(pageErrorStub.calledOnce);
        assert.isNotOk(element._errorMessage);
      });

      test('error message if showLoadFailure', () => {
        element.showLoadFailure = true;
        element._handleGetDiffError({status: 500, statusText: 'Failure!'});
        assert.isFalse(serverErrorStub.called);
        assert.isFalse(pageErrorStub.called);
        assert.equal(element._errorMessage,
            'Encountered error when loading the diff: 500 Failure!');
      });
    });

    suite('image diffs', () => {
      let mockFile1;
      let mockFile2;
      setup(() => {
        mockFile1 = {
          body: 'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAAAAAA/w==',
          type: 'image/bmp',
        };
        mockFile2 = {
          body: 'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAA/////w==',
          type: 'image/bmp',
        };
        sinon.stub(element.$.restAPI,
            'getB64FileContents')
            .callsFake(
                (changeId, patchNum, path, opt_parentIndex) => Promise.resolve(
                    opt_parentIndex === 1 ? mockFile1 : mockFile2)
            );

        element.patchRange = {basePatchNum: 'PARENT', patchNum: 1};
        element.comments = {
          left: [],
          right: [],
          meta: {patchRange: element.patchRange},
        };
      });

      test('renders image diffs with same file name', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        sinon.stub(element.$.restAPI, 'getDiff')
            .returns(Promise.resolve(mockDiff));

        const rendered = () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diff.$.diffBuilder._builder, GrDiffBuilderImage);

          // Left image rendered with the parent commit's version of the file.
          const leftImage =
              element.$.diff.$.diffTable.querySelector('td.left img');
          const leftLabel =
              element.$.diff.$.diffTable.querySelector('td.left label');
          const leftLabelContent = leftLabel.querySelector('.label');
          const leftLabelName = leftLabel.querySelector('.name');

          const rightImage =
              element.$.diff.$.diffTable.querySelector('td.right img');
          const rightLabel = element.$.diff.$.diffTable.querySelector(
              'td.right label');
          const rightLabelContent = rightLabel.querySelector('.label');
          const rightLabelName = rightLabel.querySelector('.name');

          assert.isNotOk(rightLabelName);
          assert.isNotOk(leftLabelName);

          let leftLoaded = false;
          let rightLoaded = false;

          leftImage.addEventListener('load', () => {
            assert.isOk(leftImage);
            assert.equal(leftImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile1.body);
            assert.equal(leftLabelContent.textContent, '1×1 image/bmp');
            leftLoaded = true;
            if (rightLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });

          rightImage.addEventListener('load', () => {
            assert.isOk(rightImage);
            assert.equal(rightImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile2.body);
            assert.equal(rightLabelContent.textContent, '1×1 image/bmp');

            rightLoaded = true;
            if (leftLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });
        };

        element.addEventListener('render', rendered);

        element.$.restAPI.getDiffPreferences().then(prefs => {
          element.prefs = prefs;
          element.reload();
        });
      });

      test('renders image diffs with a different file name', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot2.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot2.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        sinon.stub(element.$.restAPI, 'getDiff')
            .returns(Promise.resolve(mockDiff));

        const rendered = () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diff.$.diffBuilder._builder, GrDiffBuilderImage);

          // Left image rendered with the parent commit's version of the file.
          const leftImage =
              element.$.diff.$.diffTable.querySelector('td.left img');
          const leftLabel =
              element.$.diff.$.diffTable.querySelector('td.left label');
          const leftLabelContent = leftLabel.querySelector('.label');
          const leftLabelName = leftLabel.querySelector('.name');

          const rightImage =
              element.$.diff.$.diffTable.querySelector('td.right img');
          const rightLabel = element.$.diff.$.diffTable.querySelector(
              'td.right label');
          const rightLabelContent = rightLabel.querySelector('.label');
          const rightLabelName = rightLabel.querySelector('.name');

          assert.isOk(rightLabelName);
          assert.isOk(leftLabelName);
          assert.equal(leftLabelName.textContent, mockDiff.meta_a.name);
          assert.equal(rightLabelName.textContent, mockDiff.meta_b.name);

          let leftLoaded = false;
          let rightLoaded = false;

          leftImage.addEventListener('load', () => {
            assert.isOk(leftImage);
            assert.equal(leftImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile1.body);
            assert.equal(leftLabelContent.textContent, '1×1 image/bmp');
            leftLoaded = true;
            if (rightLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });

          rightImage.addEventListener('load', () => {
            assert.isOk(rightImage);
            assert.equal(rightImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile2.body);
            assert.equal(rightLabelContent.textContent, '1×1 image/bmp');

            rightLoaded = true;
            if (leftLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });
        };

        element.addEventListener('render', rendered);

        element.$.restAPI.getDiffPreferences().then(prefs => {
          element.prefs = prefs;
          element.reload();
        });
      });

      test('renders added image', done => {
        const mockDiff = {
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'ADDED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 0000000..f9c2f2c 100644',
            '--- /dev/null',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        sinon.stub(element.$.restAPI, 'getDiff')
            .returns(Promise.resolve(mockDiff));

        element.addEventListener('render', () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diff.$.diffBuilder._builder, GrDiffBuilderImage);

          const leftImage =
              element.$.diff.$.diffTable.querySelector('td.left img');
          const rightImage =
              element.$.diff.$.diffTable.querySelector('td.right img');

          assert.isNotOk(leftImage);
          assert.isOk(rightImage);
          done();
        });

        element.$.restAPI.getDiffPreferences().then(prefs => {
          element.prefs = prefs;
          element.reload();
        });
      });

      test('renders removed image', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        sinon.stub(element.$.restAPI, 'getDiff')
            .returns(Promise.resolve(mockDiff));

        element.addEventListener('render', () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diff.$.diffBuilder._builder, GrDiffBuilderImage);

          const leftImage =
              element.$.diff.$.diffTable.querySelector('td.left img');
          const rightImage =
              element.$.diff.$.diffTable.querySelector('td.right img');

          assert.isOk(leftImage);
          assert.isNotOk(rightImage);
          done();
        });

        element.$.restAPI.getDiffPreferences().then(prefs => {
          element.prefs = prefs;
          element.reload();
        });
      });

      test('does not render disallowed image type', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg-evil',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        mockFile1.type = 'image/jpeg-evil';

        sinon.stub(element.$.restAPI, 'getDiff')
            .returns(Promise.resolve(mockDiff));

        element.addEventListener('render', () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diff.$.diffBuilder._builder, GrDiffBuilderImage);
          const leftImage =
              element.$.diff.$.diffTable.querySelector('td.left img');
          assert.isNotOk(leftImage);
          done();
        });

        element.$.restAPI.getDiffPreferences().then(prefs => {
          element.prefs = prefs;
          element.reload();
        });
      });
    });
  });

  test('delegates cancel()', () => {
    const stub = sinon.stub(element.$.diff, 'cancel');
    element.patchRange = {};
    element.reload();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates getCursorStops()', () => {
    const returnValue = [document.createElement('b')];
    const stub = sinon.stub(element.$.diff, 'getCursorStops')
        .returns(returnValue);
    assert.equal(element.getCursorStops(), returnValue);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates isRangeSelected()', () => {
    const returnValue = true;
    const stub = sinon.stub(element.$.diff, 'isRangeSelected')
        .returns(returnValue);
    assert.equal(element.isRangeSelected(), returnValue);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates toggleLeftDiff()', () => {
    const stub = sinon.stub(element.$.diff, 'toggleLeftDiff');
    element.toggleLeftDiff();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  suite('blame', () => {
    setup(() => {
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'some/path';
    });

    test('clearBlame', () => {
      element._blame = [];
      const setBlameSpy = sinon.spy(element.$.diff.$.diffBuilder, 'setBlame');
      element.clearBlame();
      assert.isNull(element._blame);
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.equal(element.isBlameLoaded, false);
    });

    test('loadBlame', () => {
      const mockBlame = [{id: 'commit id', ranges: [{start: 1, end: 2}]}];
      const showAlertStub = sinon.stub();
      element.addEventListener('show-alert', showAlertStub);
      const getBlameStub = sinon.stub(element.$.restAPI, 'getBlame')
          .returns(Promise.resolve(mockBlame));
      element.changeNum = 42;
      element.patchRange = {patchNum: 5, basePatchNum: 4};
      element.path = 'foo/bar.baz';
      return element.loadBlame().then(() => {
        assert.isTrue(getBlameStub.calledWithExactly(
            42, 5, 'foo/bar.baz', true));
        assert.isFalse(showAlertStub.called);
        assert.equal(element._blame, mockBlame);
        assert.equal(element.isBlameLoaded, true);
      });
    });

    test('loadBlame empty', () => {
      const mockBlame = [];
      const showAlertStub = sinon.stub();
      element.addEventListener('show-alert', showAlertStub);
      sinon.stub(element.$.restAPI, 'getBlame')
          .returns(Promise.resolve(mockBlame));
      element.changeNum = 42;
      element.patchRange = {patchNum: 5, basePatchNum: 4};
      element.path = 'foo/bar.baz';
      return element.loadBlame()
          .then(() => {
            assert.isTrue(false, 'Promise should not resolve');
          })
          .catch(() => {
            assert.isTrue(showAlertStub.calledOnce);
            assert.isNull(element._blame);
            assert.equal(element.isBlameLoaded, false);
          });
    });
  });

  test('getThreadEls() returns .comment-threads', () => {
    const threadEl = document.createElement('div');
    threadEl.className = 'comment-thread';
    element.$.diff.appendChild(threadEl);
    assert.deepEqual(element.getThreadEls(), [threadEl]);
  });

  test('delegates addDraftAtLine(el)', () => {
    const param0 = document.createElement('b');
    const stub = sinon.stub(element.$.diff, 'addDraftAtLine');
    element.addDraftAtLine(param0);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 1);
    assert.equal(stub.lastCall.args[0], param0);
  });

  test('delegates clearDiffContent()', () => {
    const stub = sinon.stub(element.$.diff, 'clearDiffContent');
    element.clearDiffContent();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates expandAllContext()', () => {
    const stub = sinon.stub(element.$.diff, 'expandAllContext');
    element.expandAllContext();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('passes in changeNum', () => {
    element.changeNum = 12345;
    assert.equal(element.$.diff.changeNum, 12345);
  });

  test('passes in noAutoRender', () => {
    const value = true;
    element.noAutoRender = value;
    assert.equal(element.$.diff.noAutoRender, value);
  });

  test('passes in patchRange', () => {
    const value = {patchNum: 'foo', basePatchNum: 'bar'};
    element.patchRange = value;
    assert.equal(element.$.diff.patchRange, value);
  });

  test('passes in path', () => {
    const value = 'some/file/path';
    element.path = value;
    assert.equal(element.$.diff.path, value);
  });

  test('passes in prefs', () => {
    const value = {};
    element.prefs = value;
    assert.equal(element.$.diff.prefs, value);
  });

  test('passes in changeNum', () => {
    element.changeNum = 12345;
    assert.equal(element.$.diff.changeNum, 12345);
  });

  test('passes in projectName', () => {
    const value = 'Gerrit';
    element.projectName = value;
    assert.equal(element.$.diff.projectName, value);
  });

  test('passes in displayLine', () => {
    const value = true;
    element.displayLine = value;
    assert.equal(element.$.diff.displayLine, value);
  });

  test('passes in hidden', () => {
    const value = true;
    element.hidden = value;
    assert.equal(element.$.diff.hidden, value);
    assert.isNotNull(element.getAttribute('hidden'));
  });

  test('passes in noRenderOnPrefsChange', () => {
    const value = true;
    element.noRenderOnPrefsChange = value;
    assert.equal(element.$.diff.noRenderOnPrefsChange, value);
  });

  test('passes in lineWrapping', () => {
    const value = true;
    element.lineWrapping = value;
    assert.equal(element.$.diff.lineWrapping, value);
  });

  test('passes in viewMode', () => {
    const value = 'SIDE_BY_SIDE';
    element.viewMode = value;
    assert.equal(element.$.diff.viewMode, value);
  });

  test('passes in lineOfInterest', () => {
    const value = {number: 123, leftSide: true};
    element.lineOfInterest = value;
    assert.equal(element.$.diff.lineOfInterest, value);
  });

  suite('_reportDiff', () => {
    let reportStub;

    setup(() => {
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'file.txt';
      element.patchRange = {basePatchNum: 1};
      reportStub = sinon.stub(element.reporting, 'reportInteraction');
    });

    test('null and content-less', () => {
      element._reportDiff(null);
      assert.isFalse(reportStub.called);

      element._reportDiff({});
      assert.isFalse(reportStub.called);
    });

    test('diff w/ no delta', () => {
      const diff = {
        content: [
          {ab: ['foo', 'bar']},
          {ab: ['baz', 'foo']},
        ],
      };
      element._reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'rebase-percent-zero');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });

    test('diff w/ no rebase delta', () => {
      const diff = {
        content: [
          {ab: ['foo', 'bar']},
          {a: ['baz', 'foo']},
          {ab: ['foo', 'bar']},
          {a: ['baz', 'foo'], b: ['bar', 'baz']},
          {ab: ['foo', 'bar']},
          {b: ['baz', 'foo']},
          {ab: ['foo', 'bar']},
        ],
      };
      element._reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'rebase-percent-zero');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });

    test('diff w/ some rebase delta', () => {
      const diff = {
        content: [
          {ab: ['foo', 'bar']},
          {a: ['baz', 'foo'], due_to_rebase: true},
          {ab: ['foo', 'bar']},
          {a: ['baz', 'foo'], b: ['bar', 'baz']},
          {ab: ['foo', 'bar']},
          {b: ['baz', 'foo'], due_to_rebase: true},
          {ab: ['foo', 'bar']},
          {a: ['baz', 'foo']},
        ],
      };
      element._reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.isTrue(reportStub.calledWith(
          'rebase-percent-nonzero',
          {percentRebaseDelta: 50}
      ));
    });

    test('diff w/ all rebase delta', () => {
      const diff = {content: [{
        a: ['foo', 'bar'],
        b: ['baz', 'foo'],
        due_to_rebase: true,
      }]};
      element._reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.isTrue(reportStub.calledWith(
          'rebase-percent-nonzero',
          {percentRebaseDelta: 100}
      ));
    });

    test('diff against parent event', () => {
      element.patchRange.basePatchNum = 'PARENT';
      const diff = {content: [{
        a: ['foo', 'bar'],
        b: ['baz', 'foo'],
      }]};
      element._reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'diff-against-parent');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });
  });

  test('comments sorting', () => {
    const comments = [
      {
        id: 'new_draft',
        message: 'i do not like either of you',
        __commentSide: 'left',
        __draft: true,
        updated: '2015-12-20 15:01:20.396000000',
      },
      {
        id: 'sallys_confession',
        message: 'i like you, jack',
        updated: '2015-12-23 15:00:20.396000000',
        line: 1,
        __commentSide: 'left',
      }, {
        id: 'jacks_reply',
        message: 'i like you, too',
        updated: '2015-12-24 15:01:20.396000000',
        __commentSide: 'left',
        line: 1,
        in_reply_to: 'sallys_confession',
      },
    ];
    const sortedComments = sortComments(comments);
    assert.equal(sortedComments[0], comments[1]);
    assert.equal(sortedComments[1], comments[2]);
    assert.equal(sortedComments[2], comments[0]);
  });

  test('_createThreads', () => {
    const comments = [
      {
        id: 'sallys_confession',
        message: 'i like you, jack',
        updated: '2015-12-23 15:00:20.396000000',
        line: 1,
        __commentSide: 'left',
      }, {
        id: 'jacks_reply',
        message: 'i like you, too',
        updated: '2015-12-24 15:01:20.396000000',
        __commentSide: 'left',
        line: 1,
        in_reply_to: 'sallys_confession',
      },
      {
        id: 'new_draft',
        message: 'i do not like either of you',
        __commentSide: 'left',
        __draft: true,
        updated: '2015-12-20 15:01:20.396000000',
      },
    ];

    const actualThreads = element._createThreads(comments);

    assert.equal(actualThreads.length, 2);

    assert.equal(actualThreads[0].commentSide, 'left');
    assert.equal(actualThreads[0].comments.length, 2);
    assert.deepEqual(actualThreads[0].comments[0], comments[0]);
    assert.deepEqual(actualThreads[0].comments[1], comments[1]);
    assert.equal(actualThreads[0].patchNum, undefined);
    assert.equal(actualThreads[0].lineNum, 1);

    assert.equal(actualThreads[1].commentSide, 'left');
    assert.equal(actualThreads[1].comments.length, 1);
    assert.deepEqual(actualThreads[1].comments[0], comments[2]);
    assert.equal(actualThreads[1].patchNum, undefined);
    assert.equal(actualThreads[1].lineNum, undefined);
  });

  test('_createThreads inherits patchNum and range', () => {
    const comments = [{
      id: 'betsys_confession',
      message: 'i like you, jack',
      updated: '2015-12-24 15:00:10.396000000',
      range: {
        start_line: 1,
        start_character: 1,
        end_line: 1,
        end_character: 2,
      },
      patch_set: 5,
      __commentSide: 'left',
      line: 1,
    }];

    const expectedThreads = [
      {
        commentSide: 'left',
        comments: [{
          id: 'betsys_confession',
          message: 'i like you, jack',
          updated: '2015-12-24 15:00:10.396000000',
          range: {
            start_line: 1,
            start_character: 1,
            end_line: 1,
            end_character: 2,
          },
          patch_set: 5,
          __commentSide: 'left',
          line: 1,
        }],
        patchNum: 5,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 1,
          end_character: 2,
        },
        lineNum: 1,
        isOnParent: false,
      },
    ];

    assert.deepEqual(
        element._createThreads(comments),
        expectedThreads);
  });

  test('_createThreads does not thread unrelated comments at same location',
      () => {
        const comments = [
          {
            id: 'sallys_confession',
            message: 'i like you, jack',
            updated: '2015-12-23 15:00:20.396000000',
            __commentSide: 'left',
          }, {
            id: 'jacks_reply',
            message: 'i like you, too',
            updated: '2015-12-24 15:01:20.396000000',
            __commentSide: 'left',
          },
        ];
        assert.equal(element._createThreads(comments).length, 2);
      });

  test('_createThreads derives isOnParent using  side from first comment',
      () => {
        const comments = [
          {
            id: 'sallys_confession',
            message: 'i like you, jack',
            updated: '2015-12-23 15:00:20.396000000',
            __commentSide: 'left',
          }, {
            id: 'jacks_reply',
            message: 'i like you, too',
            updated: '2015-12-24 15:01:20.396000000',
            __commentSide: 'left',
            in_reply_to: 'sallys_confession',
          },
        ];

        assert.equal(element._createThreads(comments)[0].isOnParent, false);

        comments[0].side = 'REVISION';
        assert.equal(element._createThreads(comments)[0].isOnParent, false);

        comments[0].side = 'PARENT';
        assert.equal(element._createThreads(comments)[0].isOnParent, true);
      });

  test('_getOrCreateThread', () => {
    const commentSide = 'left';

    assert.isOk(element._getOrCreateThread('2', 3,
        commentSide, undefined, false));

    let threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 1);
    assert.equal(threads[0].commentSide, commentSide);
    assert.equal(threads[0].range, undefined);
    assert.equal(threads[0].isOnParent, false);
    assert.equal(threads[0].patchNum, 2);

    // Try to fetch a thread with a different range.
    const range = {
      start_line: 1,
      start_character: 1,
      end_line: 1,
      end_character: 3,
    };

    assert.isOk(element._getOrCreateThread(
        '3', 1, commentSide, range, true));

    threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 2);
    assert.equal(threads[1].commentSide, commentSide);
    assert.equal(threads[1].range, range);
    assert.equal(threads[1].isOnParent, true);
    assert.equal(threads[1].patchNum, 3);
  });

  test('thread should use old file path if first created' +
   'on patch set (left) before renaming', () => {
    const commentSide = 'left';
    element.file = {basePath: 'file_renamed.txt', path: element.path};

    assert.isOk(element._getOrCreateThread('2', 3,
        commentSide, undefined, /* isOnParent= */ false));

    const threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 1);
    assert.equal(threads[0].commentSide, commentSide);
    assert.equal(threads[0].path, element.file.basePath);
  });

  test('thread should use new file path if first created' +
   'on patch set (right) after renaming', () => {
    const commentSide = 'right';
    element.file = {basePath: 'file_renamed.txt', path: element.path};

    assert.isOk(element._getOrCreateThread('2', 3,
        commentSide, undefined, /* isOnParent= */ false));

    const threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 1);
    assert.equal(threads[0].commentSide, commentSide);
    assert.equal(threads[0].path, element.file.path);
  });

  test('thread should use new file path if first created' +
   'on patch set (left) but is base', () => {
    const commentSide = 'left';
    element.file = {basePath: 'file_renamed.txt', path: element.path};

    assert.isOk(element._getOrCreateThread('2', 3,
        commentSide, undefined, /* isOnParent= */ true));

    const threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 1);
    assert.equal(threads[0].commentSide, commentSide);
    assert.equal(threads[0].path, element.file.path);
  });

  test('_filterThreadElsForLocation with no threads', () => {
    const line = {beforeNumber: 3, afterNumber: 5};

    const threads = [];
    assert.deepEqual(element._filterThreadElsForLocation(threads, line), []);
    assert.deepEqual(element._filterThreadElsForLocation(threads, line,
        Side.LEFT), []);
    assert.deepEqual(element._filterThreadElsForLocation(threads, line,
        Side.RIGHT), []);
  });

  test('_filterThreadElsForLocation for line comments', () => {
    const line = {beforeNumber: 3, afterNumber: 5};

    const l3 = document.createElement('div');
    l3.setAttribute('line-num', 3);
    l3.setAttribute('comment-side', 'left');

    const l5 = document.createElement('div');
    l5.setAttribute('line-num', 5);
    l5.setAttribute('comment-side', 'left');

    const r3 = document.createElement('div');
    r3.setAttribute('line-num', 3);
    r3.setAttribute('comment-side', 'right');

    const r5 = document.createElement('div');
    r5.setAttribute('line-num', 5);
    r5.setAttribute('comment-side', 'right');

    const threadEls = [l3, l5, r3, r5];
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line),
        [l3, r5]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.LEFT), [l3]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.RIGHT), [r5]);
  });

  test('_filterThreadElsForLocation for file comments', () => {
    const line = {beforeNumber: 'FILE', afterNumber: 'FILE'};

    const l = document.createElement('div');
    l.setAttribute('comment-side', 'left');
    l.setAttribute('line-num', 'FILE');

    const r = document.createElement('div');
    r.setAttribute('comment-side', 'right');
    r.setAttribute('line-num', 'FILE');

    const threadEls = [l, r];
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line),
        [l, r]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.BOTH), [l, r]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.LEFT), [l]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.RIGHT), [r]);
  });

  suite('syntax layer with syntax_highlighting on', () => {
    setup(() => {
      const prefs = {
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
        syntax_highlighting: true,
      };
      element.patchRange = {};
      element.prefs = prefs;
      element.changeNum = 123;
      element.path = 'some/path';
    });

    test('gr-diff-host provides syntax highlighting layer to gr-diff', () => {
      element.reload();
      assert.equal(element.$.diff.layers[0], element.$.syntaxLayer);
    });

    test('rendering normal-sized diff does not disable syntax', () => {
      element.diff = {
        content: [{
          a: ['foo'],
        }],
      };
      assert.isTrue(element.$.syntaxLayer.enabled);
    });

    test('rendering large diff disables syntax', () => {
      // Before it renders, set the first diff line to 500 '*' characters.
      element.diff = {
        content: [{
          a: [new Array(501).join('*')],
        }],
      };
      assert.isFalse(element.$.syntaxLayer.enabled);
    });

    test('starts syntax layer processing on render event', done => {
      sinon.stub(element.$.syntaxLayer, 'process')
          .returns(Promise.resolve());
      sinon.stub(element.$.restAPI, 'getDiff').returns(
          Promise.resolve({content: []}));
      element.reload();
      setTimeout(() => {
        element.dispatchEvent(
            new CustomEvent('render', {bubbles: true, composed: true}));
        assert.isTrue(element.$.syntaxLayer.process.called);
        done();
      });
    });
  });

  suite('syntax layer with syntax_highlighting off', () => {
    setup(() => {
      const prefs = {
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
      };
      element.diff = {
        content: [{
          a: ['foo'],
        }],
      };
      element.patchRange = {};
      element.prefs = prefs;
    });

    test('gr-diff-host provides syntax highlighting layer', () => {
      element.reload();
      assert.equal(element.$.diff.layers[0], element.$.syntaxLayer);
    });

    test('syntax layer should be disabled', () => {
      assert.isFalse(element.$.syntaxLayer.enabled);
    });

    test('still disabled for large diff', () => {
      // Before it renders, set the first diff line to 500 '*' characters.
      element.diff = {
        content: [{
          a: [new Array(501).join('*')],
        }],
      };
      assert.isFalse(element.$.syntaxLayer.enabled);
    });
  });

  suite('coverage layer', () => {
    let notifyStub;
    setup(() => {
      notifyStub = sinon.stub();
      stub('gr-js-api-interface', {
        getCoverageAnnotationApi() {
          return Promise.resolve({
            notify: notifyStub,
            getCoverageProvider() {
              return () => Promise.resolve([
                {
                  type: 'COVERED',
                  side: 'right',
                  code_range: {
                    start_line: 1,
                    end_line: 2,
                  },
                },
                {
                  type: 'NOT_COVERED',
                  side: 'right',
                  code_range: {
                    start_line: 3,
                    end_line: 4,
                  },
                },
              ]);
            },
          });
        },
      });
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'some/path';
      const prefs = {
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
      };
      element.diff = {
        content: [{
          a: ['foo'],
        }],
      };
      element.patchRange = {};
      element.prefs = prefs;
    });

    test('getCoverageAnnotationApi should be called', done => {
      element.reload();
      flush(() => {
        assert.isTrue(element.$.jsAPI.getCoverageAnnotationApi.calledOnce);
        done();
      });
    });

    test('coverageRangeChanged should be called', done => {
      element.reload();
      flush(() => {
        assert.equal(notifyStub.callCount, 2);
        done();
      });
    });
  });

  suite('trailing newlines', () => {
    setup(() => {
    });

    suite('_lastChunkForSide', () => {
      test('deltas', () => {
        const diff = {content: [
          {a: ['foo', 'bar'], b: ['baz']},
          {ab: ['foo', 'bar', 'baz']},
          {b: ['foo']},
        ]};
        assert.equal(element._lastChunkForSide(diff, false), diff.content[2]);
        assert.equal(element._lastChunkForSide(diff, true), diff.content[1]);

        diff.content.push({a: ['foo'], b: ['bar']});
        assert.equal(element._lastChunkForSide(diff, false), diff.content[3]);
        assert.equal(element._lastChunkForSide(diff, true), diff.content[3]);
      });

      test('addition with a undefined', () => {
        const diff = {content: [
          {b: ['foo', 'bar', 'baz']},
        ]};
        assert.equal(element._lastChunkForSide(diff, false), diff.content[0]);
        assert.isNull(element._lastChunkForSide(diff, true));
      });

      test('addition with a empty', () => {
        const diff = {content: [
          {a: [], b: ['foo', 'bar', 'baz']},
        ]};
        assert.equal(element._lastChunkForSide(diff, false), diff.content[0]);
        assert.isNull(element._lastChunkForSide(diff, true));
      });

      test('deletion with b undefined', () => {
        const diff = {content: [
          {a: ['foo', 'bar', 'baz']},
        ]};
        assert.isNull(element._lastChunkForSide(diff, false));
        assert.equal(element._lastChunkForSide(diff, true), diff.content[0]);
      });

      test('deletion with b empty', () => {
        const diff = {content: [
          {a: ['foo', 'bar', 'baz'], b: []},
        ]};
        assert.isNull(element._lastChunkForSide(diff, false));
        assert.equal(element._lastChunkForSide(diff, true), diff.content[0]);
      });

      test('empty', () => {
        const diff = {content: []};
        assert.isNull(element._lastChunkForSide(diff, false));
        assert.isNull(element._lastChunkForSide(diff, true));
      });
    });

    suite('_hasTrailingNewlines', () => {
      test('shared no trailing', () => {
        const diff = undefined;
        sinon.stub(element, '_lastChunkForSide')
            .returns({ab: ['foo', 'bar']});
        assert.isFalse(element._hasTrailingNewlines(diff, false));
        assert.isFalse(element._hasTrailingNewlines(diff, true));
      });

      test('delta trailing in right', () => {
        const diff = undefined;
        sinon.stub(element, '_lastChunkForSide')
            .returns({a: ['foo', 'bar'], b: ['baz', '']});
        assert.isTrue(element._hasTrailingNewlines(diff, false));
        assert.isFalse(element._hasTrailingNewlines(diff, true));
      });

      test('addition', () => {
        const diff = undefined;
        sinon.stub(element, '_lastChunkForSide').callsFake((diff, leftSide) => {
          if (leftSide) { return null; }
          return {b: ['foo', '']};
        });
        assert.isTrue(element._hasTrailingNewlines(diff, false));
        assert.isNull(element._hasTrailingNewlines(diff, true));
      });

      test('deletion', () => {
        const diff = undefined;
        sinon.stub(element, '_lastChunkForSide').callsFake((diff, leftSide) => {
          if (!leftSide) { return null; }
          return {a: ['foo']};
        });
        assert.isNull(element._hasTrailingNewlines(diff, false));
        assert.isFalse(element._hasTrailingNewlines(diff, true));
      });
    });
  });
});

