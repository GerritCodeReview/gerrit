/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {filter} from 'rxjs/operators';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
import {isDefined} from '../../../types/types';
import {select} from '../../../utils/observable-util';
import {Comment} from '../../../types/common';

export interface CommentState {
  comment: Comment;
  commentedText?: string;
}

export const commentModelToken = define<CommentModel>('diff-model');

export class CommentModel extends Model<CommentState | undefined> {
  readonly comment$: Observable<Comment> = select(
    this.state$.pipe(filter(isDefined)),
    commentState => commentState.comment
  );

  readonly commentedText$: Observable<string | undefined> = select(
    this.state$.pipe(filter(isDefined)),
    commentState => commentState.commentedText
  );
}
