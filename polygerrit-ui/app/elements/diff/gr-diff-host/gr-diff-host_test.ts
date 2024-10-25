/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
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
  CommentThread,
  DraftInfo,
  EDIT,
  ImageInfo,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RevisionPatchSetNum,
} from '../../../types/common';
import {CoverageType} from '../../../types/types';
import {GrDiffHost} from './gr-diff-host';
import {DiffInfo, DiffViewMode, IgnoreWhitespaceType} from '../../../api/diff';
import {ErrorCallback} from '../../../api/rest';
import {SinonStub, SinonStubbedMember} from 'sinon';
import {RunResult} from '../../../models/checks/checks-model';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken, UserModel} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';

suite('gr-diff-host tests', () => {
  let element: GrDiffHost;
  let account = createAccountDetailWithId(1);
  let getDiffRestApiStub: SinonStubbedMember<RestApiService['getDiff']>;
  let userModel: UserModel;

  setup(async () => {
    stubRestApi('getAccount').callsFake(() => Promise.resolve(account));
    element = await fixture(html`<gr-diff-host
      .changeNum=${123 as NumericChangeId}
      .path=${'some/path'}
      .file=${{path: 'some/path'}}
      .change=${createChange()}
      .patchRange=${createPatchRange()}
    ></gr-diff-host>`);
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
      `,
      {ignoreAttributes: ['style']}
    );
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

      // Left image rendered with the parent commit's version of the file.
      assertIsDefined(element.diffElement);
      const leftImage = queryAndAssert(element.diffElement, 'td.left img');
      const leftLabel = queryAndAssert(element.diffElement, 'td.left label');
      const leftLabelContent = leftLabel.querySelector('.label');
      const leftLabelName = leftLabel.querySelector('.name');

      const rightImage = queryAndAssert(element.diffElement, 'td.right img');
      const rightLabel = queryAndAssert(element.diffElement, 'td.right label');
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

      assertIsDefined(element.diffElement);

      // Left image rendered with the parent commit's version of the file.
      const leftImage = queryAndAssert(element.diffElement, 'td.left img');
      const leftLabel = queryAndAssert(element.diffElement, 'td.left label');
      const leftLabelContent = leftLabel.querySelector('.label');
      const leftLabelName = leftLabel.querySelector('.name');

      const rightImage = queryAndAssert(element.diffElement, 'td.right img');
      const rightLabel = queryAndAssert(element.diffElement, 'td.right label');
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
        assertIsDefined(element.diffElement);
        const leftImage = query(element.diffElement, 'td.left img');
        const rightImage = queryAndAssert(element.diffElement, 'td.right img');

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
        assertIsDefined(element.diffElement);

        const leftImage = queryAndAssert(element.diffElement, 'td.left img');
        const rightImage = query(element.diffElement, 'td.right img');

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
        assertIsDefined(element.diffElement);
        const leftImage = query(element.diffElement, 'td.left img');
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

  test('clearBlame', async () => {
    element.blame = [];
    await element.updateComplete;
    assertIsDefined(element.diffElement);
    const isBlameLoadedStub = sinon.stub();
    element.addEventListener('is-blame-loaded-changed', isBlameLoadedStub);
    element.clearBlame();
    await element.updateComplete;
    assert.isNull(element.blame);
    assert.isTrue(isBlameLoadedStub.calledOnce);
    assert.isFalse(isBlameLoadedStub.args[0][0].detail.value);
  });

  test('loadBlame', async () => {
    const mockBlame: BlameInfo[] = [createBlame()];
    const showAlertStub = sinon.stub();
    element.addEventListener('show-alert', showAlertStub);
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
    element.addEventListener('show-alert', showAlertStub);
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
    let reportStub: SinonStubbedMember<ReportingService['reportInteraction']>;

    setup(async () => {
      element.patchRange = createPatchRange(1, 2);
      await element.updateComplete;
      reportStub = sinon.stub(element.reporting, 'reportInteraction');
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

  suite('render thread elements', () => {
    test('right start_line:1', async () => {
      const thread: CommentThread = {
        ...createCommentThread([createComment()]),
      };
      element.threads = [thread];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-comment-thread
            class="comment-thread"
            diff-side="right"
            line-num="1"
            slot="right-1"
          >
          </gr-comment-thread>
        `
      );
    });
    test('left start_line:2', async () => {
      const thread: CommentThread = {
        ...createCommentThread([
          createComment({side: CommentSide.PARENT, line: 2}),
        ]),
      };
      element.threads = [thread];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-comment-thread
            class="comment-thread"
            diff-side="left"
            line-num="2"
            slot="left-2"
          >
          </gr-comment-thread>
        `
      );
    });
  });

  suite('render check elements', () => {
    test('start_line:12', async () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [{path: 'a', range: {start_line: 12} as CommentRange}],
      };
      element.checks = [result];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-diff-check-result
            class="comment-thread"
            diff-side="right"
            line-num="12"
            slot="right-12"
          >
          </gr-diff-check-result>
        `
      );
    });

    test('start_line:13 end_line:14 without char positions', async () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [
          {path: 'a', range: {start_line: 13, end_line: 14} as CommentRange},
        ],
      };
      element.checks = [result];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-diff-check-result
            class="comment-thread"
            diff-side="right"
            line-num="14"
            slot="right-14"
          >
          </gr-diff-check-result>
        `
      );
    });

    test('start_line:13 end_line:14 with char positions', async () => {
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
      element.checks = [result];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-diff-check-result
            class="comment-thread"
            diff-side="right"
            line-num="14"
            slot="right-14"
            range='{"start_line":13,"end_line":14,"start_character":5,"end_character":7}'
          >
          </gr-diff-check-result>
        `
      );
    });

    test('empty range', async () => {
      const result: RunResult = {
        ...createRunResult(),
        codePointers: [{path: 'a', range: {} as CommentRange}],
      };
      element.checks = [result];
      await element.updateComplete;
      assert.lightDom.equal(
        element.diffElement,
        /* HTML */ `
          <gr-diff-check-result
            class="comment-thread"
            diff-side="right"
            line-num="FILE"
            slot="right-FILE"
          >
          </gr-diff-check-result>
        `
      );
    });
  });

  suite('create-comment', () => {
    let addDraftSpy: sinon.SinonSpy;

    setup(async () => {
      const commentsModel: CommentsModel = testResolver(commentsModelToken);
      addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

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

      assert.equal(addDraftSpy.callCount, 1);
      const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft1.side, CommentSide.PARENT);
      assert.equal(draft1.range, undefined);
      assert.equal(draft1.patch_set, 1 as RevisionPatchSetNum);

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

      assert.equal(addDraftSpy.callCount, 2);
      const draft2: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft2.side, CommentSide.PARENT);
      assert.equal(draft2.range, range);
      assert.equal(draft2.patch_set, 1 as RevisionPatchSetNum);
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

      assert.equal(addDraftSpy.callCount, 1);
      const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft1.side, CommentSide.REVISION);
      assert.equal(draft1.patch_set, 3 as RevisionPatchSetNum);
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

      assert.equal(addDraftSpy.callCount, 1);
      const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft1.side, CommentSide.PARENT);
      assert.equal(draft1.patch_set, 1 as RevisionPatchSetNum);
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

      assert.equal(addDraftSpy.callCount, 1);
      const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft1.side, CommentSide.PARENT);
      assert.equal(draft1.patch_set, 3 as RevisionPatchSetNum);
      assert.equal(draft1.parent, 2);
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

      assert.equal(addDraftSpy.callCount, 1);
      const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
      assert.equal(draft1.side, CommentSide.REVISION);
      assert.equal(draft1.patch_set, 2 as RevisionPatchSetNum);
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

        assert.equal(addDraftSpy.callCount, 1);
        const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
        assert.equal(draft1.side, CommentSide.REVISION);
        assert.equal(draft1.patch_set, 2 as RevisionPatchSetNum);
        assert.equal(draft1.path, element.file.basePath);
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

        assert.equal(addDraftSpy.callCount, 1);
        const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
        assert.equal(draft1.side, CommentSide.REVISION);
        assert.equal(draft1.patch_set, 3 as RevisionPatchSetNum);
        assert.equal(draft1.path, element.file.path);
      }
    );

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

        assert.equal(addDraftSpy.callCount, 1);
        const draft1: DraftInfo = addDraftSpy.lastCall.firstArg;
        assert.equal(draft1.side, CommentSide.PARENT);
        assert.equal(draft1.patch_set, 1 as RevisionPatchSetNum);
        assert.equal(draft1.path, element.file.path);
      }
    );

    test('cannot create thread on an edit', () => {
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);

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

      assert.isFalse(addDraftSpy.called);
      assert.isTrue(alertSpy.called);
    });

    test('cannot create thread on an edit base', () => {
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);

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

      assert.isFalse(addDraftSpy.called);
      assert.isTrue(alertSpy.called);
    });
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
              a: ['*'.repeat(501)],
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
              a: ['*'.repeat(501)],
            },
          ],
        })
      );
      await element.waitForReloadToRender();
      assert.isFalse(element.syntaxLayer.enabled);
    });
  });

  suite('coverage layer', () => {
    let coverageProviderStub: SinonStub;
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
      element.prefs = {
        ...createDefaultDiffPrefs(),
        line_length: 10,
        show_tabs: true,
        tab_size: 4,
        context: -1,
      };
      await element.updateComplete;

      coverageProviderStub = sinon
        .stub()
        .returns(Promise.resolve(exampleRanges));
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
