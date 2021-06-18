import { BehaviorSubject, Observable } from "rxjs";
import { distinctUntilChanged, map } from "rxjs/operators";
import { DraftInfo } from "../../utils/comment-util";

interface CommentState {
  discardedDrafts: DraftInfo[];
}

const initialState: CommentState = {
  discardedDrafts: []
};

const privateState$ = new BehaviorSubject(initialState);

// Re-exporting as Observable so that you can only subscribe, but not emit.
export const commentState$: Observable<CommentState> = privateState$;

export const discardedDrafts$ = commentState$.pipe(
  map(commentState => commentState.discardedDrafts),
  distinctUntilChanged()
)

export function addDiscardedDraft(draft: DraftInfo) {
  const current = privateState$.getValue();
  current.discardedDrafts.push(draft);
  privateState$.next(current);
}
