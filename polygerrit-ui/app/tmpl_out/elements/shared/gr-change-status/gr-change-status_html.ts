import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrChangeStatus} from '../../../../elements/shared/gr-change-status/gr-change-status';

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

export class GrChangeStatusCheck extends GrChangeStatus
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-tooltip-content'] = null!;
      useVars(el);
      el.title = this.tooltipText;
      el.maxWidth = `40em`;
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this.hasStatusLink(this.revertedChange, this.resolveWeblinks, this.status))
    {
      {
        const el: HTMLElementTagNameMap['a'] = null!;
        useVars(el);
        el.setAttribute('class', `status-link`);
        el.href = this.getStatusLink(this.revertedChange, this.resolveWeblinks, this.status);
      }
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `chip`);
        el.setAttribute('ariaLabel', `Label: ${this.status}`);
      }
      setTextContent(`
          ${this._computeStatusString(this.status)}
          `);

      {
        const el: HTMLElementTagNameMap['iron-icon'] = null!;
        useVars(el);
        el.setAttribute('class', `icon`);
        el.setAttribute('hidden', `${!this.showResolveIcon(this.resolveWeblinks, this.status)}`);
      }
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (!this.hasStatusLink(this.revertedChange, this.resolveWeblinks, this.status))
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `chip`);
        el.setAttribute('ariaLabel', `Label: ${this.status}`);
      }
      setTextContent(`
        ${this._computeStatusString(this.status)}
      `);

    }
  }
}

