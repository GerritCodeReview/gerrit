import { REVERT_TAG } from "../constants/constants";
import { ChangeInfo } from "../types/common";

export function getRevertCommitHash(change: ChangeInfo) {
  const msg = change.messages?.find(m => m.tag === REVERT_TAG);
    if (!msg) throw new Error('revert message not found');
    const REVERT_REGEX = /^Created a revert of this change as (.*)$/;
    const commit = msg.message.match(REVERT_REGEX)?.[1];
    if (!commit) throw new Error('revert commit not found');
    return {
      commit,
    };
}