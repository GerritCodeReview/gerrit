/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-host';
import {
  CommentSide,
  createDefaultDiffPrefs,
  Side,
} from '../../../constants/constants';
import {
  createAccountDetailWithId,
  createBlame,
  createChange,
  createComment,
  createCommentThread,
  createDiff,
  createPatchRange,
  createRunResult,
} from '../../../test/test-data-generators';
import {
  addListenerForTest,
  mockPromise,
  query,
  queryAll,
  queryAndAssert,
  stubReporting,
  stubRestApi,
} from '../../../test/test-utils';
import {
  BasePatchSetNum,
  BlameInfo,
  CommentRange,
  EDIT,
  ImageInfo,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {CoverageType} from '../../../types/types';
import {GrDiffBuilderImage} from '../../../embed/diff/gr-diff-builder/gr-diff-builder-image';
import {GrDiffHost, LineInfo} from './gr-diff-host';
import {DiffInfo, DiffViewMode, IgnoreWhitespaceType} from '../../../api/diff';
import {ErrorCallback} from '../../../api/rest';
import {SinonStub} from 'sinon';
import {RunResult} from '../../../models/checks/checks-model';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken, UserModel} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-diff-host tests', () => {
  let element: GrDiffHost;
  let account = createAccountDetailWithId(1);
  let getDiffRestApiStub: SinonStub;
  let userModel: UserModel;

  setup(async () => {
    stubRestApi('getAccount').callsFake(() => Promise.resolve(account));
    element = await fixture(html`<gr-diff-host></gr-diff-host>`);
    element.changeNum = 123 as NumericChangeId;
    element.path = 'some/path';
    element.change = createChange();
    element.patchRange = createPatchRange();
    getDiffRestApiStub = stubRestApi('getDiff');
    // Fall back in case a test forgets to set one up
    getDiffRestApiStub.returns(Promise.resolve(createDiff()));
    await element.updateComplete;
    userModel = testResolver(userModelToken);
  });

  suite('render reporting', () => {
    test('ends total and syntax timer after syntax layer', async () => {
      const displayedStub = stubReporting('diffViewContentDisplayed');

      element.patchRange = createPatchRange();
      element.change = createChange();
      element.prefs = createDefaultDiffPrefs();
      await element.updateComplete;
      // Force a reload because it's not possible to wait on the reload called
      // from update().
      await element.reload();
      const timeEndStub = sinon.stub(element.reporting, 'timeEnd');
      let notifySyntaxProcessed: () => void = () => {};
      sinon.stub(element.syntaxLayer, 'process').returns(
        new Promise(resolve => {
          notifySyntaxProcessed = resolve;
        })
      );
      const promise = element.reload(true);
      // Multiple cascading microtasks are scheduled.
      notifySyntaxProcessed();
      await element.updateComplete;
      await promise;
      const calls = timeEndStub.getCalls();
      assert.equal(calls.length, 4);
      assert.equal(calls[0].args[0], 'Diff Load Render');
      assert.equal(calls[1].args[0], 'Diff Content Render');
      assert.equal(calls[2].args[0], 'Diff Syntax Render');
      assert.equal(calls[3].args[0], 'Diff Total Render');
      assert.isTrue(displayedStub.called);
    });

    test('completes reload promise after syntax layer processing', async () => {
      let notifySyntaxProcessed: () => void = () => {};
      sinon.stub(element.syntaxLayer, 'process').returns(
        new Promise(resolve => {
          notifySyntaxProcessed = resolve;
        })
      );
      getDiffRestApiStub.returns(Promise.resolve(createDiff()));
      element.patchRange = createPatchRange();
      element.change = createChange();
      let reloadComplete = false;
      element.prefs = createDefaultDiffPrefs();
      const promise = mockPromise();
      element.reload().then(() => {
        reloadComplete = true;
        promise.resolve();
      });
      // Multiple cascading microtasks are scheduled.
      assert.isFalse(reloadComplete);
      notifySyntaxProcessed();
      await promise;
      assert.isTrue(reloadComplete);
    });
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-diff
          id="diff"
          style="--line-limit-marker:-1px; --content-width:100ch; --diff-max-width:none; --font-size:12px;"
        >
        </gr-diff>
      `
    );
  });

  test('reload() cancels before network resolves', async () => {
    assertIsDefined(element.diffElement);
    const cancelStub = sinon.stub(element.diffElement, 'cancel');

    // Stub the network calls into requests that never resolve.
    sinon.stub(element, 'getDiff').callsFake(() => new Promise(() => {}));
    element.patchRange = createPatchRange();
    element.change = createChange();
    element.prefs = undefined;

    // Needs to be set to something first for it to cancel.
    element.diff = createDiff();
    await element.updateComplete;

    element.reload();
    assert.isTrue(cancelStub.called);
  });

  test('prefetch getDiff', async () => {
    getDiffRestApiStub.returns(Promise.resolve(createDiff()));
    element.changeNum = 123 as NumericChangeId;
    element.patchRange = createPatchRange();
    element.path = 'file.txt';
    element.prefetchDiff();
    await element.getDiff();
    assert.isTrue(getDiffRestApiStub.calledOnce);
  });

  test('getDiff handles undefined diff responses', async () => {
    getDiffRestApiStub.returns(Promise.resolve(undefined));
    element.changeNum = 123 as NumericChangeId;
    element.patchRange = createPatchRange();
    element.path = 'file.txt';
    await element.getDiff();
  });

  test('reload resolves on error', () => {
    const onErrStub = sinon.stub(element, 'handleGetDiffError');
    const error = new Response(null, {status: 500});
    getDiffRestApiStub.callsFake(
      (
        _1: NumericChangeId,
        _2: PatchSetNum,
        _3: PatchSetNum,
        _4: string,
        _5?: IgnoreWhitespaceType,
        onErr?: ErrorCallback
      ) => {
        if (onErr) onErr(error);
        return Promise.resolve(undefined);
      }
    );
    element.patchRange = createPatchRange();
    return element.reload().then(() => {
      assert.isTrue(onErrStub.calledOnce);
    });
  });

  suite('handleGetDiffError', () => {
    let serverErrorStub: sinon.SinonStub;
    let pageErrorStub: sinon.SinonStub;

    setup(() => {
      serverErrorStub = sinon.stub();
      addListenerForTest(document, 'server-error', serverErrorStub);
      pageErrorStub = sinon.stub();
      addListenerForTest(document, 'page-error', pageErrorStub);
    });

    test('page error on HTTP-409', () => {
      element.handleGetDiffError({status: 409} as Response);
      assert.isTrue(serverErrorStub.calledOnce);
      assert.isFalse(pageErrorStub.called);
      assert.isNotOk(element.errorMessage);
    });

    test('server error on non-HTTP-409', () => {
      element.handleGetDiffError({
        status: 500,
        text: () => Promise.resolve(''),
      } as Response);
      assert.isFalse(serverErrorStub.called);
      assert.isTrue(pageErrorStub.calledOnce);
      assert.isNotOk(element.errorMessage);
    });

    test('error message if showLoadFailure', () => {
      element.showLoadFailure = true;
      element.handleGetDiffError({
        status: 500,
        statusText: 'Failure!',
      } as Response);
      assert.isFalse(serverErrorStub.called);
      assert.isFalse(pageErrorStub.called);
      assert.equal(
        element.errorMessage,
        'Encountered error when loading the diff: 500 Failure!'
      );
    });
  });

  suite('image diffs', () => {
    let mockFile1: ImageInfo;
    let mockFile2: ImageInfo;
    setup(() => {
      mockFile1 = {
        body:
          'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAAAAAA/w==',
        type: 'image/bmp',
        _expectedType: 'image/bmp',
        _name: 'carrot.bmp',
      };
      mockFile2 = {
        body:
          'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAA/////w==',
        type: 'image/bmp',
        _expectedType: 'image/bmp',
        _name: 'potato.bmp',
      };

      element.patchRange = createPatchRange();
      element.change = createChange();
    });

    test('renders image diffs with same file name', async () => {
      const mockDiff: DiffInfo = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
        meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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
      getDiffRestApiStub.returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(
        Promise.resolve({
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
        })
      );

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await element.waitForReloadToRender();

      // Recognizes that it should be an image diff.
      assert.isTrue(element.isImageDiff);
      assertIsDefined(element.diffElement);
      assert.instanceOf(
        element.diffElement.diffBuilder.builder,
        GrDiffBuilderImage
      );

      // Left image rendered with the parent commit's version of the file.
      assertIsDefined(element.diffElement);
      assertIsDefined(element.diffElement.diffTable);
      const diffTable = element.diffElement.diffTable;
      const leftImage = queryAndAssert(diffTable, 'td.left img');
      const leftLabel = queryAndAssert(diffTable, 'td.left label');
      const leftLabelContent = leftLabel.querySelector('.label');
      const leftLabelName = leftLabel.querySelector('.name');

      const rightImage = queryAndAssert(diffTable, 'td.right img');
      const rightLabel = queryAndAssert(diffTable, 'td.right label');
      const rightLabelContent = rightLabel.querySelector('.label');
      const rightLabelName = rightLabel.querySelector('.name');

      assert.isOk(leftImage);
      assert.equal(
        leftImage.getAttribute('src'),
        'data:image/bmp;base64,' + mockFile1.body
      );
      assert.isTrue(leftLabelContent?.textContent?.includes('image/bmp'));
      assert.isNotOk(leftLabelName);

      assert.isOk(rightImage);
      assert.equal(
        rightImage.getAttribute('src'),
        'data:image/bmp;base64,' + mockFile2.body
      );
      assert.isTrue(rightLabelContent?.textContent?.includes('image/bmp'));
      assert.isNotOk(rightLabelName);
    });

    test('renders image diffs with a different file name', async () => {
      const mockDiff: DiffInfo = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
        meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg', lines: 560},
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
      getDiffRestApiStub.returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(
        Promise.resolve({
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
        })
      );

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await element.waitForReloadToRender();

      // Recognizes that it should be an image diff.
      assert.isTrue(element.isImageDiff);
      assertIsDefined(element.diffElement);
      assert.instanceOf(
        element.diffElement.diffBuilder.builder,
        GrDiffBuilderImage
      );

      // Left image rendered with the parent commit's version of the file.
      assertIsDefined(element.diffElement.diffTable);
      const diffTable = element.diffElement.diffTable;
      const leftImage = queryAndAssert(diffTable, 'td.left img');
      const leftLabel = queryAndAssert(diffTable, 'td.left label');
      const leftLabelContent = leftLabel.querySelector('.label');
      const leftLabelName = leftLabel.querySelector('.name');

      const rightImage = queryAndAssert(diffTable, 'td.right img');
      const rightLabel = queryAndAssert(diffTable, 'td.right label');
      const rightLabelContent = rightLabel.querySelector('.label');
      const rightLabelName = rightLabel.querySelector('.name');

      assert.isOk(rightLabelName);
      assert.isOk(leftLabelName);
      assert.equal(leftLabelName?.textContent, mockDiff.meta_a?.name);
      assert.equal(rightLabelName?.textContent, mockDiff.meta_b?.name);

      assert.isOk(leftImage);
      assert.equal(
        leftImage.getAttribute('src'),
        'data:image/bmp;base64,' + mockFile1.body
      );
      assert.isTrue(leftLabelContent?.textContent?.includes('image/bmp'));

      assert.isOk(rightImage);
      assert.equal(
        rightImage.getAttribute('src'),
        'data:image/bmp;base64,' + mockFile2.body
      );
      assert.isTrue(rightLabelContent?.textContent?.includes('image/bmp'));
    });

    test('renders added image', async () => {
      const mockDiff: DiffInfo = {
        meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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
      getDiffRestApiStub.returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(
        Promise.resolve({
          baseImage: null,
          revisionImage: {
            ...mockFile2,
            _expectedType: 'image/jpeg',
            _name: 'carrot2.jpg',
          },
        })
      );

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await element.waitForReloadToRender().then(() => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assertIsDefined(element.diffElement);
        assert.instanceOf(
          element.diffElement.diffBuilder.builder,
          GrDiffBuilderImage
        );
        assertIsDefined(element.diffElement.diffTable);
        const diffTable = element.diffElement.diffTable;

        const leftImage = query(diffTable, 'td.left img');
        const rightImage = queryAndAssert(diffTable, 'td.right img');

        assert.isNotOk(leftImage);
        assert.isOk(rightImage);
      });
    });

    test('renders removed image', async () => {
      const mockDiff: DiffInfo = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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
      getDiffRestApiStub.returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(
        Promise.resolve({
          baseImage: {
            ...mockFile1,
            _expectedType: 'image/jpeg',
            _name: 'carrot.jpg',
          },
          revisionImage: null,
        })
      );

      element.prefs = createDefaultDiffPrefs();
      element.reload();
      await element.waitForReloadToRender().then(() => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assertIsDefined(element.diffElement);
        assert.instanceOf(
          element.diffElement.diffBuilder.builder,
          GrDiffBuilderImage
        );

        assertIsDefined(element.diffElement.diffTable);
        const diffTable = element.diffElement.diffTable;

        const leftImage = queryAndAssert(diffTable, 'td.left img');
        const rightImage = query(diffTable, 'td.right img');

        assert.isOk(leftImage);
        assert.isNotOk(rightImage);
      });
    });

    test('does not render disallowed image type', async () => {
      const mockDiff: DiffInfo = {
        meta_a: {
          name: 'carrot.jpg',
          content_type: 'image/jpeg-evil',
          lines: 560,
        },
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

      getDiffRestApiStub.returns(Promise.resolve(mockDiff));
      stubRestApi('getImagesForDiff').returns(
        Promise.resolve({
          baseImage: {
            ...mockFile1,
            _expectedType: 'image/jpeg',
            _name: 'carrot.jpg',
          },
          revisionImage: null,
        })
      );

      element.prefs = createDefaultDiffPrefs();
      element.updateComplete.then(() => {
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assertIsDefined(element.diffElement);
        assert.instanceOf(
          element.diffElement.diffBuilder.builder,
          GrDiffBuilderImage
        );
        assertIsDefined(element.diffElement.diffTable);
        const diffTable = element.diffElement.diffTable;

        const leftImage = query(diffTable, 'td.left img');
        assert.isNotOk(leftImage);
      });
    });
  });

  test('cannot create comments when not logged in', () => {
    userModel.setAccount(undefined);
    element.patchRange = createPatchRange();
    const showAuthRequireSpy = sinon.spy();
    element.addEventListener('show-auth-required', showAuthRequireSpy);

    element.dispatchEvent(
      new CustomEvent('create-comment', {
        detail: {
          lineNum: 3,
          side: Side.LEFT,
          path: '/p',
        },
      })
    );

    assertIsDefined(element.diffElement);
    const threads = queryAll(element.diffElement, 'gr-comment-thread');
    assert.equal(threads.length, 0);
    assert.isTrue(showAuthRequireSpy.called);
  });

  test('delegates cancel()', () => {
    assertIsDefined(element.diffElement);
    const stub = sinon.stub(element.diffElement, 'cancel');
    element.patchRange = createPatchRange();
    element.cancel();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates getCursorStops()', () => {
    const returnValue = [document.createElement('b')];
    assertIsDefined(element.diffElement);
    const stub = sinon
      .stub(element.diffElement, 'getCursorStops')
      .returns(returnValue);
    assert.equal(element.getCursorStops(), returnValue);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates isRangeSelected()', () => {
    const returnValue = true;
    assertIsDefined(element.diffElement);
    const stub = sinon
      .stub(element.diffElement, 'isRangeSelected')
      .returns(returnValue);
    assert.equal(element.isRangeSelected(), returnValue);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates toggleLeftDiff()', () => {
    assertIsDefined(element.diffElement);
    const stub = sinon.stub(element.diffElement, 'toggleLeftDiff');
    element.toggleLeftDiff();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  suite('blame', () => {
    setup(async () => {
      element = await fixture(html`<gr-diff-host></gr-diff-host>`);
      element.changeNum = 123 as NumericChangeId;
      element.path = 'some/path';
      await element.updateComplete;
    });

    test('clearBlame', async () => {
      element.blame = [];
      await element.updateComplete;
      assertIsDefined(element.diffElement);
      const setBlameSpy = sinon.spy(
        element.diffElement.diffBuilder,
        'setBlame'
      );
      const isBlameLoadedStub = sinon.stub();
      element.addEventListener('is-blame-loaded-changed', isBlameLoadedStub);
      element.clearBlame();
      await element.updateComplete;
      assert.isNull(element.blame);
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.isTrue(isBlameLoadedStub.calledOnce);
      assert.isFalse(isBlameLoadedStub.args[0][0].detail.value);
    });

    test('loadBlame', async () => {
      const mockBlame: BlameInfo[] = [createBlame()];
      const showAlertStub = sinon.stub();
      element.addEventListener(EventType.SHOW_ALERT, showAlertStub);
      const getBlameStub = stubRestApi('getBlame').returns(
        Promise.resolve(mockBlame)
      );
      const changeNum = 42 as NumericChangeId;
      element.changeNum = changeNum;
      element.patchRange = createPatchRange();
      element.path = 'foo/bar.baz';
      await element.updateComplete;
      const isBlameLoadedStub = sinon.stub();
      element.addEventListener('is-blame-loaded-changed', isBlameLoadedStub);

      return element.loadBlame().then(() => {
        assert.isTrue(
          getBlameStub.calledWithExactly(
            changeNum,
            1 as RevisionPatchSetNum,
            'foo/bar.baz',
            true
          )
        );
        assert.isFalse(showAlertStub.called);
        assert.equal(element.blame, mockBlame);
        assert.isTrue(isBlameLoadedStub.calledOnce);
        assert.isTrue(isBlameLoadedStub.args[0][0].detail.value);
      });
    });

    test('loadBlame empty', async () => {
      const mockBlame: BlameInfo[] = [];
      const showAlertStub = sinon.stub();
      const isBlameLoadedStub = sinon.stub();
      element.addEventListener(EventType.SHOW_ALERT, showAlertStub);
      element.addEventListener('is-blame-loaded-changed', isBlameLoadedStub);
      stubRestApi('getBlame').returns(Promise.resolve(mockBlame));
      const changeNum = 42 as NumericChangeId;
      element.changeNum = changeNum;
      element.patchRange = createPatchRange();
      element.path = 'foo/bar.baz';
      await element.updateComplete;
      return element
        .loadBlame()
        .then(() => {
          assert.isTrue(false, 'Promise should not resolve');
        })
        .catch(() => {
          assert.isTrue(showAlertStub.calledOnce);
          assert.isNull(element.blame);
          // We don't expect a call because
          assert.isTrue(isBlameLoadedStub.notCalled);
        });
    });
  });

  test('getThreadEls() returns .comment-threads', () => {
    const threadEl = document.createElement('gr-comment-thread');
    threadEl.className = 'comment-thread';
    assertIsDefined(element.diffElement);
    element.diffElement.appendChild(threadEl);
    assert.deepEqual(element.getThreadEls(), [threadEl]);
  });

  test('delegates addDraftAtLine(el)', () => {
    const param0 = document.createElement('b');
    assertIsDefined(element.diffElement);
    const stub = sinon.stub(element.diffElement, 'addDraftAtLine');
    element.addDraftAtLine(param0);
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 1);
    assert.equal(stub.lastCall.args[0], param0);
  });

  test('delegates clearDiffContent()', () => {
    assertIsDefined(element.diffElement);
    const stub = sinon.stub(element.diffElement, 'clearDiffContent');
    element.clearDiffContent();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('delegates toggleAllContext()', () => {
    assertIsDefined(element.diffElement);
    const stub = sinon.stub(element.diffElement, 'toggleAllContext');
    element.toggleAllContext();
    assert.isTrue(stub.calledOnce);
    assert.equal(stub.lastCall.args.length, 0);
  });

  test('passes in noAutoRender', async () => {
    const value = true;
    element.noAutoRender = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.noAutoRender, value);
  });

  test('passes in path', async () => {
    const value = 'some/file/path';
    element.path = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.path, value);
  });

  test('passes in prefs', async () => {
    const value = createDefaultDiffPrefs();
    element.prefs = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.prefs, value);
  });

  test('passes in displayLine', async () => {
    const value = true;
    element.displayLine = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.displayLine, value);
  });

  test('passes in hidden', async () => {
    const value = true;
    element.hidden = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.hidden, value);
    assert.isNotNull(element.getAttribute('hidden'));
  });

  test('passes in noRenderOnPrefsChange', async () => {
    const value = true;
    element.noRenderOnPrefsChange = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.noRenderOnPrefsChange, value);
  });

  test('passes in lineWrapping', async () => {
    const value = true;
    element.lineWrapping = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.lineWrapping, value);
  });

  test('passes in viewMode', async () => {
    const value = DiffViewMode.SIDE_BY_SIDE;
    element.viewMode = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.viewMode, value);
  });

  test('passes in lineOfInterest', async () => {
    const value = {lineNum: 123, side: Side.LEFT};
    element.lineOfInterest = value;
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    assert.equal(element.diffElement.lineOfInterest, value);
  });

  suite('reportDiff', () => {
    let reportStub: SinonStub;

    setup(async () => {
      element = await fixture(html`<gr-diff-host></gr-diff-host>`);
      element.changeNum = 123 as NumericChangeId;
      element.path = 'file.txt';
      element.patchRange = createPatchRange(1, 2);
      reportStub = sinon.stub(element.reporting, 'reportInteraction');
      await element.updateComplete;
      reportStub.reset();
    });

    test('undefined', () => {
      element.reportDiff(undefined);
      assert.isFalse(reportStub.called);
    });

    test('diff w/ no delta', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [{ab: ['foo', 'bar']}, {ab: ['baz', 'foo']}],
      };
      element.reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'rebase-percent-zero');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });

    test('diff w/ no rebase delta', () => {
      const diff: DiffInfo = {
        ...createDiff(),
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
      element.reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'rebase-percent-zero');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });

    test('diff w/ some rebase delta', () => {
      const diff: DiffInfo = {
        ...createDiff(),
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
      element.reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.isTrue(
        reportStub.calledWith('rebase-percent-nonzero', {
          percentRebaseDelta: 50,
        })
      );
    });

    test('diff w/ all rebase delta', () => {
      const diff: DiffInfo = {
        ...createDiff(),
        content: [
          {
            a: ['foo', 'bar'],
            b: ['baz', 'foo'],
            due_to_rebase: true,
          },
        ],
      };
      element.reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.isTrue(
        reportStub.calledWith('rebase-percent-nonzero', {
          percentRebaseDelta: 100,
        })
      );
    });

    test('diff against parent event', () => {
      element.patchRange = createPatchRange();
      const diff: DiffInfo = {
        ...createDiff(),
        content: [
          {
            a: ['foo', 'bar'],
            b: ['baz', 'foo'],
          },
        ],
      };
      element.reportDiff(diff);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'diff-against-parent');
      assert.isUndefined(reportStub.lastCall.args[1]);
    });
  });

  suite('createCheckEl method', () => {
    test('start_line:12', () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [{path: 'a', range: {start_line: 12} as CommentRange}],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-12');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), '12');
      assert.equal(el.getAttribute('range'), null);
      assert.equal(el.result, result);
    });

    test('start_line:13 end_line:14 without char positions', () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [
          {path: 'a', range: {start_line: 13, end_line: 14} as CommentRange},
        ],
      };
      const el = element.createCheckEl(result);
      assert.equal(el.getAttribute('slot'), 'right-14');
      assert.equal(el.getAttribute('diff-side'), 'right');
      assert.equal(el.getAttribute('line-num'), '14');
      assert.equal(el.getAttribute('range'), null);
      assert.equal(el.result, result);
    });

    test('start_line:13 end_line:14 with char positions', () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [
          {
            path: 'a',
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
      assert.equal(
        el.getAttribute('range'),
        '{"start_line":13,' +
          '"end_line":14,' +
          '"start_character":5,' +
          '"end_character":7}'
      );
      assert.equal(el.result, result);
    });

    test('empty range', () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [{path: 'a', range: {} as CommentRange}],
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
      account = createAccountDetailWithId(1);
      element.disconnectedCallback();
      element.connectedCallback();
      await element.updateComplete;
    });

    test('creates comments if they do not exist yet', async () => {
      element.patchRange = createPatchRange();
      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            lineNum: 3,
            side: Side.LEFT,
            path: '/p',
          },
        })
      );
      assertIsDefined(element.diffElement);
      let threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );

      assert.equal(threads.length, 1);
      assert.equal(threads[0].thread?.commentSide, CommentSide.PARENT);
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[0].thread?.range, undefined);
      assert.equal(threads[0].thread?.patchNum, 1 as RevisionPatchSetNum);

      // Try to fetch a thread with a different range.
      const range = {
        start_line: 1,
        start_character: 1,
        end_line: 1,
        end_character: 3,
      };
      element.patchRange = createPatchRange();

      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            lineNum: 1,
            side: Side.LEFT,
            path: '/p',
            range,
          },
        })
      );

      assertIsDefined(element.diffElement);
      threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );

      assert.equal(threads.length, 2);
      assert.equal(threads[0].thread?.commentSide, CommentSide.PARENT);
      assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
      assert.equal(threads[1].thread?.range, range);
      assert.equal(threads[1].thread?.patchNum, 1 as RevisionPatchSetNum);
    });

    test('should not be on parent if on the right', async () => {
      element.patchRange = createPatchRange(2, 3);
      // Need to recompute threads.
      await element.updateComplete;
      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: Side.RIGHT,
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      assert.equal(threads.length, 1);
      const threadEl = threads[0];

      assert.equal(threadEl.thread?.commentSide, CommentSide.REVISION);
      assert.equal(threadEl.getAttribute('diff-side'), Side.RIGHT);
    });

    test('should be on parent if right and base is PARENT', () => {
      element.patchRange = createPatchRange();

      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: Side.LEFT,
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      const threadEl = threads[0];

      assert.equal(threadEl.thread?.commentSide, CommentSide.PARENT);
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test('should be on parent if right and base negative', () => {
      element.patchRange = createPatchRange(-2, 3);

      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: Side.LEFT,
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      const threadEl = threads[0];

      assert.equal(threadEl.thread?.commentSide, CommentSide.PARENT);
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test('should not be on parent otherwise', () => {
      element.patchRange = createPatchRange(2, 3);
      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: Side.LEFT,
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      const threadEl = threads[0];

      assert.equal(threadEl.thread?.commentSide, CommentSide.REVISION);
      assert.equal(threadEl.getAttribute('diff-side'), Side.LEFT);
    });

    test(
      'thread should use old file path if first created ' +
        'on patch set (left) before renaming',
      async () => {
        element.patchRange = createPatchRange(2, 3);
        element.file = {basePath: 'file_renamed.txt', path: element.path ?? ''};
        await element.updateComplete;

        element.dispatchEvent(
          new CustomEvent('create-comment', {
            detail: {
              side: Side.LEFT,
              path: '/p',
            },
          })
        );

        assertIsDefined(element.diffElement);
        const threads =
          element.diffElement.querySelectorAll<GrCommentThread>(
            'gr-comment-thread'
          );
        assert.equal(threads.length, 1);
        assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
        assert.equal(threads[0].thread?.path, element.file.basePath);
      }
    );

    test(
      'thread should use new file path if first created ' +
        'on patch set (right) after renaming',
      async () => {
        element.patchRange = createPatchRange(2, 3);
        element.file = {basePath: 'file_renamed.txt', path: element.path ?? ''};
        await element.updateComplete;

        element.dispatchEvent(
          new CustomEvent('create-comment', {
            detail: {
              side: Side.RIGHT,
              path: '/p',
            },
          })
        );

        assertIsDefined(element.diffElement);
        const threads =
          element.diffElement.querySelectorAll<GrCommentThread>(
            'gr-comment-thread'
          );

        assert.equal(threads.length, 1);
        assert.equal(threads[0].getAttribute('diff-side'), Side.RIGHT);
        assert.equal(threads[0].thread?.path, element.file.path);
      }
    );

    test('multiple threads created on the same range', async () => {
      element.patchRange = createPatchRange(2, 3);
      element.file = {basePath: 'file_renamed.txt', path: element.path ?? ''};
      await element.updateComplete;

      const comment = {
        ...createComment(),
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 2,
        },
        patch_set: 3 as RevisionPatchSetNum,
      };
      const thread = createCommentThread([comment]);
      element.threads = [thread];
      await element.updateComplete;

      assertIsDefined(element.diffElement);
      let threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );

      assert.equal(threads.length, 1);
      element.threads = [...element.threads, thread];
      await element.updateComplete;

      assertIsDefined(element.diffElement);
      threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      // Threads have same rootId so element is reused
      assert.equal(threads.length, 1);

      const newThread = {...thread};
      newThread.rootId = 'differentRootId' as UrlEncodedCommentId;
      element.threads = [...element.threads, newThread];
      await element.updateComplete;
      threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      // New thread has a different rootId
      assert.equal(threads.length, 2);
    });

    test('unsaved thread changes to draft', async () => {
      element.patchRange = createPatchRange(2, 3);
      element.file = {basePath: 'file_renamed.txt', path: element.path ?? ''};
      element.threads = [];
      await element.updateComplete;

      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: Side.RIGHT,
            path: element.path,
            lineNum: 13,
          },
        })
      );
      await element.updateComplete;
      assert.equal(element.getThreadEls().length, 1);
      const threadEl = element.getThreadEls()[0];
      assert.equal(threadEl.thread?.line, 13);
      assert.isDefined(threadEl.unsavedComment);
      assert.equal(threadEl.thread?.comments.length, 0);

      const draftThread = createCommentThread([
        {
          path: element.path,
          patch_set: 3 as RevisionPatchSetNum,
          line: 13,
          __draft: true,
        },
      ]);
      element.threads = [draftThread];
      await element.updateComplete;

      // We expect that no additional thread element was created.
      assert.equal(element.getThreadEls().length, 1);
      // In fact the thread element must still be the same.
      assert.equal(element.getThreadEls()[0], threadEl);
      // But it must have been updated from unsaved to draft:
      assert.isUndefined(threadEl.unsavedComment);
      assert.equal(threadEl.thread?.comments.length, 1);
    });

    test(
      'thread should use new file path if first created ' +
        'on patch set (left) but is base',
      async () => {
        element.patchRange = createPatchRange();
        element.file = {basePath: 'file_renamed.txt', path: element.path ?? ''};
        await element.updateComplete;

        element.dispatchEvent(
          new CustomEvent('create-comment', {
            detail: {
              side: Side.LEFT,
              path: '/p',
            },
          })
        );

        assertIsDefined(element.diffElement);
        const threads =
          element.diffElement.querySelectorAll<GrCommentThread>(
            'gr-comment-thread'
          );

        assert.equal(threads.length, 1);
        assert.equal(threads[0].getAttribute('diff-side'), Side.LEFT);
        assert.equal(threads[0].thread?.path, element.file.path);
      }
    );

    test('cannot create thread on an edit', () => {
      const alertSpy = sinon.spy();
      element.addEventListener(EventType.SHOW_ALERT, alertSpy);

      const diffSide = Side.RIGHT;
      element.patchRange = {
        basePatchNum: 3 as BasePatchSetNum,
        patchNum: EDIT,
      };
      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: diffSide,
            path: '/p',
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );

      assert.equal(threads.length, 0);
      assert.isTrue(alertSpy.called);
    });

    test('cannot create thread on an edit base', () => {
      const alertSpy = sinon.spy();
      element.addEventListener(EventType.SHOW_ALERT, alertSpy);

      const diffSide = Side.LEFT;
      element.patchRange = {
        basePatchNum: PARENT,
        patchNum: EDIT,
      };
      element.dispatchEvent(
        new CustomEvent('create-comment', {
          detail: {
            side: diffSide,
            path: '/p',
          },
        })
      );

      assertIsDefined(element.diffElement);
      const threads =
        element.diffElement.querySelectorAll<GrCommentThread>(
          'gr-comment-thread'
        );
      assert.equal(threads.length, 0);
      assert.isTrue(alertSpy.called);
    });
  });

  test('filterThreadElsForLocation with no threads', () => {
    const line = {beforeNumber: 3, afterNumber: 5};
    const threads: GrCommentThread[] = [];
    assert.deepEqual(
      element.filterThreadElsForLocation(threads, line, Side.LEFT),
      []
    );
    assert.deepEqual(
      element.filterThreadElsForLocation(threads, line, Side.RIGHT),
      []
    );
  });

  test('filterThreadElsForLocation for line comments', () => {
    const line = {beforeNumber: 3, afterNumber: 5};

    const l3 = document.createElement('gr-comment-thread');
    l3.setAttribute('line-num', '3');
    l3.setAttribute('diff-side', Side.LEFT);

    const l5 = document.createElement('gr-comment-thread');
    l5.setAttribute('line-num', '5');
    l5.setAttribute('diff-side', Side.LEFT);

    const r3 = document.createElement('gr-comment-thread');
    r3.setAttribute('line-num', '3');
    r3.setAttribute('diff-side', Side.RIGHT);

    const r5 = document.createElement('gr-comment-thread');
    r5.setAttribute('line-num', '5');
    r5.setAttribute('diff-side', Side.RIGHT);

    const threadEls: GrCommentThread[] = [l3, l5, r3, r5];
    assert.deepEqual(
      element.filterThreadElsForLocation(threadEls, line, Side.LEFT),
      [l3]
    );
    assert.deepEqual(
      element.filterThreadElsForLocation(threadEls, line, Side.RIGHT),
      [r5]
    );
  });

  test('filterThreadElsForLocation for file comments', () => {
    const line: LineInfo = {beforeNumber: 'FILE', afterNumber: 'FILE'};

    const l = document.createElement('gr-comment-thread');
    l.setAttribute('diff-side', Side.LEFT);
    l.setAttribute('line-num', 'FILE');

    const r = document.createElement('gr-comment-thread');
    r.setAttribute('diff-side', Side.RIGHT);
    r.setAttribute('line-num', 'FILE');

    const threadEls: GrCommentThread[] = [l, r];
    assert.deepEqual(
      element.filterThreadElsForLocation(threadEls, line, Side.LEFT),
      [l]
    );
    assert.deepEqual(
      element.filterThreadElsForLocation(threadEls, line, Side.RIGHT),
      [r]
    );
  });

  suite('syntax layer with syntax_highlighting on', async () => {
    setup(async () => {
      const prefs = {
        ...createDefaultDiffPrefs(),
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
        syntax_highlighting: true,
      };
      element.patchRange = createPatchRange();
      element.prefs = prefs;
      element.changeNum = 123 as NumericChangeId;
      element.change = createChange();
      element.path = 'some/path';
    });

    test('gr-diff-host provides syntax highlighting layer', async () => {
      getDiffRestApiStub.returns(Promise.resolve(createDiff()));
      await element.updateComplete;
      assertIsDefined(element.diffElement);
      assertIsDefined(element.diffElement.layers);
      assert.equal(element.diffElement.layers[1], element.syntaxLayer);
    });

    test('rendering normal-sized diff does not disable syntax', async () => {
      element.diff = createDiff();
      getDiffRestApiStub.returns(Promise.resolve(element.diff));
      await element.updateComplete;
      assert.isTrue(element.syntaxLayer.enabled);
    });

    test('rendering large diff disables syntax', async () => {
      // Before it renders, set the first diff line to 500 '*' characters.
      getDiffRestApiStub.returns(
        Promise.resolve({
          ...createDiff(),
          content: [
            {
              a: [new Array(501).join('*')],
            },
          ],
        })
      );
      element.reload();
      await element.waitForReloadToRender();
      assert.isFalse(element.syntaxLayer.enabled);
    });

    test('starts syntax layer processing on render event', async () => {
      const stub = sinon
        .stub(element.syntaxLayer, 'process')
        .returns(Promise.resolve());
      getDiffRestApiStub.returns(Promise.resolve(createDiff()));
      await element.reload();
      element.dispatchEvent(
        new CustomEvent('render', {bubbles: true, composed: true})
      );
      assert.isTrue(stub.called);
    });
  });

  suite('syntax layer with syntax_highlighting off', () => {
    setup(async () => {
      const prefs = {
        ...createDefaultDiffPrefs(),
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
        syntax_highlighting: false,
      };
      element.patchRange = createPatchRange();
      element.change = createChange();
      element.prefs = prefs;
    });

    test('gr-diff-host provides syntax highlighting layer', async () => {
      await element.waitForReloadToRender();
      assertIsDefined(element.diffElement);
      assertIsDefined(element.diffElement.layers);
      assert.equal(element.diffElement.layers[1], element.syntaxLayer);
    });

    test('syntax layer should be disabled', async () => {
      await element.waitForReloadToRender();
      assert.isFalse(element.syntaxLayer.enabled);
    });

    test('still disabled for large diff', async () => {
      getDiffRestApiStub.callsFake(() =>
        Promise.resolve({
          ...createDiff(),
          content: [
            {
              a: [new Array(501).join('*')],
            },
          ],
        })
      );
      await element.waitForReloadToRender();
      assert.isFalse(element.syntaxLayer.enabled);
    });
  });

  suite('coverage layer', () => {
    let notifyStub: SinonStub;
    let coverageProviderStub: SinonStub;
    let getCoverageAnnotationApisStub: SinonStub;
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
      coverageProviderStub = sinon
        .stub()
        .returns(Promise.resolve(exampleRanges));
      element = await fixture(html`<gr-diff-host></gr-diff-host>`);
      element.changeNum = 123 as NumericChangeId;
      element.change = createChange();
      element.path = 'some/path';
      const prefs = {
        ...createDefaultDiffPrefs(),
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
      };
      element.patchRange = createPatchRange();
      element.prefs = prefs;
      await element.updateComplete;

      getDiffRestApiStub.returns(
        Promise.resolve({
          ...createDiff(),
          content: [{a: ['foo']}],
        })
      );
      testResolver(pluginLoaderToken).pluginsModel.coverageRegister({
        pluginName: 'test-coverage-plugin',
        provider: coverageProviderStub,
      });
      await element.reload();
    });

    test('provider is called with appropriate params', async () => {
      element.patchRange = createPatchRange(1, 3);
      await element.updateComplete;
      await element.reload();
      await element.waitForReloadToRender();
      assert.isTrue(
        coverageProviderStub.calledWithExactly(
          123,
          'some/path',
          1,
          3,
          element.change
        )
      );
    });

    test('provider is called with appropriate params - special patchset values', async () => {
      element.patchRange = createPatchRange();
      await element.waitForReloadToRender();
      assert.isTrue(
        coverageProviderStub.calledWithExactly(
          123,
          'some/path',
          undefined,
          1,
          element.change
        )
      );
    });
  });

  suite('trailing newlines', () => {
    setup(() => {});

    suite('lastChunkForSide', () => {
      test('deltas', () => {
        const diff: DiffInfo = {
          ...createDiff(),
          content: [
            {a: ['foo', 'bar'], b: ['baz']},
            {ab: ['foo', 'bar', 'baz']},
            {b: ['foo']},
          ],
        };
        assert.equal(element.lastChunkForSide(diff, false), diff.content[2]);
        assert.equal(element.lastChunkForSide(diff, true), diff.content[1]);

        diff.content.push({a: ['foo'], b: ['bar']});
        assert.equal(element.lastChunkForSide(diff, false), diff.content[3]);
        assert.equal(element.lastChunkForSide(diff, true), diff.content[3]);
      });

      test('addition with a undefined', () => {
        const diff: DiffInfo = {
          ...createDiff(),
          content: [{b: ['foo', 'bar', 'baz']}],
        };
        assert.equal(element.lastChunkForSide(diff, false), diff.content[0]);
        assert.isNull(element.lastChunkForSide(diff, true));
      });

      test('addition with a empty', () => {
        const diff: DiffInfo = {
          ...createDiff(),
          content: [{a: [], b: ['foo', 'bar', 'baz']}],
        };
        assert.equal(element.lastChunkForSide(diff, false), diff.content[0]);
        assert.isNull(element.lastChunkForSide(diff, true));
      });

      test('deletion with b undefined', () => {
        const diff: DiffInfo = {
          ...createDiff(),
          content: [{a: ['foo', 'bar', 'baz']}],
        };
        assert.isNull(element.lastChunkForSide(diff, false));
        assert.equal(element.lastChunkForSide(diff, true), diff.content[0]);
      });

      test('deletion with b empty', () => {
        const diff: DiffInfo = {
          ...createDiff(),
          content: [{a: ['foo', 'bar', 'baz'], b: []}],
        };
        assert.isNull(element.lastChunkForSide(diff, false));
        assert.equal(element.lastChunkForSide(diff, true), diff.content[0]);
      });

      test('empty', () => {
        const diff: DiffInfo = {...createDiff(), content: []};
        assert.isNull(element.lastChunkForSide(diff, false));
        assert.isNull(element.lastChunkForSide(diff, true));
      });
    });

    suite('hasTrailingNewlines', () => {
      test('shared no trailing', () => {
        const diff = undefined;
        sinon.stub(element, 'lastChunkForSide').returns({ab: ['foo', 'bar']});
        assert.isFalse(element.hasTrailingNewlines(diff, false));
        assert.isFalse(element.hasTrailingNewlines(diff, true));
      });

      test('delta trailing in right', () => {
        const diff = undefined;
        sinon
          .stub(element, 'lastChunkForSide')
          .returns({a: ['foo', 'bar'], b: ['baz', '']});
        assert.isTrue(element.hasTrailingNewlines(diff, false));
        assert.isFalse(element.hasTrailingNewlines(diff, true));
      });

      test('addition', () => {
        const diff: DiffInfo | undefined = undefined;
        sinon
          .stub(element, 'lastChunkForSide')
          .callsFake((_: DiffInfo | undefined, leftSide: boolean) => {
            if (leftSide) {
              return null;
            }
            return {b: ['foo', '']};
          });
        assert.isTrue(element.hasTrailingNewlines(diff, false));
        assert.isNull(element.hasTrailingNewlines(diff, true));
      });

      test('deletion', () => {
        const diff: DiffInfo | undefined = undefined;
        sinon
          .stub(element, 'lastChunkForSide')
          .callsFake((_: DiffInfo | undefined, leftSide: boolean) => {
            if (!leftSide) {
              return null;
            }
            return {a: ['foo']};
          });
        assert.isNull(element.hasTrailingNewlines(diff, false));
        assert.isFalse(element.hasTrailingNewlines(diff, true));
      });
    });
  });
});
