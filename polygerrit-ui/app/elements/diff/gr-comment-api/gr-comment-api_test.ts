/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {ChangeComments} from './gr-comment-api';
import {
  isInRevisionOfPatchRange,
  isInBaseOfPatchRange,
  isDraftThread,
  isUnresolved,
  createCommentThreads,
} from '../../../utils/comment-util';
import {
  createDraft,
  createComment,
  createChangeComments,
  createCommentThread,
  createFileInfo,
} from '../../../test/test-data-generators';
import {CommentSide, FileInfoStatus} from '../../../constants/constants';
import {
  BasePatchSetNum,
  CommentInfo,
  CommentThread,
  DraftInfo,
  PARENT,
  PatchRange,
  PatchSetNum,
  RevisionPatchSetNum,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {stubRestApi} from '../../../test/test-utils';
import {assert} from '@open-wc/testing';

suite('ChangeComments tests', () => {
  let changeComments: ChangeComments;

  suite('_changeComment methods', () => {
    setup(() => {
      stubRestApi('getDiffComments').resolves({});
      stubRestApi('getDiffDrafts').resolves({});
    });

    suite('ported comments', () => {
      let portedComments: {[path: string]: CommentInfo[]};
      const comment1: CommentInfo = {
        ...createComment(),
        unresolved: true,
        id: '1' as UrlEncodedCommentId,
        line: 136,
        patch_set: 2 as RevisionPatchSetNum,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 1,
          end_character: 1,
        },
      };

      const comment2: CommentInfo = {
        ...createComment(),
        patch_set: 2 as RevisionPatchSetNum,
        id: '2' as UrlEncodedCommentId,
        line: 5,
      };

      const comment3: CommentInfo = {
        ...createComment(),
        side: CommentSide.PARENT,
        line: 10,
        unresolved: true,
      };

      const comment4: CommentInfo = {
        ...comment3,
        parent: -2,
      };

      const draft1: DraftInfo = {
        ...createDraft(),
        id: 'db977012_e1f13828' as UrlEncodedCommentId,
        line: 4,
        patch_set: 2 as RevisionPatchSetNum,
      };
      const draft2: DraftInfo = {
        ...createDraft(),
        id: '503008e2_0ab203ee' as UrlEncodedCommentId,
        line: 11,
        unresolved: true,
        // slightly larger timestamp so it's sorted higher
        updated: '2018-02-13 22:49:48.018000001' as Timestamp,
        patch_set: 2 as RevisionPatchSetNum,
      };

      setup(() => {
        portedComments = {
          'karma.conf.js': [
            {
              ...comment1,
              patch_set: 4 as RevisionPatchSetNum,
              range: {
                start_line: 136,
                start_character: 16,
                end_line: 136,
                end_character: 29,
              },
            },
          ],
        };

        changeComments = new ChangeComments(
          {
            /* comments */
            'karma.conf.js': [
              // resolved comment that will not be ported over
              comment2,
              // original comment that will be ported over to patchset 4
              comment1,
            ],
          },
          {} /* drafts */,
          portedComments,
          {} /* ported drafts */
        );
      });

      test('threads containing ported comment are returned', () => {
        assert.equal(changeComments.getAllThreadsForChange().length, 2);

        const portedThreads = changeComments._getPortedCommentThreads(
          {path: 'karma.conf.js'},
          {patchNum: 4 as RevisionPatchSetNum, basePatchNum: PARENT}
        );

        assert.equal(portedThreads.length, 1);
        // check that the location of the thread matches the ported comment
        assert.equal(portedThreads[0].patchNum, 4 as RevisionPatchSetNum);
        assert.deepEqual(portedThreads[0].range, {
          start_line: 136,
          start_character: 16,
          end_line: 136,
          end_character: 29,
        });

        // thread ported over if comparing patchset 1 vs patchset 4
        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 4 as RevisionPatchSetNum,
              basePatchNum: 1 as BasePatchSetNum,
            }
          ).length,
          1
        );

        // verify ported thread is not returned if original thread will be
        // shown
        // original thread attached to right side
        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {patchNum: 2 as RevisionPatchSetNum, basePatchNum: PARENT}
          ).length,
          0
        );
        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 2 as RevisionPatchSetNum,
              basePatchNum: 1 as BasePatchSetNum,
            }
          ).length,
          0
        );

        // original thread attached to left side
        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 3 as RevisionPatchSetNum,
              basePatchNum: 2 as BasePatchSetNum,
            }
          ).length,
          0
        );
      });

      test('threads without any ported comment are filtered out', () => {
        changeComments = new ChangeComments(
          {
            /* comments */
            // comment that is not ported over
            'karma.conf.js': [comment2],
          },
          {
            /* drafts */ 'karma.conf.js': [draft2],
          },
          // comment1 that is ported over but does not have any thread
          // that has a comment that matches it
          portedComments,
          {} /* ported drafts */
        );

        assert.equal(
          createCommentThreads(
            changeComments.getAllCommentsForPath('karma.conf.js')
          ).length,
          1
        );
        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {patchNum: 4 as RevisionPatchSetNum, basePatchNum: PARENT}
          ).length,
          0
        );
      });

      test('comments with side=PARENT are ported over', () => {
        changeComments = new ChangeComments(
          {
            /* comments */
            // comment left on Base
            'karma.conf.js': [comment3],
          },
          {
            /* drafts */ 'karma.conf.js': [draft2],
          },
          {
            /* ported comments */
            'karma.conf.js': [
              {
                ...comment3,
                line: 31,
                patch_set: 4 as RevisionPatchSetNum,
              },
            ],
          },
          {} /* ported drafts */
        );

        const portedThreads = changeComments._getPortedCommentThreads(
          {path: 'karma.conf.js'},
          {patchNum: 4 as RevisionPatchSetNum, basePatchNum: PARENT}
        );
        assert.equal(portedThreads.length, 1);
        assert.equal(portedThreads[0].line, 31);

        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 4 as RevisionPatchSetNum,
              basePatchNum: -2 as BasePatchSetNum,
            }
          ).length,
          0
        );

        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 4 as RevisionPatchSetNum,
              basePatchNum: 2 as BasePatchSetNum,
            }
          ).length,
          0
        );
      });

      test('comments left on merge parent is not ported over', () => {
        changeComments = new ChangeComments(
          {
            /* comments */
            // comment left on Base
            'karma.conf.js': [comment4],
          },
          {
            /* drafts */ 'karma.conf.js': [draft2],
          },
          {
            /* ported comments */
            'karma.conf.js': [
              {
                ...comment4,
                line: 31,
                patch_set: 4 as RevisionPatchSetNum,
              },
            ],
          },
          {} /* ported drafts */
        );

        const portedThreads = changeComments._getPortedCommentThreads(
          {path: 'karma.conf.js'},
          {patchNum: 4 as RevisionPatchSetNum, basePatchNum: PARENT}
        );
        assert.equal(portedThreads.length, 0);

        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 4 as RevisionPatchSetNum,
              basePatchNum: -2 as BasePatchSetNum,
            }
          ).length,
          0
        );

        assert.equal(
          changeComments._getPortedCommentThreads(
            {path: 'karma.conf.js'},
            {
              patchNum: 4 as RevisionPatchSetNum,
              basePatchNum: 2 as BasePatchSetNum,
            }
          ).length,
          0
        );
      });

      test('ported comments contribute to comment count', () => {
        const fileInfo = createFileInfo();
        assert.equal(
          changeComments.computeCommentsString(
            {basePatchNum: PARENT, patchNum: 2 as RevisionPatchSetNum},
            'karma.conf.js',
            fileInfo
          ),
          '2 comments (1 unresolved)'
        );

        // comment1 is ported over to patchset 4
        assert.equal(
          changeComments.computeCommentsString(
            {basePatchNum: PARENT, patchNum: 4 as RevisionPatchSetNum},
            'karma.conf.js',
            fileInfo
          ),
          '1 comment (1 unresolved)'
        );
      });

      test('drafts are ported over', () => {
        changeComments = new ChangeComments(
          {} /* comments */,
          {
            /* drafts */
            // draft1: resolved draft that will be ported over to ps 4
            // draft2: unresolved draft that will be ported over to ps 4
            'karma.conf.js': [draft1, draft2],
          },
          {} /* ported comments */,
          {
            /* ported drafts */
            'karma.conf.js': [
              {
                ...draft1,
                line: 5,
                patch_set: 4 as RevisionPatchSetNum,
              },
              {
                ...draft2,
                line: 31,
                patch_set: 4 as RevisionPatchSetNum,
              },
            ],
          }
        );

        const portedThreads = changeComments._getPortedCommentThreads(
          {path: 'karma.conf.js'},
          {patchNum: 4 as RevisionPatchSetNum, basePatchNum: PARENT}
        );

        // resolved draft is ported over
        assert.equal(portedThreads.length, 2);
        assert.equal(portedThreads[0].line, 5);
        assert.isTrue(isDraftThread(portedThreads[0]));
        assert.isFalse(isUnresolved(portedThreads[0]));

        // unresolved draft is ported over
        assert.equal(portedThreads[1].line, 31);
        assert.isTrue(isDraftThread(portedThreads[1]));
        assert.isTrue(isUnresolved(portedThreads[1]));

        assert.equal(
          createCommentThreads(
            changeComments.getAllCommentsForPath('karma.conf.js')
          ).length,
          0
        );
      });
    });

    test('_isInBaseOfPatchRange', () => {
      const comment: {
        patch_set?: PatchSetNum;
        side?: CommentSide;
        parent?: number;
      } = {patch_set: 1 as PatchSetNum};
      const patchRange = {
        basePatchNum: 1 as BasePatchSetNum,
        patchNum: 2 as RevisionPatchSetNum,
      };
      assert.isTrue(isInBaseOfPatchRange(comment, patchRange));

      patchRange.basePatchNum = PARENT;
      assert.isFalse(isInBaseOfPatchRange(comment, patchRange));

      comment.side = CommentSide.PARENT;
      assert.isFalse(isInBaseOfPatchRange(comment, patchRange));

      comment.patch_set = 2 as PatchSetNum;
      assert.isTrue(isInBaseOfPatchRange(comment, patchRange));

      patchRange.basePatchNum = -2 as BasePatchSetNum;
      comment.side = CommentSide.PARENT;
      comment.parent = 1;
      assert.isFalse(isInBaseOfPatchRange(comment, patchRange));

      comment.parent = 2;
      assert.isTrue(isInBaseOfPatchRange(comment, patchRange));
    });

    test('isInRevisionOfPatchRange', () => {
      const comment: {
        patch_set?: PatchSetNum;
        side?: CommentSide;
      } = {patch_set: 123 as PatchSetNum};
      const patchRange: PatchRange = {
        basePatchNum: 122 as BasePatchSetNum,
        patchNum: 124 as RevisionPatchSetNum,
      };
      assert.isFalse(isInRevisionOfPatchRange(comment, patchRange));

      patchRange.patchNum = 123 as RevisionPatchSetNum;
      assert.isTrue(isInRevisionOfPatchRange(comment, patchRange));

      comment.side = CommentSide.PARENT;
      assert.isFalse(isInRevisionOfPatchRange(comment, patchRange));
    });

    suite('comment ranges and paths', () => {
      const comments = [
        {
          // legacy from when we were still supporting robot comments
          ...createComment(),
          id: '01' as UrlEncodedCommentId,
        },
        {
          // legacy from when we were still supporting robot comments
          ...createComment(),
          id: '02' as UrlEncodedCommentId,
        },
        {
          ...createComment(),
          id: '03' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          path: 'file/1',
          side: CommentSide.PARENT,
          line: 2,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '04' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          path: 'file/1',
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '05' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          line: 2,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '06' as UrlEncodedCommentId,
          patch_set: 3 as RevisionPatchSetNum,
          line: 2,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '07' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          side: CommentSide.PARENT,
          unresolved: false,
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '08' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          side: CommentSide.PARENT,
          unresolved: true,
          in_reply_to: '07' as UrlEncodedCommentId,
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '09' as UrlEncodedCommentId,
          patch_set: 3 as RevisionPatchSetNum,
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '10' as UrlEncodedCommentId,
          patch_set: 5 as RevisionPatchSetNum,
          side: CommentSide.PARENT,
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createComment(),
          id: '11' as UrlEncodedCommentId,
          patch_set: 5 as RevisionPatchSetNum,
          line: 1,
          updated: makeTime(1),
        },
        {
          ...createDraft(),
          id: '12' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          side: CommentSide.PARENT,
          line: 1,
          updated: makeTime(3),
          path: 'file/1',
        },
        {
          ...createDraft(),
          id: '13' as UrlEncodedCommentId,
          in_reply_to: '04' as UrlEncodedCommentId,
          patch_set: 2 as RevisionPatchSetNum,
          line: 1,
          // Draft gets lower timestamp than published comment, because we
          // want to test that the draft still gets sorted to the end.
          updated: makeTime(2),
          path: 'file/1',
        },
        {
          ...createDraft(),
          id: '14' as UrlEncodedCommentId,
          patch_set: 3 as RevisionPatchSetNum,
          line: 1,
          path: 'file/2',
          updated: makeTime(3),
        },
      ] as const;
      const drafts: {[path: string]: DraftInfo[]} = {
        'file/1': [comments[11], comments[12]],
        'file/2': [comments[13]],
      };
      const commentsByFile: {[path: string]: CommentInfo[]} = {
        'file/1': [comments[2], comments[3]],
        'file/2': [comments[4], comments[5]],
        'file/3': [comments[6], comments[7], comments[8]],
        'file/4': [comments[9], comments[10]],
      };

      function makeTime(mins: number) {
        return `2013-02-26 15:0${mins}:43.986000000` as Timestamp;
      }

      setup(() => {
        changeComments = new ChangeComments(
          commentsByFile,
          drafts,
          {} /* portedComments */,
          {} /* portedDrafts */
        );
      });

      test('getPaths', () => {
        const patchRange: PatchRange = {
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: 4 as RevisionPatchSetNum,
        };
        let paths = changeComments.getPaths(patchRange);
        assert.equal(Object.keys(paths).length, 0);

        patchRange.basePatchNum = PARENT;
        patchRange.patchNum = 3 as RevisionPatchSetNum;
        paths = changeComments.getPaths(patchRange);
        assert.notProperty(paths, 'file/1');
        assert.property(paths, 'file/2');
        assert.property(paths, 'file/3');
        assert.notProperty(paths, 'file/4');

        patchRange.patchNum = 2 as RevisionPatchSetNum;
        paths = changeComments.getPaths(patchRange);
        assert.property(paths, 'file/1');
        assert.property(paths, 'file/2');
        assert.property(paths, 'file/3');
        assert.notProperty(paths, 'file/4');

        paths = changeComments.getPaths();
        assert.property(paths, 'file/1');
        assert.property(paths, 'file/2');
        assert.property(paths, 'file/3');
        assert.property(paths, 'file/4');
      });

      test('getCommentsForPath', () => {
        const patchRange: PatchRange = {
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: 3 as RevisionPatchSetNum,
        };
        let path = 'file/1';
        let comments = changeComments.getCommentsForPath(path, patchRange);
        assert.equal(
          comments.filter(c => isInBaseOfPatchRange(c, patchRange)).length,
          0
        );
        assert.equal(
          comments.filter(c => isInRevisionOfPatchRange(c, patchRange)).length,
          0
        );

        path = 'file/2';
        comments = changeComments.getCommentsForPath(path, patchRange);
        assert.equal(
          comments.filter(c => isInBaseOfPatchRange(c, patchRange)).length,
          0
        );
        assert.equal(
          comments.filter(c => isInRevisionOfPatchRange(c, patchRange)).length,
          2
        );

        patchRange.basePatchNum = 2 as BasePatchSetNum;
        comments = changeComments.getCommentsForPath(path, patchRange);
        assert.equal(
          comments.filter(c => isInBaseOfPatchRange(c, patchRange)).length,
          1
        );
        assert.equal(
          comments.filter(c => isInRevisionOfPatchRange(c, patchRange)).length,
          2
        );

        patchRange.basePatchNum = PARENT;
        path = 'file/3';
        comments = changeComments.getCommentsForPath(path, patchRange);
        assert.equal(
          comments.filter(c => isInBaseOfPatchRange(c, patchRange)).length,
          0
        );
        assert.equal(
          comments.filter(c => isInRevisionOfPatchRange(c, patchRange)).length,
          1
        );
      });

      test('getAllCommentsForPath', () => {
        let path = 'file/1';
        let comments = changeComments.getAllCommentsForPath(path);
        assert.equal(comments.length, 2);
        path = 'file/2';
        comments = changeComments.getAllCommentsForPath(path, 2 as PatchSetNum);
        assert.equal(comments.length, 1);
        const aCopyOfComments = changeComments.getAllCommentsForPath(
          path,
          2 as PatchSetNum
        );
        assert.deepEqual(comments, aCopyOfComments);
        assert.notEqual(comments[0], aCopyOfComments[0]);
      });

      test('getAllDraftsForPath', () => {
        const path = 'file/1';
        const drafts = changeComments.getAllDraftsForPath(path);
        assert.equal(drafts.length, 2);
      });

      test('computeUnresolvedNum', () => {
        assert.equal(
          changeComments.computeUnresolvedNum({
            patchNum: 2 as PatchSetNum,
            path: 'file/1',
          }),
          0
        );
        assert.equal(
          changeComments.computeUnresolvedNum({
            patchNum: 1 as PatchSetNum,
            path: 'file/1',
          }),
          0
        );
        assert.equal(
          changeComments.computeUnresolvedNum({
            patchNum: 2 as PatchSetNum,
            path: 'file/3',
          }),
          1
        );
      });

      test('computeUnresolvedNum w/ non-linear thread', () => {
        const comments: {[path: string]: CommentInfo[]} = {
          path: [
            {
              id: '9c6ba3c6_28b7d467' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              updated: '2018-02-28 14:41:13.000000000' as Timestamp,
              unresolved: true,
            },
            {
              id: '3df7b331_0bead405' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              in_reply_to: '1c346623_ab85d14a' as UrlEncodedCommentId,
              updated: '2018-02-28 23:07:55.000000000' as Timestamp,
              unresolved: false,
            },
            {
              id: '6153dce6_69958d1e' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              in_reply_to: '9c6ba3c6_28b7d467' as UrlEncodedCommentId,
              updated: '2018-02-28 17:11:31.000000000' as Timestamp,
              unresolved: true,
            },
            {
              id: '1c346623_ab85d14a' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              in_reply_to: '9c6ba3c6_28b7d467' as UrlEncodedCommentId,
              updated: '2018-02-28 23:01:39.000000000' as Timestamp,
              unresolved: false,
            },
          ],
        };
        changeComments = new ChangeComments(comments, {}, {}, {});
        assert.equal(
          changeComments.computeUnresolvedNum(
            {patchNum: 1 as PatchSetNum},
            true
          ),
          0
        );
      });

      test('computeCommentsString', () => {
        const changeComments = createChangeComments();
        const parentTo1: PatchRange = {
          basePatchNum: PARENT,
          patchNum: 1 as RevisionPatchSetNum,
        };
        const parentTo2: PatchRange = {
          basePatchNum: PARENT,
          patchNum: 2 as RevisionPatchSetNum,
        };
        const _1To2: PatchRange = {
          basePatchNum: 1 as BasePatchSetNum,
          patchNum: 2 as RevisionPatchSetNum,
        };
        const fileInfo = createFileInfo();

        assert.equal(
          changeComments.computeCommentsString(
            parentTo1,
            '/COMMIT_MSG',
            fileInfo
          ),
          '2 comments (1 unresolved)'
        );
        assert.equal(
          changeComments.computeCommentsString(
            parentTo1,
            '/COMMIT_MSG',
            {...fileInfo, status: FileInfoStatus.UNMODIFIED},
            true
          ),
          '2 comments (1 unresolved)(no changes)'
        );
        assert.equal(
          changeComments.computeCommentsString(_1To2, '/COMMIT_MSG', fileInfo),
          '3 comments (1 unresolved)'
        );

        assert.equal(
          changeComments.computeCommentsString(
            parentTo1,
            'myfile.txt',
            fileInfo
          ),
          '1 comment'
        );
        assert.equal(
          changeComments.computeCommentsString(_1To2, 'myfile.txt', fileInfo),
          '3 comments'
        );

        assert.equal(
          changeComments.computeCommentsString(
            parentTo1,
            'file_added_in_rev2.txt',
            fileInfo
          ),
          ''
        );
        assert.equal(
          changeComments.computeCommentsString(
            _1To2,
            'file_added_in_rev2.txt',
            fileInfo
          ),
          ''
        );

        assert.equal(
          changeComments.computeCommentsString(
            parentTo2,
            '/COMMIT_MSG',
            fileInfo
          ),

          '1 comment'
        );
        assert.equal(
          changeComments.computeCommentsString(_1To2, '/COMMIT_MSG', fileInfo),
          '3 comments (1 unresolved)'
        );

        assert.equal(
          changeComments.computeCommentsString(
            parentTo2,
            'myfile.txt',
            fileInfo
          ),
          '2 comments'
        );
        assert.equal(
          changeComments.computeCommentsString(_1To2, 'myfile.txt', fileInfo),
          '3 comments'
        );

        assert.equal(
          changeComments.computeCommentsString(
            parentTo2,
            'file_added_in_rev2.txt',
            fileInfo
          ),
          ''
        );
        assert.equal(
          changeComments.computeCommentsString(
            _1To2,
            'file_added_in_rev2.txt',
            fileInfo
          ),
          ''
        );
        assert.equal(
          changeComments.computeCommentsString(
            parentTo2,
            'unresolved.file',
            fileInfo
          ),
          '2 comments (1 unresolved)'
        );
        assert.equal(
          changeComments.computeCommentsString(
            _1To2,
            'unresolved.file',
            fileInfo
          ),
          '2 comments (1 unresolved)'
        );
      });

      test('computeCommentThreads - check length', () => {
        assert.equal(
          changeComments.computeCommentThreads({
            patchNum: 2 as PatchSetNum,
            path: 'file/1',
          }).length,
          2
        );
        assert.deepEqual(
          changeComments.computeCommentThreads({
            patchNum: 1 as PatchSetNum,
            path: 'file/1',
          }),
          []
        );
        assert.equal(
          changeComments.computeCommentThreads({
            patchNum: 2 as PatchSetNum,
            path: 'file/3',
          }).length,
          1
        );
      });

      test('computeCommentThreads - check content', () => {
        const expectedThreads: CommentThread[] = [
          {
            ...createCommentThread([{...comments[9], path: 'file/4'}]),
          },
          {
            ...createCommentThread([{...comments[10], path: 'file/4'}]),
          },
        ];
        assert.deepEqual(
          changeComments.computeCommentThreads({
            path: 'file/4',
          }),
          expectedThreads
        );
      });

      test('computeDraftCount', () => {
        assert.equal(
          changeComments.computeDraftCount({
            patchNum: 2 as PatchSetNum,
            path: 'file/1',
          }),
          2
        );
        assert.equal(
          changeComments.computeDraftCount({
            patchNum: 1 as PatchSetNum,
            path: 'file/1',
          }),
          0
        );
        assert.equal(
          changeComments.computeDraftCount({
            patchNum: 2 as PatchSetNum,
            path: 'file/3',
          }),
          0
        );
        assert.equal(changeComments.computeDraftCount(), 3);
      });

      test('getAllPublishedComments', () => {
        let publishedComments = changeComments.getAllPublishedComments();
        assert.equal(Object.keys(publishedComments).length, 4);
        assert.equal(Object.keys(publishedComments['file/1']).length, 2);
        assert.equal(Object.keys(publishedComments['file/2']).length, 2);
        publishedComments = changeComments.getAllPublishedComments(
          2 as PatchSetNum
        );
        assert.equal(Object.keys(publishedComments['file/1']).length, 2);
        assert.equal(Object.keys(publishedComments['file/2']).length, 1);
      });

      test('getAllComments', () => {
        let comments = changeComments.getAllComments();
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments['file/1']).length, 2);
        assert.equal(Object.keys(comments['file/2']).length, 2);
        comments = changeComments.getAllComments(false, 2 as PatchSetNum);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments['file/1']).length, 2);
        assert.equal(Object.keys(comments['file/2']).length, 1);
        // Include drafts
        comments = changeComments.getAllComments(true);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments['file/1']).length, 4);
        assert.equal(Object.keys(comments['file/2']).length, 3);
        comments = changeComments.getAllComments(true, 2 as PatchSetNum);
        assert.equal(Object.keys(comments).length, 4);
        assert.equal(Object.keys(comments['file/1']).length, 4);
        assert.equal(Object.keys(comments['file/2']).length, 1);
      });

      test('computeAllThreads', () => {
        const expectedThreads: CommentThread[] = [
          {
            ...createCommentThread([
              {...comments[3], path: 'file/1'},
              {...comments[12], path: 'file/1'},
            ]),
          },
          {
            ...createCommentThread([{...comments[11], path: 'file/1'}]),
          },
          {
            ...createCommentThread([{...comments[2], path: 'file/1'}]),
          },
          {
            ...createCommentThread([{...comments[4], path: 'file/2'}]),
          },
          {
            ...createCommentThread([{...comments[13], path: 'file/2'}]),
          },
          {
            ...createCommentThread([{...comments[5], path: 'file/2'}]),
          },
          {
            ...createCommentThread([
              {...comments[6], path: 'file/3'},
              {...comments[7], path: 'file/3'},
            ]),
          },
          {
            ...createCommentThread([{...comments[8], path: 'file/3'}]),
          },
          {
            ...createCommentThread([{...comments[9], path: 'file/4'}]),
          },
          {
            ...createCommentThread([{...comments[10], path: 'file/4'}]),
          },
        ];
        const threads = changeComments.getAllThreadsForChange();
        assert.deepEqual(
          threads.map(t => t.rootId),
          expectedThreads.map(t => t.rootId)
        );
        assert.deepEqual(threads, expectedThreads);
      });
    });
  });
});
