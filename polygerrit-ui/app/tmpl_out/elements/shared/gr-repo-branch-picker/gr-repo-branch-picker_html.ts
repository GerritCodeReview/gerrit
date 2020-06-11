import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrRepoBranchPicker} from '../../../../elements/shared/gr-repo-branch-picker/gr-repo-branch-picker';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrRepoBranchPickerCheck extends GrRepoBranchPicker
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-labeled-autocomplete'] = null!;
      useVars(el);
      el.setAttribute('id', `repoInput`);
      el.label = `Repository`;
      el.placeholder = `Select repo`;
      el.addEventListener('commit', this._repoCommitted.bind(this));
      el.query = this._repoQuery;
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-labeled-autocomplete'] = null!;
      useVars(el);
      el.setAttribute('id', `branchInput`);
      el.label = `Branch`;
      el.placeholder = `Select branch`;
      el.disabled = this._branchDisabled;
      el.addEventListener('commit', this._branchCommitted.bind(this));
      el.query = this._query;
    }
  }
}

