import { DiffViewMode } from "../../api/diff";
import { BehaviorSubject, Observable } from "rxjs";

interface ViewState {
  // If the user screen width is too low then we want to set the diffMode to
  // Unified
  diffMode?: DiffViewMode;
}

const initialState: ViewState = {};

const privateState$ = new BehaviorSubject(initialState);

export const viewState$: Observable<ViewState> = privateState$;

export const diffViewMode

