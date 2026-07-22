import re

with open('/google/cog/cloud/beckysiegel/gerrit-ws/gerrit/polygerrit-ui/app/elements/shared/gr-comment-thread/gr-comment-thread.ts', 'r') as f:
    code = f.read()

# Add reporting property
reporting_code = "  private readonly reporting = getAppContext().reportingService;\n"
if "reporting =" not in code:
    code = re.sub(r'constructor\(\) {', reporting_code + r'\n  constructor() {', code)


# Add handleCommentDisagree method
disagree_method = """  private handleCommentDisagree() {
    this.createReplyComment(
      'Disagree',
      /* userWantsToEdit= */ false,
      /* unresolved= */ false
    );
    const lastComment = this.getLastComment();
    if (lastComment) {
      this.reporting.reportInteraction(Interaction.AI_AGENT_SUGGESTION_DISAGREE, {
        commentId: lastComment.id,
      });
    }
  }
"""
if "handleCommentDisagree" not in code:
    code = re.sub(r'  private handleCommentDone\(\) \{', disagree_method + r'\n  private handleCommentDone() {', code)

# Add disagree button
disagree_btn = """                  ${this.getLastComment()?.is_ai ? html`
                    <gr-button
                      id="disagreeBtn"
                      link
                      class="action disagree"
                      ?disabled=${this.saving}
                      @click=${this.handleCommentDisagree}
                      >Disagree</gr-button
                    >
                  ` : nothing}"""
if "id=\"disagreeBtn\"" not in code:
    code = re.sub(r'(\s+)(<gr-button\s+id="ackBtn")', r'\1' + disagree_btn + r'\1\2', code)

with open('/google/cog/cloud/beckysiegel/gerrit-ws/gerrit/polygerrit-ui/app/elements/shared/gr-comment-thread/gr-comment-thread.ts', 'w') as f:
    f.write(code)
