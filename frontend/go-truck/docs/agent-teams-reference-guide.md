# Agent Teams Master Reference Guide

> **Source**: [Claude Code Agent Teams Documentation](https://code.claude.com/docs/en/agent-teams)
> **Status**: Experimental (disabled by default)
> **Last Updated**: 2026-09-24

---

## Quick Start

### Enable Agent Teams

Add to your `.claude/settings.json`:

```json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  }
}
```

### Start Your First Team

```text
I'm designing a CLI tool that helps developers track TODO comments across
their codebase. Spawn three teammates to explore this from different angles:
one on UX, one on technical architecture, one playing devil's advocate.
```

---

## Core Concepts

### Agent Teams vs Subagents

| Aspect | Subagents | Agent Teams |
|--------|-----------|-------------|
| **Context** | Own window; returns result to caller | Own window; fully independent |
| **Communication** | Returns result to caller | Direct messaging between teammates |
| **Coordination** | Lead manages all work | Self-coordination + shared task list |
| **Best for** | Focused tasks, result matters | Complex work, discussion, collaboration |
| **Token cost** | Lower | Higher |

**Use subagents** when you need quick, focused workers that report back.
**Use agent teams** when teammates need to share findings, challenge each other, and coordinate independently.

---

## Team Architecture

### Components

| Component | Role |
|-----------|------|
| **Team Lead** | Main session that spawns teammates and coordinates |
| **Teammates** | Independent Claude Code instances working on tasks |
| **Task List** | Shared work items (pending/in-progress/completed) |
| **Mailbox** | JSON-based messaging system at `~/.claude/teams/{team-name}/inboxes/{agent-name}.json` |

### Team Storage

- **Team config**: `~/.claude/teams/{team-name}/config.json` (runtime state, cleaned on session end)
- **Task list**: `~/.claude/tasks/{team-name}/` (persists for resumed sessions)
- **Team name format**: `session-{first-8-chars-of-session-id}`

---

## Configuration

### Display Modes

Set in `~/.claude/settings.json` or via flag:

```json
{
  "teammateMode": "auto"  // options: "in-process" (default), "auto", "tmux", "iterm2"
}
```

```bash
claude --teammate-mode auto
```

| Mode | Description | Requirements |
|------|-------------|--------------|
| `in-process` | All teammates in main terminal, select via arrow keys | Any terminal |
| `auto` | Split panes in tmux/iTerm2, fallback to in-process | tmux or iTerm2 + `it2` CLI |
| `tmux` | Force split-pane via tmux | tmux |
| `iterm2` | Force iTerm2 native split panes | iTerm2 + `it2` CLI |

### Specify Teammates & Models

```text
Spawn 4 teammates to refactor these modules in parallel. Use Sonnet for each teammate.
```

**Model selection priority**:
1. Model named in spawn prompt
2. Subagent definition's `model` (if `inherit`, uses lead's model)
3. `CLAUDE_CODE_SUBAGENT_MODEL` env var (if not `inherit`)
4. Lead's current model

**Force single model** for all teammates:
```bash
export CLAUDE_CODE_SUBAGENT_MODEL_FORCE=1
export CLAUDE_CODE_SUBAGENT_MODEL=sonnet
```

### Subagent Definitions for Teammates

Define reusable roles in `.claude/agents/` or user/project scope:

```text
Spawn a teammate using the security-reviewer agent type to audit the auth module.
```

**Applied from definition**:
- `tools` (plus `SendMessage`, `TaskCreate`, `TaskGet`, `TaskList`, `TaskUpdate` for in-process)
- `model` (when not specified in prompt)
- `body` (appended to system prompt for in-process; replaces for split-pane)
- `mcpServers` (split-pane only)

**NOT applied**:
- `skills` (loads from project/user settings)

---

## Team Control Patterns

### Spawn Teammates with Clear Names

```text
Spawn three named teammates:
- researcher: investigate authentication libraries
- architect: design the module structure
- critic: challenge assumptions and find edge cases
```

### Plan Before Implement

```text
# 1. Enter plan mode first
/plan

# 2. Then request teammate
Spawn an architect teammate to refactor the authentication module.
```

Teammate plans in read-only mode → sends approval request → lead auto-approves → teammate implements.

### Direct Messaging

**In-process mode**: Arrow keys to select → Enter to view → type message → `x` to stop

**Split-pane mode**: Click pane to interact directly

### Task Management

```text
# Lead assigns explicitly
Assign the database migration task to the backend teammate

# Teammates self-claim
# After finishing, teammate picks next unassigned, unblocked task
```

**Task states**: `pending` → `in_progress` → `completed`
**Dependencies**: Blocked tasks unblock automatically when dependencies complete

### Shutdown Teammates

```text
Ask the researcher teammate to shut down
```

Lead sends shutdown request; teammate can approve (graceful exit) or reject.

---

## Best Practices

### 1. Give Enough Context

```text
Spawn a security reviewer teammate with the prompt: "Review the authentication module
at src/auth/ for security vulnerabilities. Focus on token handling, session
management, and input validation. The app uses JWT tokens stored in
httpOnly cookies. Report any issues with severity ratings."
```

### 2. Choose Appropriate Team Size

- **Start with 3-5 teammates** for most workflows
- Token costs scale linearly
- Coordination overhead increases with team size
- Diminishing returns beyond optimal size
- 3 focused teammates > 5 scattered ones

### 3. Size Tasks Appropriately

| Too Small | Too Large | Just Right |
|-----------|-----------|------------|
| Coordination overhead exceeds benefit | Long without check-ins, wasted effort risk | Self-contained, clear deliverable |

**Target**: 5-6 tasks per teammate

### 4. Wait for Completion

```text
Wait for your teammates to complete their tasks before proceeding
```

### 5. Start with Research/Review

If new to agent teams, begin with:
- PR reviews
- Library research
- Bug investigation

Clear boundaries, no coordination challenges of parallel implementation.

### 6. Avoid File Conflicts

Break work so each teammate owns different files.

### 7. Monitor & Steer

Check progress, redirect failed approaches, synthesize findings. Don't let team run unattended too long.

---

## Use Case Patterns

### Parallel Code Review

```text
Spawn three teammates to review PR #142:
- One focused on security implications
- One checking performance impact
- One validating test coverage
Have them each review and report findings.
```

### Competing Hypotheses Investigation

```text
Users report the app exits after one message instead of staying connected.
Spawn 5 agent teammates to investigate different hypotheses. Have them talk to
each other to try to disprove each other's theories, like a scientific
debate. Update the findings doc with whatever consensus emerges.
```

### New Feature Development

```text
Spawn teammates for each layer:
- frontend: build UI components
- backend: implement API endpoints
- testing: write integration tests
- devops: update CI/CD pipeline
Coordinate through shared task list.
```

### Cross-Layer Refactoring

```text
Spawn 4 teammates for the refactor:
- api-contracts: update OpenAPI specs
- backend: refactor service layer
- frontend: update API client
- testing: update contract tests
Each owns separate files, syncs via task list.
```

---

## Troubleshooting

### Teammates Not Appearing

- **In-process**: Check agent panel below prompt (arrow keys to select)
- **Hidden rows**: Idle rows hide 30s after panel idle; send message by name to wake
- **Collapsed rows**: `N idle agents` row → Enter to expand
- **Split panes**: Verify `tmux` or `it2` CLI installed and in PATH
- **Task complexity**: Claude may not spawn team for simple tasks

### Too Many Permission Prompts

Pre-approve common operations in permission settings before spawning.

### Agents Stopping Early

- Check teammate output (select in panel → Enter, or click pane)
- Give additional instructions directly
- Spawn replacement teammate
- Lead may stop early → tell it to keep going

### Orphaned tmux Sessions

```bash
tmux ls
tmux kill-session -t <session-name>
```

---

## Known Limitations

| Limitation | Impact |
|------------|--------|
| No session resumption with in-process teammates | `/resume` and `/rewind` don't restore teammates |
| Task status can lag | Dependent tasks may appear blocked incorrectly |
| Shutdown can be slow | Teammates finish current request before exiting |
| One team per session | Can't create additional named teams |
| No nested teams | Teammates can't spawn their own teammates |
| No background subagents from in-process teammates | `background: true` fails; `run_in_background: true` runs in foreground |
| Lead is fixed | Can't promote teammate to lead |
| Permissions set at spawn | Can change after, but not at spawn time |
| Split panes require tmux/iTerm2 | Not supported in VS Code terminal, Windows Terminal, Ghostty |

---

## Cost Considerations

- **Token usage scales with active teammates** (each has own context window)
- Research, review, new features: usually worthwhile
- Routine tasks: single session more cost-effective
- In-process teammates: 5-min cache TTL by default
- Extend to 1 hour: `subagentPromptCacheTtl: "1h"` in settings (higher API rate)

---

## Related Approaches

| Approach | Best For |
|----------|----------|
| **Subagents** | Lightweight delegation, focused research/verification |
| **Cross-session messaging** | Passing findings between sessions you run yourself |
| **Git worktrees** | Manual parallel sessions without automated coordination |

---

## Quick Reference Commands

```bash
# Enable agent teams
export CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1

# Launch with specific mode
claude --teammate-mode auto

# Check tmux
which tmux

# Kill orphaned tmux session
tmux kill-session -t <name>
```

```text
# In-session commands
/plan                    # Enter plan mode for teammate planning
Ctrl+T                   # Toggle task list (in-process)
Arrow keys + Enter       # Select and message teammate
x                        # Stop selected teammate
```

---

## Decision Flowchart

```
Task requires parallel exploration?
├── No → Use single session or subagents
└── Yes → Can work be split into independent pieces?
    ├── No → Use subagents (lead coordinates)
    └── Yes → Do teammates need to discuss/challenge?
        ├── No → Subagents may suffice
        └── Yes → AGENT TEAMS
            ├── Team size: 3-5
            ├── Display: auto (if tmux/iTerm2) or in-process
            ├── Context: detailed spawn prompts
            ├── Tasks: 5-6 per teammate, separate files
            └── Monitor: check progress, synthesize findings
```

---

## Version Notes

- **v2.1.181+**: Idle row hiding behavior
- **v2.1.186+**: `iterm2` display mode, effort level passed to split-pane teammates
- **v2.1.199+**: Idle rows persist while any agent works; collapsed idle rows; `/model` `/fast` apply to lead only
- **v2.1.207+**: Malformed mailbox entries don't block delivery
- **v2.1.234+**: `teammateDefaultModel` removed; use prompt to specify model
- **v2.1.251+**: Model selection priority changed (prompt > subagent def > env var > lead)
- **v2.1.257+**: `CLAUDE_CODE_SUBAGENT_MODEL_FORCE` support