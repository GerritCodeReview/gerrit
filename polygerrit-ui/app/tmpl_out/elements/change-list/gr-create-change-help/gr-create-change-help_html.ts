import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrCreateChangeHelp} from '../../../../elements/change-list/gr-create-change-help/gr-create-change-help';

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

export class GrCreateChangeHelpCheck extends GrCreateChangeHelp
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `graphic`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `circle`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('id', `icon`);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `help`);
    }
    {
      const el: HTMLElementTagNameMap['h2'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this._handleCreateTap.bind(this));
    }
  }
}

