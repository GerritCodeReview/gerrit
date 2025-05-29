/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {filter} from 'rxjs/operators';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/base/model';
import {isDefined} from '../../../types/types';
import {select} from '../../../utils/observable-util';
import {Comment} from '../../../types/common';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {isBase64FileContent, NumericChangeId} from '../../../api/rest-api';
import {assert, assertIsDefined} from '../../../utils/common-util';
import {getContentInCommentRange} from '../../../utils/comment-util';
import {SpecialFilePath} from '../../../constants/constants';

export interface CommentState {
  comment?: Comment;
  commentedText?: string;
}

const initialState: CommentState = {
  comment: undefined,
  commentedText: undefined,
};

export const commentModelToken = define<CommentModel>('diff-model');

export class CommentModel extends Model<CommentState | undefined> {
  readonly comment$: Observable<Comment | undefined> = select(
    this.state$.pipe(filter(isDefined)),
    commentState => commentState.comment
  );

  readonly commentedText$: Observable<string | undefined> = select(
    this.state$.pipe(filter(isDefined)),
    commentState => commentState.commentedText
  );

  constructor(private readonly restApiService: RestApiService) {
    super(initialState);
  }

  async getCommentedCode(
    comment?: Comment,
    changeNum?: NumericChangeId
  ): Promise<string | undefined> {
    assertIsDefined(comment, 'comment');
    assertIsDefined(changeNum, 'changeNum');
    if (comment.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) return;
    const file = await this.restApiService.getFileContent(
      changeNum,
      comment.path!,
      comment.patch_set!
    );
    assert(
      !!file && isBase64FileContent(file) && !!file.content,
      'file content for comment not found'
    );
    const commentedText = getContentInCommentRange(file.content, comment);
    assert(!!commentedText, 'file content for comment not found');
    this.updateState({
      commentedText,
    });
    return commentedText;
  }
}
