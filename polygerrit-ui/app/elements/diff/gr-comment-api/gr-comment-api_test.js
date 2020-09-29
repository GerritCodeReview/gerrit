/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-comment-api.js';

const basicFixture = fixtureFromElement('gr-comment-api');

suite('gr-comment-api tests', () => {
  const PARENT = 'PARENT';

  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('loads logged-out', () => {
    const changeNum = 1234;

    sinon.stub(element.$.restAPI, 'getLoggedIn')
        .returns(Promise.resolve(false));
    sinon.stub(element.$.restAPI, 'getDiffComments')
        .returns(Promise.resolve({
          'foo.c': [{id: '123', message: 'foo bar', in_reply_to: '321'}],
        }));
    sinon.stub(element.$.restAPI, 'getDiffRobotComments')
        .returns(Promise.resolve({'foo.c': [{id: '321', message: 'done'}]}));
    sinon.stub(element.$.restAPI, 'getDiffDrafts')
        .returns(Promise.resolve({}));

    return element.loadAll(changeNum).then(() => {
      assert.isTrue(element.$.restAPI.getDiffComments.calledWithExactly(
          changeNum));
      assert.isTrue(element.$.restAPI.getDiffRobotComments.calledWithExactly(
          changeNum));
      assert.isTrue(element.$.restAPI.getDiffDrafts.calledWithExactly(
          changeNum));
      assert.isOk(element._changeComments._comments);
      assert.isOk(element._changeComments._robotComments);
      assert.deepEqual(element._changeComments._drafts, {});
    });
  });

  test('loads logged-in', () => {
    const changeNum = 1234;

    sinon.stub(element.$.restAPI, 'getLoggedIn')
        .returns(Promise.resolve(true));
    sinon.stub(element.$.restAPI, 'getDiffComments')
        .returns(Promise.resolve({
          'foo.c': [{id: '123', message: 'foo bar', in_reply_to: '321'}],
        }));
    sinon.stub(element.$.restAPI, 'getDiffRobotComments')
        .returns(Promise.resolve({'foo.c': [{id: '321', message: 'done'}]}));
    sinon.stub(element.$.restAPI, 'getDiffDrafts')
        .returns(Promise.resolve({'foo.c': [{id: '555', message: 'ack'}]}));

    return element.loadAll(changeNum).then(() => {
      assert.isTrue(element.$.restAPI.getDiffComments.calledWithExactly(
          changeNum));
      assert.isTrue(element.$.restAPI.getDiffRobotComments.calledWithExactly(
          changeNum));
      assert.isTrue(element.$.restAPI.getDiffDrafts.calledWithExactly(
          changeNum));
      assert.isOk(element._changeComments._comments);
      assert.isOk(element._changeComments._robotComments);
      assert.notDeepEqual(element._changeComments._drafts, {});
    });
  });

  suite('reloadDrafts', () => {
    let commentStub;
    let robotCommentStub;
    let draftStub;
    setup(() => {
      commentStub = sinon.stub(element.$.restAPI, 'getDiffComments')
          .returns(Promise.resolve({}));
      robotCommentStub = sinon.stub(element.$.restAPI,
          'getDiffRobotComments').returns(Promise.resolve({}));
      draftStub = sinon.stub(element.$.restAPI, 'getDiffDrafts')
          .returns(Promise.resolve({}));
    });

    test('without loadAll first', done => {
      assert.isNotOk(element._changeComments);
      sinon.spy(element, 'loadAll');
      element.reloadDrafts().then(() => {
        assert.isTrue(element.loadAll.called);
        assert.isOk(element._changeComments);
        assert.equal(commentStub.callCount, 1);
        assert.equal(robotCommentStub.callCount, 1);
        assert.equal(draftStub.callCount, 1);
        done();
      });
    });

    test('with loadAll first', done => {
      assert.isNotOk(element._changeComments);
      element.loadAll()
          .then(() => {
            assert.isOk(element._changeComments);
            assert.equal(commentStub.callCount, 1);
            assert.equal(robotCommentStub.callCount, 1);
            assert.equal(draftStub.callCount, 1);
            return element.reloadDrafts();
          })
          .then(() => {
            assert.isOk(element._changeComments);
            assert.equal(commentStub.callCount, 1);
            assert.equal(robotCommentStub.callCount, 1);
            assert.equal(draftStub.callCount, 2);
            done();
          });
    });
  });

  suite('_changeComment methods', () => {
    setup(done => {
      const changeNum = 1234;
      stub('gr-rest-api-interface', {
        getDiffComments() { return Promise.resolve({}); },
        getDiffRobotComments() { return Promise.resolve({}); },
        getDiffDrafts() { return Promise.resolve({}); },
      });
      element.loadAll(changeNum).then(() => {
        done();
      });
    });

    test('_isInBaseOfPatchRange', () => {
      const comment = {patch_set: 1};
      const patchRange = {basePatchNum: 1, patchNum: 2};
      assert.isTrue(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));

      patchRange.basePatchNum = PARENT;
      assert.isFalse(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));

      comment.side = PARENT;
      assert.isFalse(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));

      comment.patch_set = 2;
      assert.isTrue(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));

      patchRange.basePatchNum = -2;
      comment.side = PARENT;
      comment.parent = 1;
      assert.isFalse(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));

      comment.parent = 2;
      assert.isTrue(element._changeComments._isInBaseOfPatchRange(comment,
          patchRange));
    });

    test('_isInRevisionOfPatchRange', () => {
      const comment = {patch_set: 123};
      const patchRange = {basePatchNum: 122, patchNum: 124};
      assert.isFalse(element._changeComments._isInRevisionOfPatchRange(
          comment, patchRange));

      patchRange.patchNum = 123;
      assert.isTrue(element._changeComments._isInRevisionOfPatchRange(
          comment, patchRange));

      comment.side = PARENT;
      assert.isFalse(element._changeComments._isInRevisionOfPatchRange(
          comment, patchRange));
    });

    test('_isInPatchRange', () => {
      const patchRange1 = {basePatchNum: 122, patchNum: 124};
      const patchRange2 = {basePatchNum: 123, patchNum: 125};
      const patchRange3 = {basePatchNum: 124, patchNum: 125};

      const isInBasePatchStub = sinon.stub(element._changeComments,
          '_isInBaseOfPatchRange');
      const isInRevisionPatchStub = sinon.stub(element._changeComments,
          '_isInRevisionOfPatchRange');

      isInBasePatchStub.withArgs({}, patchRange1).returns(true);
      isInBasePatchStub.withArgs({}, patchRange2).returns(false);
      isInBasePatchStub.withArgs({}, patchRange3).returns(false);

      isInRevisionPatchStub.withArgs({}, patchRange1).returns(false);
      isInRevisionPatchStub.withArgs({}, patchRange2).returns(true);
      isInRevisionPatchStub.withArgs({}, patchRange3).returns(false);

      assert.isTrue(element._changeComments._isInPatchRange({}, patchRange1));
      assert.isTrue(element._changeComments._isInPatchRange({}, patchRange2));
      assert.isFalse(element._changeComments._isInPatchRange({},
          patchRange3));
    });

    suite('comment ranges and paths', () => {
      function makeTime(mins) {
        return `2013-02-26 15:0${mins}:43.986000000`;
      }

      setup(() => {
        element._changeComments._drafts = {
          'file/one': [
            {
              id: '11',
              patch_set: 2,
              side: PARENT,
              line: 1,
              updated: makeTime(3),
            },
            {
              id: '12',
              in_reply_to: '04',
              patch_set: 2,
              line: 1,
              // Draft gets lower timestamp than published comment, because we
              // want to test that the draft still gets sorted to the end.
              updated: makeTime(2),
            },
          ],
          'file/two': [
            {
              id: '05',
              patch_set: 3,
              line: 1,
              updated: makeTime(3),
            },
          ],
        };
        element._changeComments._robotComments = {
          'file/one': [
            {
              id: '01',
              patch_set: 2,
              side: PARENT,
              line: 1,
              updated: makeTime(1),
              range: {
                start_line: 1,
                start_character: 2,
                end_line: 2,
                end_character: 2,
              },
            }, {
              id: '02',
              in_reply_to: '04',
              patch_set: 2,
              unresolved: true,
              line: 1,
              updated: makeTime(3),
            },
          ],
        };
        element._changeComments._comments = {
          'file/one': [
            {
              id: '03',
              patch_set: 2,
              side: PARENT,
              line: 2,
              updated: makeTime(1),
            },
            {id: '04', patch_set: 2, line: 1, updated: makeTime(1)},
          ],
          'file/two': [
            {id: '05', patch_set: 2, line: 2, updated: makeTime(1)},
            {id: '06', patch_set: 3, line: 2, updated: makeTime(1)},
          ],
          'file/three': [
            {
              id: '07',
              patch_set: 2,
              side: PARENT,
              unresolved: true,
              line: 1,
              updated: makeTime(1),
            },
            {id: '08', patch_set: 3, line: 1, updated: makeTime(1)},
          ],
          'file/four': [
            {
              id: '09',
              patch_set: 5,
              side: PARENT,
              line: 1,
              updated: makeTime(1),
            },
            {id: '10', patch_set: 5, line: 1, updated: makeTime(1)},
          ],
        };
      });

      test('getPaths', () => {
        const patchRange = {basePatchNum: 1, patchNum: 4};
        let paths = element._changeComments.getPaths(patchRange);
        assert.equal(Object.keys(paths).length, 0);

        patchRange.basePatchNum = PARENT;
        patchRange.patchNum = 3;
        paths = element._changeComments.getPaths(patchRange);
        assert.notProperty(paths, 'file/one');
        assert.property(paths, 'file/two');
        assert.property(paths, 'file/three');
        assert.notProperty(paths, 'file/four');

        patchRange.patchNum = 2;
        paths = element._changeComments.getPaths(patchRange);
        assert.property(paths, 'file/one');
        assert.property(paths, 'file/two');
        assert.property(paths, 'file/three');
        assert.notProperty(paths, 'file/four');

        paths = element._changeComments.getPaths();
        assert.property(paths, 'file/one');
        assert.property(paths, 'file/two');
        assert.property(paths, 'file/three');
        assert.property(paths, 'file/four');
      });

      test('getCommentsBySideForPath', () => {
        const patchRange = {basePatchNum: 1, patchNum: 3};
        let path = 'file/one';
        let comments = element._changeComments.getCommentsBySideForPath(path,
            patchRange);
        assert.equal(comments.meta.changeNum, 1234);
        assert.equal(comments.left.length, 0);
        assert.equal(comments.right.length, 0);

        path = 'file/two';
        comments = element._changeComments.getCommentsBySideForPath(path,
            patchRange);
        assert.equal(comments.left.length, 0);
        assert.equal(comments.right.length, 2);

        patchRange.basePatchNum = 2;
        comments = element._changeComments.getCommentsBySideForPath(path,
            patchRange);
        assert.equal(comments.left.length, 1);
        assert.equal(comments.right.length, 2);

        patchRange.basePatchNum = PARENT;
        path = 'file/three';
        comments = element._changeComments.getCommentsBySideForPath(path,
            patchRange);
        assert.equal(comments.left.length, 0);
        assert.equal(comments.right.length, 1);
      });

      test('getAllCommentsForPath', () => {
        let path = 'file/one';
        let comments = element._changeComments.getAllCommentsForPath(path);
        assert.equal(comments.length, 4);
        path = 'file/two';
        comments = element._changeComments.getAllCommentsForPath(path, 2);
        assert.equal(comments.length, 1);
        const aCopyOfComments = element._changeComments
            .getAllCommentsForPath(path, 2);
        assert.deepEqual(comments, aCopyOfComments);
        assert.notEqual(comments[0], aCopyOfComments[0]);
      });

      test('getAllDraftsForPath', () => {
        const path = 'file/one';
        const drafts = element._changeComments.getAllDraftsForPath(path);
        assert.equal(drafts.length, 2);
        const aCopyOfDrafts = element._changeComments
            .getAllDraftsForPath(path);
        assert.deepEqual(drafts, aCopyOfDrafts);
        assert.notEqual(drafts[0], aCopyOfDrafts[0]);
      });

      test('computeUnresolvedNum', () => {
        assert.equal(element._changeComments
            .computeUnresolvedNum({
              patchNum: 2,
              path: 'file/one',
            }), 0);
        assert.equal(element._changeComments
            .computeUnresolvedNum({
              patchNum: 1,
              path: 'file/one',
            }), 0);
        assert.equal(element._changeComments
            .computeUnresolvedNum({
              patchNum: 2,
              path: 'file/three',
            }), 1);
      });

      test('computeUnresolvedNum w/ non-linear thread', () => {
        element._changeComments._drafts = {};
        element._changeComments._robotComments = {};
        element._changeComments._comments = {
          path: [{
            id: '9c6ba3c6_28b7d467',
            patch_set: 1,
            updated: '2018-02-28 14:41:13.000000000',
            unresolved: true,
          }, {
            id: '3df7b331_0bead405',
            patch_set: 1,
            in_reply_to: '1c346623_ab85d14a',
            updated: '2018-02-28 23:07:55.000000000',
            unresolved: false,
          }, {
            id: '6153dce6_69958d1e',
            patch_set: 1,
            in_reply_to: '9c6ba3c6_28b7d467',
            updated: '2018-02-28 17:11:31.000000000',
            unresolved: true,
          }, {
            id: '1c346623_ab85d14a',
            patch_set: 1,
            in_reply_to: '9c6ba3c6_28b7d467',
            updated: '2018-02-28 23:01:39.000000000',
            unresolved: false,
          }],
        };
        assert.equal(
            element._changeComments.computeUnresolvedNum(1, 'path'), 0);
      });

      test('computeCommentCount', () => {
        assert.equal(element._changeComments
            .computeCommentCount({
              patchNum: 2,
              path: 'file/one',
            }), 4);
        assert.equal(element._changeComments
            .computeCommentCount({
              patchNum: 1,
              path: 'file/one',
            }), 0);
        assert.equal(element._changeComments
            .computeCommentCount({
              patchNum: 2,
              path: 'file/three',
            }), 1);
      });

      test('computeDraftCount', () => {
        assert.equal(element._changeComments
            .computeDraftCount({
              patchNum: 2,
              path: 'file/one',
            }), 2);
        assert.equal(element._changeComments
            .computeDraftCount({
              patchNum: 1,
              path: 'file/one',
            }), 0);
        assert.equal(element._changeComments
            .computeDraftCount({
              patchNum: 2,
              path: 'file/three',
            }), 0);
        assert.equal(element._changeComments
            .computeDraftCount(), 3);
      });

      test('getAllPublishedComments', () => {
        let publishedComments = element._changeComments
            .getAllPublishedComments();
        assert.equal(Object.keys(publishedComments).length, 4);
        assert.equal(Object.keys(publishedComments[['file/one']]).length, 4);
        assert.equal(Object.keys(publishedComments[['file/two']]).length, 2);
        publishedComments = element._changeComments
            .getAllPublishedComments(2);
        assert.equal(Object.keys(publishedComments[['file/one']]).length, 4);
        assert.equal(Object.keys(publishedComments[['file/two']]).length, 1);
      });

      test('getAllComments', () => {
        let comments = element._changeComments.getAllComments();
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments[['file/one']]).length, 4);
        assert.equal(Object.keys(comments[['file/two']]).length, 2);
        comments = element._changeComments.getAllComments(false, 2);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments[['file/one']]).length, 4);
        assert.equal(Object.keys(comments[['file/two']]).length, 1);
        // Include drafts
        comments = element._changeComments.getAllComments(true);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments[['file/one']]).length, 6);
        assert.equal(Object.keys(comments[['file/two']]).length, 3);
        comments = element._changeComments.getAllComments(true, 2);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments[['file/one']]).length, 6);
        assert.equal(Object.keys(comments[['file/two']]).length, 1);
      });

      test('computeAllThreads', () => {
        const expectedThreads = [
          {
            comments: [
              {
                id: '01',
                patch_set: 2,
                side: 'PARENT',
                line: 1,
                updated: '2013-02-26 15:01:43.986000000',
                range: {
                  start_line: 1,
                  start_character: 2,
                  end_line: 2,
                  end_character: 2,
                },
                __path: 'file/one',
              },
            ],
            commentSide: 'PARENT',
            patchNum: 2,
            path: 'file/one',
            line: 1,
            rootId: '01',
          }, {
            comments: [
              {
                id: '03',
                patch_set: 2,
                side: 'PARENT',
                line: 2,
                __path: 'file/one',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            commentSide: 'PARENT',
            patchNum: 2,
            path: 'file/one',
            line: 2,
            rootId: '03',
          }, {
            comments: [
              {
                id: '04',
                patch_set: 2,
                line: 1,
                __path: 'file/one',
                updated: '2013-02-26 15:01:43.986000000',
              },
              {
                id: '02',
                in_reply_to: '04',
                patch_set: 2,
                unresolved: true,
                line: 1,
                __path: 'file/one',
                updated: '2013-02-26 15:03:43.986000000',
              },
              {
                id: '12',
                in_reply_to: '04',
                patch_set: 2,
                line: 1,
                __path: 'file/one',
                __draft: true,
                updated: '2013-02-26 15:02:43.986000000',
              },
            ],
            patchNum: 2,
            path: 'file/one',
            line: 1,
            rootId: '04',
          }, {
            comments: [
              {
                id: '05',
                patch_set: 2,
                line: 2,
                __path: 'file/two',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            patchNum: 2,
            path: 'file/two',
            line: 2,
            rootId: '05',
          }, {
            comments: [
              {
                id: '06',
                patch_set: 3,
                line: 2,
                __path: 'file/two',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            patchNum: 3,
            path: 'file/two',
            line: 2,
            rootId: '06',
          }, {
            comments: [
              {
                id: '07',
                patch_set: 2,
                side: 'PARENT',
                unresolved: true,
                line: 1,
                __path: 'file/three',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            commentSide: 'PARENT',
            patchNum: 2,
            path: 'file/three',
            line: 1,
            rootId: '07',
          }, {
            comments: [
              {
                id: '08',
                patch_set: 3,
                line: 1,
                __path: 'file/three',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            patchNum: 3,
            path: 'file/three',
            line: 1,
            rootId: '08',
          }, {
            comments: [
              {
                id: '09',
                patch_set: 5,
                side: 'PARENT',
                line: 1,
                __path: 'file/four',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            commentSide: 'PARENT',
            patchNum: 5,
            path: 'file/four',
            line: 1,
            rootId: '09',
          }, {
            comments: [
              {
                id: '10',
                patch_set: 5,
                line: 1,
                __path: 'file/four',
                updated: '2013-02-26 15:01:43.986000000',
              },
            ],
            rootId: '10',
            patchNum: 5,
            path: 'file/four',
            line: 1,
          }, {
            comments: [
              {
                id: '05',
                patch_set: 3,
                line: 1,
                __path: 'file/two',
                __draft: true,
                updated: '2013-02-26 15:03:43.986000000',
              },
            ],
            rootId: '05',
            patchNum: 3,
            path: 'file/two',
            line: 1,
          }, {
            comments: [
              {
                id: '11',
                patch_set: 2,
                side: 'PARENT',
                line: 1,
                __path: 'file/one',
                __draft: true,
                updated: '2013-02-26 15:03:43.986000000',
              },
            ],
            rootId: '11',
            commentSide: 'PARENT',
            patchNum: 2,
            path: 'file/one',
            line: 1,
          },
        ];
        const threads = element._changeComments.getAllThreadsForChange();
        assert.deepEqual(threads, expectedThreads);
      });

      test('getCommentsForThreadGroup', () => {
        let expectedComments = [
          {
            __path: 'file/one',
            id: '04',
            patch_set: 2,
            line: 1,
            updated: '2013-02-26 15:01:43.986000000',
          },
          {
            __path: 'file/one',
            id: '02',
            in_reply_to: '04',
            patch_set: 2,
            unresolved: true,
            line: 1,
            updated: '2013-02-26 15:03:43.986000000',
          },
          {
            __path: 'file/one',
            __draft: true,
            id: '12',
            in_reply_to: '04',
            patch_set: 2,
            line: 1,
            updated: '2013-02-26 15:02:43.986000000',
          },
        ];
        assert.deepEqual(element._changeComments.getCommentsForThread('04'),
            expectedComments);

        expectedComments = [{
          id: '11',
          patch_set: 2,
          side: 'PARENT',
          line: 1,
          __path: 'file/one',
          __draft: true,
          updated: '2013-02-26 15:03:43.986000000',
        }];

        assert.deepEqual(element._changeComments.getCommentsForThread('11'),
            expectedComments);

        assert.deepEqual(element._changeComments.getCommentsForThread('1000'),
            null);
      });
    });
  });
});

