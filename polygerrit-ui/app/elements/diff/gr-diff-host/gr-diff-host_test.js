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

import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {createDefaultDiffPrefs, Side} from '../../../constants/constants.js';
import {createChange, createComment, createCommentThread} from '../../../test/test-data-generators.js';
import {addListenerForTest, mockPromise, stubRestApi, waitUntil} from '../../../test/test-utils.js';
import {EditPatchSetNum, ParentPatchSetNum} from '../../../types/common.js';
import {CoverageType} from '../../../types/types.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {GrDiffBuilderImage} from '../../../embed/diff/gr-diff-builder/gr-diff-builder-image.js';
import {waitForEventOnce} from '../../../utils/event-util.js';

const basicFixture = fixtureFromElement('gr-diff-host');

suite('gr-diff-host tests', () => {
  let element;

  let loggedIn;

  setup(async () => {
    loggedIn = false;
    stubRestApi('getLoggedIn').callsFake(() => Promise.resolve(loggedIn));
    element = basicFixture.instantiate();
    element.changeNum = 123;
    element.path = 'some/path';
    sinon.stub(element.reporting, 'time');
    sinon.stub(element.reporting, 'timeEnd');
    await flush();
  });

  suite('plugin layers', () => {
    const pluginLayers = [{annotate: () => {}}, {annotate: () => {}}];
    setup(() => {
      element = basicFixture.instantiate();
      sinon.stub(element.jsAPI, 'getDiffLayers').returns(pluginLayers);
      element.changeNum = 123;
      element.path = 'some/path';
    });
    test('plugin layers requested', async () => {
      element.patchRange = {};
      element.change = createChange();
      stubRestApi('getDiff').returns(Promise.resolve({content: []}));
      await element.reload();
      assert(element.jsAPI.getDiffLayers.called);
    });
  });

  suite('render reporting', () => {
    test('ends total and syntax timer after syntax layer', async () => {
      sinon.stub(element.reporting, 'diffViewContentDisplayed');
      let notifySyntaxProcessed;
      sinon.stub(element.syntaxLayer, 'process').returns(
          new Promise(resolve => {
            notifySyntaxProcessed = resolve;
          })
      );
      stubRestApi('getDiff').returns(Promise.resolve({content: []}));
      element.patchRange = {};
      element.change = createChange();
      element.prefs = createDefaultDiffPrefs();
      element.reload(true);
      // Multiple cascading microtasks are scheduled.
      await flush();
      notifySyntaxProcessed();
      await waitUntil(() => element.reporting.timeEnd.callCount === 4);
      const calls = element.reporting.timeEnd.getCalls();
      assert.equal(calls.length, 4);
      assert.equal(calls[0].args[0], 'Diff Load Render');
      assert.equal(calls[1].args[0], 'Diff Content Render');
      assert.equal(calls[2].args[0], 'Diff Syntax Render');
      assert.equal(calls[3].args[0], 'Diff Total Render');
      assert.isTrue(element.reporting.diffViewContentDisplayed.called);
    });

    test('completes reload promise after syntax layer processing', async () => {
      let notifySyntaxProcessed;
      sinon.stub(element.syntaxLayer, 'process').returns(new Promise(
          resolve => {
            notifySyntaxProcessed = resolve;
          }));
      stubRestApi('getDiff').returns(
          Promise.resolve({content: []}));
      element.patchRange = {};
      element.change = createChange();
      let reloadComplete = false;
      element.prefs = createDefaultDiffPrefs();
      element.reload().then(() => {
        reloadComplete = true;
      });
      // Multiple cascading microtasks are scheduled.
      await flush();
      assert.isFalse(reloadComplete);
      notifySyntaxProcessed();
      await waitUntil(() => reloadComplete);
      assert.isTrue(reloadComplete);
    });
  });

  test('reload() cancels before network resolves', () => {
    const cancelStub = sinon.stub(element.$.diff, 'cancel');

    // Stub the network calls into requests that never resolve.
    sinon.stub(element, '_getDiff').callsFake(() => new Promise(() => {}));
    element.patchRange = {};
    element.change = createChange();

    // Needs to be set to something first for it to cancel.
    element.diff = {
      content: [{
        a: ['foo'],
      }],
    };

    element.reload();
    assert.isTrue(cancelStub.called);
  });

  test('reload() loads files weblinks', async () => {
    element.change = createChange();
    const weblinksStub = sinon.stub(GerritNav, '_generateWeblinks')
        .returns({name: 'stubb', url: '#s'});
    stubRestApi('getDiff').returns(Promise.resolve({
      content: [],
    }));
    element.projectName = 'test-project';
    element.path = 'test-path';
    element.commitRange = {baseCommit: 'test-base', commit: 'test-commit'};
    element.patchRange = {};

    await element.reload();

    assert.equal(weblinksStub.callCount, 3);
    assert.deepEqual(weblinksStub.firstCall.args[0], {
      commit: 'test-base',
      file: 'test-path',
      options: {
        weblinks: undefined,
      },
      repo: 'test-project',
      type: GerritNav.WeblinkType.EDIT});
    assert.deepEqual(element.editWeblinks, [{
      name: 'stubb', url: '#s',
    }]);
    assert.deepEqual(weblinksStub.secondCall.args[0], {
      commit: 'test-base',
      file: 'test-path',
      options: {
        weblinks: undefined,
      },
      repo: 'test-project',
      type: GerritNav.WeblinkType.FILE});
    assert.deepEqual(weblinksStub.thirdCall.args[0], {
      commit: 'test-commit',
      file: 'test-path',
      options: {
        weblinks: undefined,
      },
      repo: 'test-project',
      type: GerritNav.WeblinkType.FILE});
    assert.deepEqual(element.filesWeblinks, {
      meta_a: [{name: 'stubb', url: '#s'}],
      meta_b: [{name: 'stubb', url: '#s'}],
    });
  });

  test('prefetch getDiff', async () => {
    const diffRestApiStub = stubRestApi('getDiff')
        .returns(Promise.resolve({content: []}));
    element.changeNum = 123;
    element.patchRange = {basePatchNum: 1, patchNum: 2};
    element.path = 'file.txt';
    element.prefetchDiff();
    await element._getDiff();
    assert.isTrue(diffRestApiStub.calledOnce);
  });

  test('_getDiff handles null diff responses', async () => {
    stubRestApi('getDiff').returns(Promise.resolve(null));
    element.changeNum = 123;
    element.patchRange = {basePatchNum: 1, patchNum: 2};
    element.path = 'file.txt';
    await element._getDiff();
  });

  test('reload resolves on error', () => {
    const onErrStub = sinon.stub(element, '_handleGetDiffError');
    const error = new Response(null, {ok: false, status: 500});
    stubRestApi('getDiff').callsFake(
        (changeNum, basePatchNum, patchNum, path, whitespace, onErr) => {
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
      addListenerForTest(document, 'server-error', serverErrorStub);
      pageErrorStub = sinon.stub();
      addListenerForTest(document, 'page-error', pageErrorStub);
    });

    test('page error on HTTP-409', () => {
      element._handleGetDiffError({status: 409});
      assert.isTrue(serverErrorStub.calledOnce);
      assert.isFalse(pageErrorStub.called);
      assert.isNotOk(element._errorMessage);
    });

    test('server error on non-HTTP-409', () => {
      element._handleGetDiffError({
        status: 500,
        text: () => Promise.resolve(''),
      });
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

      element.patchRange = {basePatchNum: 'PARENT', patchNum: 1};
      element.change = createChange();
      element.comments = {
        left: [],
        right: [],
        meta: {patchRange: element.patchRange},
      };
    });

    test('renders image diffs with same file name', async () => {
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
      stubRestApi('getDiff').returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(Promise.resolve({
        baseImage: {
          ...mockFile1,
          _expectedType: 'image/jpeg',
          _name: 'carrot.jpg',
        },
        revisionImage: {
          ...mockFile2,
          _expectedType: 'image/jpeg',
          _name: 'carrot.jpg',
        },
      }));

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await waitForEventOnce(element, 'render');

      // Recognizes that it should be an image diff.
      assert.isTrue(element.isImageDiff);
      assert.instanceOf(
          element.$.diff.diffBuilder.builder, GrDiffBuilderImage);

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

      assert.isOk(leftImage);
      assert.equal(leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body);
      assert.equal(leftLabelContent.textContent, '1×1 image/bmp');
      assert.isNotOk(leftLabelName);

      assert.isOk(rightImage);
      assert.equal(rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body);
      assert.equal(rightLabelContent.textContent, '1×1 image/bmp');
      assert.isNotOk(rightLabelName);
    });

    test('renders image diffs with a different file name', async () => {
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
      stubRestApi('getDiff').returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(Promise.resolve({
        baseImage: {
          ...mockFile1,
          _expectedType: 'image/jpeg',
          _name: 'carrot.jpg',
        },
        revisionImage: {
          ...mockFile2,
          _expectedType: 'image/jpeg',
          _name: 'carrot2.jpg',
        },
      }));

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await waitForEventOnce(element, 'render');

      // Recognizes that it should be an image diff.
      assert.isTrue(element.isImageDiff);
      assert.instanceOf(
          element.$.diff.diffBuilder.builder, GrDiffBuilderImage);

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

      assert.isOk(leftImage);
      assert.equal(leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body);
      assert.equal(leftLabelContent.textContent, '1×1 image/bmp');

      assert.isOk(rightImage);
      assert.equal(rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body);
      assert.equal(rightLabelContent.textContent, '1×1 image/bmp');
    });

    test('renders added image', async () => {
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
      stubRestApi('getDiff').returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(Promise.resolve({
        baseImage: null,
        revisionImage: {
          ...mockFile2,
          _expectedType: 'image/jpeg',
          _name: 'carrot2.jpg',
        },
      }));

      const promise = mockPromise();
      element.addEventListener('render', () => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(
            element.$.diff.diffBuilder.builder, GrDiffBuilderImage);

        const leftImage =
            element.$.diff.$.diffTable.querySelector('td.left img');
        const rightImage =
            element.$.diff.$.diffTable.querySelector('td.right img');

        assert.isNotOk(leftImage);
        assert.isOk(rightImage);
        promise.resolve();
      });

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await promise;
    });

    test('renders removed image', async () => {
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
      stubRestApi('getDiff').returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(Promise.resolve({
        baseImage: {
          ...mockFile1,
          _expectedType: 'image/jpeg',
          _name: 'carrot.jpg',
        },
        revisionImage: null,
      }));

      const promise = mockPromise();
      element.addEventListener('render', () => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(
            element.$.diff.diffBuilder.builder, GrDiffBuilderImage);

        const leftImage =
            element.$.diff.$.diffTable.querySelector('td.left img');
        const rightImage =
            element.$.diff.$.diffTable.querySelector('td.right img');

        assert.isOk(leftImage);
        assert.isNotOk(rightImage);
        promise.resolve();
      });

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await promise;
    });

    test('does not render disallowed image type', async () => {
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

      stubRestApi('getDiff').returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(Promise.resolve({
        baseImage: {
          ...mockFile1,
          _expectedType: 'image/jpeg',
          _name: 'carrot.jpg',
        },
        revisionImage: null,
      }));

      const promise = mockPromise();
      element.addEventListener('render', () => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(
            element.$.diff.diffBuilder.builder, GrDiffBuilderImage);
        const leftImage =
            element.$.diff.$.diffTable.querySelector('td.left img');
        assert.isNotOk(leftImage);
        promise.resolve();
      });

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await promise;
    });
  });

  test('cannot create comments when not logged in', () => {
    element.patchRange = {
      basePatchNum: 'PARENT',
      patchNum: 2,
    };
    const showAuthRequireSpy = sinon.spy();
    element.addEventListener('show-auth-required', showAuthRequireSpy);

    element.dispatchEvent(new CustomEvent('create-comment', {
      detail: {
        lineNum: 3,
        side: Side.LEFT,
        path: '/p',
      },
    }));

    const threads = dom(element.$.diff)
        .queryDistributedElements('gr-comment-thread');

    assert.equal(threads.length, 0);

    assert.isTrue(showAuthRequireSpy.called);
  });

  test('delegates cancel()', () => {
    const stub = sinon.stub(element.$.diff, 'cancel');
    element.patchRange = {};
    element.cancel();
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
    setup(async () => {
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'some/path';
      await flush();
    });

    test('clearBlame', () => {
      element._blame = [];
      const setBlameSpy = sinon.spy(element.$.diff.diffBuilder, 'setBlame');
      element.clearBlame();
      assert.isNull(element._blame);
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.equal(element.isBlameLoaded, false);
    });

    test('loadBlame', () => {
      const mockBlame = [{id: 'commit id', ranges: [{start: 1, end: 2}]}];
      const showAlertStub = sinon.stub();
      element.addEventListener('show-alert', showAlertStub);
      const getBlameStub = stubRestApi('getBlame')
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
      stubRestApi('getBlame')
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
    const threadEl = document.createElement('gr-comment-thread');
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

  test('delegates toggleAllContext()', () => {
    const stub = sinon.stub(element.$.diff, 'toggleAllContext');
    element.toggleAllContext();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('passes in noAutoRender', () => {
    const value = true;
    element.noAutoRender = value;
    assert.equal(element.$.diff.noAutoRender, value);
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
    const value = {lineNum: 123, side: Side.LEFT};
    element.lineOfInterest = value;
    assert.equal(element.$.diff.lineOfInterest, value);
  });

  suite('_reportDiff', () => {
    let reportStub;

    setup(async () => {
      element = basicFixture.instantiate();
      element.changeNum = 123;
      element.path = 'file.txt';
      element.patchRange = {basePatchNum: 1};
      reportStub = sinon.stub(element.reporting, 'reportInteraction');
      await flush();
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

  suite('createCheckEl method', () => {
    test('start_line:12', () => {
      const result = {
        codePointers: [{range: {start_line: 12}}],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-12');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), '12');
      assert.equal(el.getAttribute('range'), null);
      assert.equal(el.result, result);
    });

    test('start_line:13 end_line:14 without char positions', () => {
      const result = {
        codePointers: [{range: {start_line: 13, end_line: 14}}],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-14');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), '14');
      assert.equal(el.getAttribute('range'), null);
      assert.equal(el.result, result);
    });

    test('start_line:13 end_line:14 with char positions', () => {
      const result = {
        codePointers: [
          {
            range: {
              start_line: 13,
              end_line: 14,
              start_character: 5,
              end_character: 7,
            },
          },
        ],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-14');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), '14');
      assert.equal(el.getAttribute('range'),
          '{"start_line":13,' +
          '"end_line":14,' +
          '"start_character":5,' +
          '"end_character":7}');
      assert.equal(el.result, result);
    });

    test('empty range', () => {
      const result = {
        codePointers: [{range: {}}],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-FILE');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), 'FILE');
      assert.equal(el.getAttribute('range'), null);
      assert.equal(el.result, result);
    });
  });

  suite('create-comment', () => {
    setup(async () => {
      loggedIn = true;
      element.connectedCallback();
      await flush();
    });

    test('creates comments if they do not exist yet', () => {
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 2,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          lineNum: 3,
          side: Side.LEFT,
          path: '/p',
        },
      }));

      let threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 1);
      assert.equal(threads[0].thread.commentSide, 'PARENT');
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[0].thread.range, undefined);
      assert.equal(threads[0].thread.patchNum, 2);

      // Try to fetch a thread with a different range.
      const range = {
        start_line: 1,
        start_character: 1,
        end_line: 1,
        end_character: 3,
      };
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 3,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          lineNum: 1,
          side: Side.LEFT,
          path: '/p',
          range,
        },
      }));

      threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 2);
      assert.equal(threads[0].thread.commentSide, 'PARENT');
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[1].thread.range, range);
      assert.equal(threads[1].thread.patchNum, 3);
    });

    test('should not be on parent if on the right', () => {
      element.patchRange = {
        basePatchNum: 2,
        patchNum: 3,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.RIGHT,
        },
      }));

      const threadEl = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread')[0];

      assert.equal(threadEl.thread.commentSide, 'REVISION');
      assert.equal(threadEl.getAttribute('diff-side'), Side.RIGHT);
    });

    test('should be on parent if right and base is PARENT', () => {
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 3,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.LEFT,
        },
      }));

      const threadEl = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread')[0];

      assert.equal(threadEl.thread.commentSide, 'PARENT');
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test('should be on parent if right and base negative', () => {
      element.patchRange = {
        basePatchNum: -2, // merge parents have negative numbers
        patchNum: 3,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.LEFT,
        },
      }));

      const threadEl = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread')[0];

      assert.equal(threadEl.thread.commentSide, 'PARENT');
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test('should not be on parent otherwise', () => {
      element.patchRange = {
        basePatchNum: 2, // merge parents have negative numbers
        patchNum: 3,
      };

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.LEFT,
        },
      }));

      const threadEl = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread')[0];

      assert.equal(threadEl.thread.commentSide, 'REVISION');
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test('thread should use old file path if first created ' +
    'on patch set (left) before renaming', async () => {
      element.patchRange = {
        basePatchNum: 2,
        patchNum: 3,
      };
      element.file = {basePath: 'file_renamed.txt', path: element.path};
      await flush();

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.LEFT,
          path: '/p',
        },
      }));

      const threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 1);
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[0].thread.path, element.file.basePath);
    });

    test('thread should use new file path if first created ' +
    'on patch set (right) after renaming', async () => {
      element.patchRange = {
        basePatchNum: 2,
        patchNum: 3,
      };
      element.file = {basePath: 'file_renamed.txt', path: element.path};
      await flush();

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.RIGHT,
          path: '/p',
        },
      }));

      const threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 1);
      assert.equal(threads[0].getAttribute('diff-side'), Side.RIGHT);
      assert.equal(threads[0].thread.path, element.file.path);
    });

    test('multiple threads created on the same range', async () => {
      element.patchRange = {
        basePatchNum: 2,
        patchNum: 3,
      };
      element.file = {basePath: 'file_renamed.txt', path: element.path};
      await flush();

      const comment = {
        ...createComment(),
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 2,
        },
        patch_set: 3,
      };
      const thread = createCommentThread([comment]);
      element.threads = [thread];

      let threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 1);
      element.threads= [...element.threads, thread];

      threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');
      // Threads have same rootId so element is reused
      assert.equal(threads.length, 1);

      const newThread = {...thread};
      newThread.rootId = 'differentRootId';
      element.threads= [...element.threads, newThread];
      threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');
      // New thread has a different rootId
      assert.equal(threads.length, 2);
    });

    test('unsaved thread changes to draft', async () => {
      element.patchRange = {
        basePatchNum: 2,
        patchNum: 3,
      };
      element.file = {basePath: 'file_renamed.txt', path: element.path};
      element.threads = [];
      await flush();

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.RIGHT,
          path: element.path,
          lineNum: 13,
        },
      }));
      await flush();
      assert.equal(element.getThreadEls().length, 1);
      const threadEl = element.getThreadEls()[0];
      assert.equal(threadEl.thread.line, 13);
      assert.isDefined(threadEl.unsavedComment);
      assert.equal(threadEl.thread.comments.length, 0);

      const draftThread = createCommentThread([{
        path: element.path,
        patch_set: 3,
        line: 13,
        __draft: true,
      }]);
      element.threads = [draftThread];
      await flush();

      // We expect that no additional thread element was created.
      assert.equal(element.getThreadEls().length, 1);
      // In fact the thread element must still be the same.
      assert.equal(element.getThreadEls()[0], threadEl);
      // But it must have been updated from unsaved to draft:
      assert.isUndefined(threadEl.unsavedComment);
      assert.equal(threadEl.thread.comments.length, 1);
    });

    test('thread should use new file path if first created ' +
    'on patch set (left) but is base', async () => {
      element.patchRange = {
        basePatchNum: 'PARENT',
        patchNum: 3,
      };
      element.file = {basePath: 'file_renamed.txt', path: element.path};
      await flush();

      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: Side.LEFT,
          path: '/p',
        },
      }));

      const threads =
          dom(element.$.diff).queryDistributedElements('gr-comment-thread');

      assert.equal(threads.length, 1);
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[0].thread.path, element.file.path);
    });

    test('cannot create thread on an edit', () => {
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);

      const diffSide = Side.LEFT;
      element.patchRange = {
        basePatchNum: EditPatchSetNum,
        patchNum: 3,
      };
      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: diffSide,
          path: '/p',
        },
      }));

      const threads =
          dom(element.$.diff).queryDistributedElements('gr-comment-thread');
      assert.equal(threads.length, 0);
      assert.isTrue(alertSpy.called);
    });

    test('cannot create thread on an edit base', () => {
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);

      const diffSide = Side.LEFT;
      element.patchRange = {
        basePatchNum: ParentPatchSetNum,
        patchNum: EditPatchSetNum,
      };
      element.dispatchEvent(new CustomEvent('create-comment', {
        detail: {
          side: diffSide,
          path: '/p',
        },
      }));

      const threads = dom(element.$.diff)
          .queryDistributedElements('gr-comment-thread');
      assert.equal(threads.length, 0);
      assert.isTrue(alertSpy.called);
    });
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
    l3.setAttribute('diff-side', Side.LEFT);

    const l5 = document.createElement('div');
    l5.setAttribute('line-num', 5);
    l5.setAttribute('diff-side', Side.LEFT);

    const r3 = document.createElement('div');
    r3.setAttribute('line-num', 3);
    r3.setAttribute('diff-side', Side.RIGHT);

    const r5 = document.createElement('div');
    r5.setAttribute('line-num', 5);
    r5.setAttribute('diff-side', Side.RIGHT);

    const threadEls = [l3, l5, r3, r5];
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.LEFT), [l3]);
    assert.deepEqual(element._filterThreadElsForLocation(threadEls, line,
        Side.RIGHT), [r5]);
  });

  test('_filterThreadElsForLocation for file comments', () => {
    const line = {beforeNumber: 'FILE', afterNumber: 'FILE'};

    const l = document.createElement('div');
    l.setAttribute('diff-side', Side.LEFT);
    l.setAttribute('line-num', 'FILE');

    const r = document.createElement('div');
    r.setAttribute('diff-side', Side.RIGHT);
    r.setAttribute('line-num', 'FILE');

    const threadEls = [l, r];
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
      element.change = createChange();
      element.path = 'some/path';
    });

    test('gr-diff-host provides syntax highlighting layer', async () => {
      stubRestApi('getDiff').returns(Promise.resolve({content: []}));
      await element.reload();
      assert.equal(element.$.diff.layers[1], element.syntaxLayer);
    });

    test('rendering normal-sized diff does not disable syntax', () => {
      element.diff = {
        content: [{
          a: ['foo'],
        }],
      };
      assert.isTrue(element.syntaxLayer.enabled);
    });

    test('rendering large diff disables syntax', () => {
      // Before it renders, set the first diff line to 500 '*' characters.
      element.diff = {
        content: [{
          a: [new Array(501).join('*')],
        }],
      };
      assert.isFalse(element.syntaxLayer.enabled);
    });

    test('starts syntax layer processing on render event', async () => {
      sinon.stub(element.syntaxLayer, 'process')
          .returns(Promise.resolve());
      stubRestApi('getDiff').returns(Promise.resolve({content: []}));
      await element.reload();
      element.dispatchEvent(
          new CustomEvent('render', {bubbles: true, composed: true}));
      assert.isTrue(element.syntaxLayer.process.called);
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
      element.change = createChange();
      element.prefs = prefs;
    });

    test('gr-diff-host provides syntax highlighting layer', async () => {
      stubRestApi('getDiff').returns(Promise.resolve({content: []}));
      await element.reload();
      assert.equal(element.$.diff.layers[1], element.syntaxLayer);
    });

    test('syntax layer should be disabled', () => {
      assert.isFalse(element.syntaxLayer.enabled);
    });

    test('still disabled for large diff', () => {
      // Before it renders, set the first diff line to 500 '*' characters.
      element.diff = {
        content: [{
          a: [new Array(501).join('*')],
        }],
      };
      assert.isFalse(element.syntaxLayer.enabled);
    });
  });

  suite('coverage layer', () => {
    let notifyStub;
    let coverageProviderStub;
    const exampleRanges = [
      {
        type: CoverageType.COVERED,
        side: Side.RIGHT,
        code_range: {
          start_line: 1,
          end_line: 2,
        },
      },
      {
        type: CoverageType.NOT_COVERED,
        side: Side.RIGHT,
        code_range: {
          start_line: 3,
          end_line: 4,
        },
      },
    ];

    setup(async () => {
      notifyStub = sinon.stub();
      coverageProviderStub = sinon.stub().returns(
          Promise.resolve(exampleRanges));

      element = basicFixture.instantiate();
      sinon.stub(element.jsAPI, 'getCoverageAnnotationApis').returns(
          Promise.resolve([{
            notify: notifyStub,
            getCoverageProvider() {
              return coverageProviderStub;
            },
          }]));
      element.changeNum = 123;
      element.change = createChange();
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
      stubRestApi('getDiff').returns(Promise.resolve(element.diff));
      await flush();
    });

    test('getCoverageAnnotationApis should be called', async () => {
      await element.reload();
      assert.isTrue(element.jsAPI.getCoverageAnnotationApis.calledOnce);
    });

    test('coverageRangeChanged should be called', async () => {
      await element.reload();
      assert.equal(notifyStub.callCount, 2);
      assert.isTrue(notifyStub.calledWithExactly(
          'some/path', 1, 2, Side.RIGHT));
      assert.isTrue(notifyStub.calledWithExactly(
          'some/path', 3, 4, Side.RIGHT));
    });

    test('provider is called with appropriate params', async () => {
      element.patchRange.basePatchNum = 1;
      element.patchRange.patchNum = 3;

      await element.reload();
      assert.isTrue(coverageProviderStub.calledWithExactly(
          123, 'some/path', 1, 3, element.change));
    });

    test('provider is called with appropriate params - special patchset values',
        async () => {
          element.patchRange.basePatchNum = 'PARENT';
          element.patchRange.patchNum = 'invalid';

          await element.reload();
          assert.isTrue(coverageProviderStub.calledWithExactly(
              123, 'some/path', undefined, undefined, element.change));
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

