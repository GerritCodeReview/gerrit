/**
 * Provider for listening to change update signals.
 * Clients can use these signals to refresh the change state.
 */
export declare interface ChangeUpdatesProvider {
    subscribe(repo: string, change: number, callback: () => void): void;
    unsubcribe(repo: string, change: number): void;
}
