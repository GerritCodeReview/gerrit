/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import './gr-comment-api.js';
import {ChangeComments} from './gr-comment-api.js';
import {isInRevisionOfPatchRange, isInBaseOfPatchRange, isDraftThread, isUnresolved, createCommentThreads} from '../../../utils/comment-util.js';
import {createDraft, createComment, createChangeComments, createCommentThread} from '../../../test/test-data-generators.js';
import {CommentSide} from '../../../constants/constants.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-comment-api');

suite('gr-comment-api tests', () => {
  const PARENT = 'PARENT';

  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('_changeComment methods', () => {
    setup(() => {
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
    });

    suite('ported comments', () => {
      let portedComments;
      let changeComments;
      const comment1 = {
        ...createComment(),
        unresolved: true,
        id: '1',
        line: 136,
        patch_set: 2,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 1,
          end_character: 1,
        },
      };

      const comment2 = {
        ...createComment(),
        patch_set: 2,
        id: '2',
        line: 5,
      };

      const comment3 = {
        ...createComment(),
        side: CommentSide.PARENT,
        line: 10,
        unresolved: true,
      };

      const comment4 = {
        ...comment3,
        parent: -2,
      };

      const draft1 = {
        ...createDraft(),
        id: 'db977012_e1f13828',
        line: 4,
        patch_set: 2,
      };
      const draft2 = {
        ...createDraft(),
        id: '503008e2_0ab203ee',
        line: 11,
        unresolved: true,
        // slightly larger timestamp so it's sorted higher
        updated: '2018-02-13 22:49:48.018000001',
        patch_set: 2,
      };

      setup(() => {
        portedComments = {
          'karma.conf.js': [{
            ...comment1,
            patch_set: 4,
            range: {
              start_line: 136,
              start_character: 16,
              end_line: 136,
              end_character: 29,
            },
          }],
        };

        changeComments = new ChangeComments(
            {/* comments */
              'karma.conf.js': [
                // resolved comment that will not be ported over
                comment2,
                // original comment that will be ported over to patchset 4
                comment1,
              ],
            },
            {}/* robot comments */,
            {}/* drafts */,
            portedComments,
            {}/* ported drafts */
        );
      });

      test('threads containing ported comment are returned', () => {
        assert.equal(changeComments.getAllThreadsForChange().length,
            2);

        const portedThreads = changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 'PARENT'});

        assert.equal(portedThreads.length, 1);
        // check that the location of the thread matches the ported comment
        assert.equal(portedThreads[0].patchNum, 4);
        assert.deepEqual(portedThreads[0].range, {
          start_line: 136,
          start_character: 16,
          end_line: 136,
          end_character: 29,
        });

        // thread ported over if comparing patchset 1 vs patchset 4
        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 1}
        ).length, 1);

        // verify ported thread is not returned if original thread will be
        // shown
        // original thread attached to right side
        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 2, basePatchNum: 'PARENT'}
        ).length, 0);
        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 2, basePatchNum: 1}
        ).length, 0);

        // original thread attached to left side
        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 3, basePatchNum: 2}
        ).length, 0);
      });

      test('threads without any ported comment are filtered out', () => {
        changeComments = new ChangeComments(
            {/* comments */
              // comment that is not ported over
              'karma.conf.js': [comment2],
            },
            {}/* robot comments */,
            {/* drafts */
              'karma.conf.js': [draft2],
            },
            // comment1 that is ported over but does not have any thread
            // that has a comment that matches it
            portedComments,
            {}/* ported drafts */
        );

        assert.equal(createCommentThreads(changeComments
            .getAllCommentsForPath('karma.conf.js')).length, 1);
        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 'PARENT'}
        ).length, 0);
      });

      test('comments with side=PARENT are ported over', () => {
        changeComments = new ChangeComments(
            {/* comments */
              // comment left on Base
              'karma.conf.js': [comment3],
            },
            {}/* robot comments */,
            {/* drafts */
              'karma.conf.js': [draft2],
            },
            {/* ported comments */
              'karma.conf.js': [{
                ...comment3,
                line: 31,
                patch_set: 4,
              }],
            },
            {}/* ported drafts */
        );

        const portedThreads = changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 'PARENT'});
        assert.equal(portedThreads.length, 1);
        assert.equal(portedThreads[0].line, 31);

        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: -2}
        ).length, 0);

        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 2}
        ).length, 0);
      });

      test('comments left on merge parent is not ported over', () => {
        changeComments = new ChangeComments(
            {/* comments */
              // comment left on Base
              'karma.conf.js': [comment4],
            },
            {}/* robot comments */,
            {/* drafts */
              'karma.conf.js': [draft2],
            },
            {/* ported comments */
              'karma.conf.js': [{
                ...comment4,
                line: 31,
                patch_set: 4,
              }],
            },
            {}/* ported drafts */
        );

        const portedThreads = changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 'PARENT'});
        assert.equal(portedThreads.length, 0);

        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: -2}
        ).length, 0);

        assert.equal(changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 2}
        ).length, 0);
      });

      test('ported comments contribute to comment count', () => {
        assert.equal(changeComments.computeCommentsString(
            {basePatchNum: 'PARENT', patchNum: 2}, 'karma.conf.js',
            {__path: 'karma.conf.js'}), '2 comments (1 unresolved)');

        // comment1 is ported over to patchset 4
        assert.equal(changeComments.computeCommentsString(
            {basePatchNum: 'PARENT', patchNum: 4}, 'karma.conf.js',
            {__path: 'karma.conf.js'}), '1 comment (1 unresolved)');
      });

      test('drafts are ported over', () => {
        changeComments = new ChangeComments(
            {}/* comments */,
            {}/* robotComments */,
            {/* drafts */
              // draft1: resolved draft that will be ported over to ps 4
              // draft2: unresolved draft that will be ported over to ps 4
              'karma.conf.js': [draft1, draft2],
            },
            {}/* ported comments */,
            {/* ported drafts */
              'karma.conf.js': [
                {
                  ...draft1,
                  line: 5,
                  patch_set: 4,
                },
                {
                  ...draft2,
                  line: 31,
                  patch_set: 4,
                },
              ],
            }
        );

        const portedThreads = changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'}, {patchNum: 4, basePatchNum: 'PARENT'});

        // resolved draft is ported over
        assert.equal(portedThreads.length, 2);
        assert.equal(portedThreads[0].line, 5);
        assert.isTrue(isDraftThread(portedThreads[0]));
        assert.isFalse(isUnresolved(portedThreads[0]));

        // unresolved draft is ported over
        assert.equal(portedThreads[1].line, 31);
        assert.isTrue(isDraftThread(portedThreads[1]));
        assert.isTrue(isUnresolved(portedThreads[1]));

        assert.equal(createCommentThreads(
            changeComments.getAllCommentsForPath('karma.conf.js'),
            {patchNum: 4, basePatchNum: 'PARENT'}).length, 0);
      });
    });

    test('_isInBaseOfPatchRange', () => {
      const comment = {patch_set: 1};
      const patchRange = {basePatchNum: 1, patchNum: 2};
      assert.isTrue(isInBaseOfPatchRange(comment,
          patchRange));

      patchRange.basePatchNum = PARENT;
      assert.isFalse(isInBaseOfPatchRange(comment,
          patchRange));

      comment.side = PARENT;
      assert.isFalse(isInBaseOfPatchRange(comment,
          patchRange));

      comment.patch_set = 2;
      assert.isTrue(isInBaseOfPatchRange(comment,
          patchRange));

      patchRange.basePatchNum = -2;
      comment.side = PARENT;
      comment.parent = 1;
      assert.isFalse(isInBaseOfPatchRange(comment,
          patchRange));

      comment.parent = 2;
      assert.isTrue(isInBaseOfPatchRange(comment,
          patchRange));
    });

    test('isInRevisionOfPatchRange', () => {
      const comment = {patch_set: 123};
      const patchRange = {basePatchNum: 122, patchNum: 124};
      assert.isFalse(isInRevisionOfPatchRange(
          comment, patchRange));

      patchRange.patchNum = 123;
      assert.isTrue(isInRevisionOfPatchRange(
          comment, patchRange));

      comment.side = PARENT;
      assert.isFalse(isInRevisionOfPatchRange(
          comment, patchRange));
    });

    suite('comment ranges and paths', () => {
      const commentObjs = {};
      function makeTime(mins) {
        return `2013-02-26 15:0${mins}:43.986000000`;
      }

      setup(() => {
        commentObjs['01'] = {
          ...createComment(),
          id: '01',
          patch_set: 2,
          path: 'file/one',
          side: PARENT,
          line: 1,
          updated: makeTime(1),
          range: {
            start_line: 1,
            start_character: 2,
            end_line: 2,
            end_character: 2,
          },
        };

        commentObjs['02'] = {
          ...createComment(),
          id: '02',
          in_reply_to: '04',
          patch_set: 2,
          path: 'file/one',
          unresolved: true,
          line: 1,
          updated: makeTime(3),
        };

        commentObjs['03'] = {
          ...createComment(),
          id: '03',
          patch_set: 2,
          path: 'file/one',
          side: PARENT,
          line: 2,
          updated: makeTime(1),
        };

        commentObjs['04'] = {
          ...createComment(),
          id: '04',
          patch_set: 2,
          path: 'file/one',
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['05'] = {
          ...createComment(),
          id: '05',
          patch_set: 2,
          line: 2,
          updated: makeTime(1),
        };

        commentObjs['06'] = {
          ...createComment(),
          id: '06',
          patch_set: 3,
          line: 2,
          updated: makeTime(1),
        };

        commentObjs['07'] = {
          ...createComment(),
          id: '07',
          patch_set: 2,
          side: PARENT,
          unresolved: false,
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['08'] = {
          ...createComment(),
          id: '08',
          patch_set: 2,
          side: PARENT,
          unresolved: true,
          in_reply_to: '07',
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['09'] = {
          ...createComment(),
          id: '09',
          patch_set: 3,
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['10'] = {
          ...createComment(),
          id: '10',
          patch_set: 5,
          side: PARENT,
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['11'] = {
          ...createComment(),
          id: '11',
          patch_set: 5,
          line: 1,
          updated: makeTime(1),
        };

        commentObjs['12'] = {
          ...createDraft(),
          id: '12',
          patch_set: 2,
          side: PARENT,
          line: 1,
          updated: makeTime(3),
          path: 'file/one',
        };

        commentObjs['13'] = {
          ...createDraft(),
          id: '13',
          in_reply_to: '04',
          patch_set: 2,
          line: 1,
          // Draft gets lower timestamp than published comment, because we
          // want to test that the draft still gets sorted to the end.
          updated: makeTime(2),
          path: 'file/one',
        };

        commentObjs['14'] = {
          ...createDraft(),
          id: '14',
          patch_set: 3,
          line: 1,
          path: 'file/two',
          updated: makeTime(3),
        };

        const drafts = {
          'file/one': [
            commentObjs['12'],
            commentObjs['13'],
          ],
          'file/two': [
            commentObjs['14'],
          ],
        };
        const robotComments = {
          'file/one': [
            commentObjs['01'], commentObjs['02'],
          ],
        };
        const comments = {
          'file/one': [commentObjs['03'], commentObjs['04']],
          'file/two': [commentObjs['05'], commentObjs['06']],
          'file/three': [commentObjs['07'], commentObjs['08'],
            commentObjs['09']],
          'file/four': [commentObjs['10'], commentObjs['11']],
        };
        element._changeComments =
            new ChangeComments(comments, robotComments, drafts, {}, {});
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

      test('getCommentsForPath', () => {
        const patchRange = {basePatchNum: 1, patchNum: 3};
        let path = 'file/one';
        let comments = element._changeComments.getCommentsForPath(path,
            patchRange);
        assert.equal(comments.filter(c => isInBaseOfPatchRange(c, patchRange))
            .length, 0);
        assert.equal(comments.filter(c => isInRevisionOfPatchRange(c,
            patchRange)).length, 0);

        path = 'file/two';
        comments = element._changeComments.getCommentsForPath(path,
            patchRange);
        assert.equal(comments.filter(c => isInBaseOfPatchRange(c, patchRange))
            .length, 0);
        assert.equal(comments.filter(c => isInRevisionOfPatchRange(c,
            patchRange)).length, 2);

        patchRange.basePatchNum = 2;
        comments = element._changeComments.getCommentsForPath(path,
            patchRange);
        assert.equal(comments.filter(c => isInBaseOfPatchRange(c,
            patchRange)).length, 1);
        assert.equal(comments.filter(c => isInRevisionOfPatchRange(c,
            patchRange)).length, 2);

        patchRange.basePatchNum = PARENT;
        path = 'file/three';
        comments = element._changeComments.getCommentsForPath(path,
            patchRange);
        assert.equal(comments.filter(c => isInBaseOfPatchRange(c, patchRange))
            .length, 0);
        assert.equal(comments.filter(c => isInRevisionOfPatchRange(c,
            patchRange)).length, 1);
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
        const comments = {
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
        element._changeComments = new ChangeComments(comments, {}, {}, 1234);
        assert.equal(
            element._changeComments.computeUnresolvedNum(1, 'path'), 0);
      });

      test('computeCommentsString', () => {
        const changeComments = createChangeComments();
        const parentTo1 = {
          basePatchNum: 'PARENT',
          patchNum: 1,
        };
        const parentTo2 = {
          basePatchNum: 'PARENT',
          patchNum: 2,
        };
        const _1To2 = {
          basePatchNum: 1,
          patchNum: 2,
        };

        assert.equal(
            changeComments.computeCommentsString(parentTo1, '/COMMIT_MSG',
                {__path: '/COMMIT_MSG'}), '2 comments (1 unresolved)');
        assert.equal(
            changeComments.computeCommentsString(parentTo1, '/COMMIT_MSG',
                {__path: '/COMMIT_MSG', status: 'U'}, true),
            '2 comments (1 unresolved)(no changes)');
        assert.equal(
            changeComments.computeCommentsString(_1To2, '/COMMIT_MSG',
                {__path: '/COMMIT_MSG'}), '3 comments (1 unresolved)');

        assert.equal(
            changeComments.computeCommentsString(parentTo1, 'myfile.txt',
                {__path: 'myfile.txt'}), '1 comment');
        assert.equal(
            changeComments.computeCommentsString(_1To2, 'myfile.txt',
                {__path: 'myfile.txt'}), '3 comments');

        assert.equal(
            changeComments.computeCommentsString(parentTo1,
                'file_added_in_rev2.txt',
                {__path: 'file_added_in_rev2.txt'}), '');
        assert.equal(
            changeComments.computeCommentsString(_1To2,
                'file_added_in_rev2.txt',
                {__path: 'file_added_in_rev2.txt'}), '');

        assert.equal(
            changeComments.computeCommentsString(parentTo2, '/COMMIT_MSG',
                {__path: '/COMMIT_MSG'}), '1 comment');
        assert.equal(
            changeComments.computeCommentsString(_1To2, '/COMMIT_MSG',
                {__path: '/COMMIT_MSG'}), '3 comments (1 unresolved)');

        assert.equal(
            changeComments.computeCommentsString(parentTo2, 'myfile.txt',
                {__path: 'myfile.txt'}), '2 comments');
        assert.equal(
            changeComments.computeCommentsString(_1To2, 'myfile.txt',
                {__path: 'myfile.txt'}), '3 comments');

        assert.equal(
            changeComments.computeCommentsString(parentTo2,
                'file_added_in_rev2.txt',
                {__path: 'file_added_in_rev2.txt'}), '');
        assert.equal(
            changeComments.computeCommentsString(_1To2,
                'file_added_in_rev2.txt',
                {__path: 'file_added_in_rev2.txt'}), '');
        assert.equal(
            changeComments.computeCommentsString(parentTo2, 'unresolved.file',
                {__path: 'unresolved.file'}), '2 comments (1 unresolved)');
        assert.equal(
            changeComments.computeCommentsString(_1To2, 'unresolved.file',
                {__path: 'unresolved.file'}), '2 comments (1 unresolved)');
      });

      test('computeCommentThreadCount', () => {
        assert.equal(element._changeComments
            .computeCommentThreadCount({
              patchNum: 2,
              path: 'file/one',
            }), 3);
        assert.equal(element._changeComments
            .computeCommentThreadCount({
              patchNum: 1,
              path: 'file/one',
            }), 0);
        assert.equal(element._changeComments
            .computeCommentThreadCount({
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
            ...createCommentThread([{...commentObjs['01'], path: 'file/one'}]),
          }, {
            ...createCommentThread([{...commentObjs['03'], path: 'file/one'}]),
          }, {
            ...createCommentThread([{...commentObjs['04'], path: 'file/one'},
              {...commentObjs['02'], path: 'file/one'},
              {...commentObjs['13'], path: 'file/one'}]),
          }, {
            ...createCommentThread([{...commentObjs['05'], path: 'file/two'}]),
          }, {
            ...createCommentThread([{...commentObjs['06'], path: 'file/two'}]),
          }, {
            ...createCommentThread([{...commentObjs['07'], path: 'file/three'},
              {...commentObjs['08'], path: 'file/three'}]),
          }, {
            ...createCommentThread([{...commentObjs['09'], path: 'file/three'}]
            ),
          }, {
            ...createCommentThread([{...commentObjs['10'], path: 'file/four'}]),
          }, {
            ...createCommentThread([{...commentObjs['11'], path: 'file/four'}]),
          }, {
            ...createCommentThread([{...commentObjs['12'], path: 'file/one'}]),
          }, {
            ...createCommentThread([{...commentObjs['14'], path: 'file/two'}]),
          },
        ];
        const threads = element._changeComments.getAllThreadsForChange();
        assert.deepEqual(threads, expectedThreads);
      });
    });
  });
});

