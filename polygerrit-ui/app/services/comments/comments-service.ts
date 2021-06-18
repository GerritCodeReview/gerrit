import { UrlEncodedCommentId } from "../../types/common";
import { DraftInfo } from "../../utils/comment-util";
import { discardedDrafts$ } from "./comments-model";

export class CommentsService {
  private discardedDrafts: DraftInfo[] = [];

  constructor() {
    discardedDrafts$.subscribe(discardedDrafts => {
      this.discardedDrafts = discardedDrafts;
    })
  }

  restoreDiscardedDraft(id: UrlEncodedCommentId) {
    const index = this.discardedDrafts.findIndex(draft => draft.id === id);
    if (index === -1) throw new Error('discarded draft not found');
    const draft = this.discardedDrafts.splice(index, 1);
    this.drafts.push()
  }

}