import '../../test/common-test-setup-karma';
import { createComment, createFixSuggestionInfo } from '../../test/test-data-generators';
import { stubRestApi } from '../../test/test-utils';
import { NumericChangeId, RobotId, RobotRunId, Timestamp, UrlEncodedCommentId } from '../../types/common';
import { CommentsService } from './comments-service';

suite('change service tests', () => {
  let commentsService: CommentsService;

  test('loads logged-out', () => {
    const changeNum = 1234 as NumericChangeId;

    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
        Promise.resolve({
          'foo.c': [{
            ...createComment(),
            id: '123' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
          }],
        }));
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
        Promise.resolve({
          'foo.c': [{
            ...createComment(),
            id: '321' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
            robot_id: 'robot_1' as RobotId,
            robot_run_id: 'run_1' as RobotRunId,
            properties: {},
            fix_suggestions: [
              createFixSuggestionInfo('fix_1'),
              createFixSuggestionInfo('fix_2'),
            ],
          }]
        }));
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
        Promise.resolve({}));

    commentsService.loadAll(changeNum);
    assert.isTrue(diffCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffRobotCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffDraftsSpy.calledWithExactly(changeNum));
  });

  test('loads logged-in', () => {
    const changeNum = 1234 as NumericChangeId;

    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
        Promise.resolve({
          'foo.c': [{
            ...createComment(),
            id: '123' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
          }],
        }));
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
        Promise.resolve({
          'foo.c': [{
            ...createComment(),
            id: '321' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
            robot_id: 'robot_1' as RobotId,
            robot_run_id: 'run_1' as RobotRunId,
            properties: {},
            fix_suggestions: [
              createFixSuggestionInfo('fix_1'),
              createFixSuggestionInfo('fix_2'),
            ],
          }]
        }));
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
        Promise.resolve({}));

    commentsService.loadAll(changeNum);
    assert.isTrue(diffCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffRobotCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffDraftsSpy.calledWithExactly(changeNum));
  });

})