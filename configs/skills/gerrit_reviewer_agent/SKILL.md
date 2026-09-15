---
name: gerrit-reviewer-agent
description: >
  Instructs the agentic harness to trigger the gerrit_reviewer agent
  to perform automated multi-perspective code reviews on Gerrit Changes.
---

# Gerrit Reviewer Agent Skill

## Overview

This skill instructs the main agent on how and when to trigger the `gerrit_reviewer` subagent
to perform automated code reviews. It acts as a routing and delegation skill.

## Triggering the Review

The agent should use this skill when:
- The user explicitly requests a code review on a Gerrit change (e.g., "review this change", "run code review on change 12345").
- The user provides a Gerrit change URL or number and asks for review feedback.

## Step-by-Step Execution

1.  **Extract Gerrit Change Info**:
    - Identify the Gerrit change URL or number from the user request.
    - Example URL:
      `https://gerrit-review.googlesource.com/c/gerrit/+/561628`
    - Extract the host (e.g., `gerrit-review.googlesource.com` or `https://gerrit-review.googlesource.com`) and the Change ID or change number (e.g., `561628`).
    - If no host is provided, default to `https://gerrit-review.googlesource.com`.

2.  **Invoke Gerrit Reviewer**:
    - Trigger the `gerrit_reviewer` subagent using the `invoke_subagent` tool.
    - **TypeName**: `gerrit_reviewer`
    - **Role**: `Automated Code Reviewer`
    - **Prompt**: Construct a prompt containing the Gerrit Change URL or change number and host.
      - **When invoked by another agent (Subagent Mode)**: Explicitly instruct the subagent to run in Subagent Mode and NOT post draft comments, replies, or publish anything to Gerrit. Instruct it to collect and synthesize all findings and return the feedback directly via message.
      - *Example Subagent Mode Prompt*:
        > Run in Subagent Mode. Please perform a code review on the Gerrit change:
        > https://gerrit-review.googlesource.com/c/gerrit/+/561628
        > Do NOT post draft comments, replies, or publish anything to Gerrit. Return all findings and synthesized feedback directly to me.
      - *Example Standalone Mode Prompt*:
        > Please perform an automated code review on Gerrit change 561628 on host https://gerrit-review.googlesource.com. Post verified findings as draft comments.

3.  **Await Completion**:
    - Stop calling tools and wait for the `gerrit_reviewer` subagent to report back.

4.  **Process Findings & Respond to User**:
    - Once the `gerrit_reviewer` subagent sends a message containing the review findings, process the feedback.
    - Present the synthesized findings clearly to the user, highlighting any critical correctness, security, or contract-breaking defects.
