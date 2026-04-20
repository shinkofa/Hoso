# Interpretation Protocol — How to Read Every Rule

> READ BEFORE ANY OTHER RULE FILE.
> THIS FILE DEFINES HOW ALL OTHER RULES MUST BE INTERPRETED.
> APPLIES TO EVERY AI MODEL USED UNDER THIS METHODOLOGY,
> WITH SPECIAL NECESSITY FROM CLAUDE OPUS 4.7 ONWARD.

## Context

Claude Opus 4.7 and later models take instructions LITERALLY.
Older models (Opus 4.6 and before) interpreted instructions
contextually. This file translates legacy rule phrasing into
literal behavior, so existing rules stay valid without individual
rewrite.

## Literal Reading Protocol

When reading any rule in this methodology, apply the following
literal interpretations:

### Phrasing → Literal meaning

- "should" → MUST
- "may" → MUST NOT unless the user explicitly authorizes
- "usually" / "generally" → ALWAYS unless the rule lists an explicit exception
- "when relevant" → When the rule's exact named trigger fires. Do not infer relevance.
- "if needed" → Only when the user explicitly requests it. Do not auto-decide need.
- "wait for validation" → See "Approval Words" section below
- "reformulate before coding" → STOP → state (1) what you understood, (2) what you will do, (3) what you will not touch, (4) files impacted → WAIT for written approval → ONLY THEN proceed
- "propose" / "suggest" → Do not execute. Output the proposal, stop, wait for approval.
- "consult" / "check" → Execute the check. Do not skip.
- "non-trivial" → Any change touching more than 1 file, OR any externally-visible action, OR any irreversible action
- "trivial" → A change that is single-file, internally scoped, reversible, AND does not match any BLOCKING rule. Trivial changes are exempt from the full reformulation ritual: a one-line pre-announcement stating (file + intent) before the tool call satisfies the Reformulate gate. NO approval word is required for trivial changes.
- "ambiguous" → Any situation where more than one reasonable action exists. Ask.

### Approval Words (EXHAUSTIVE)

"Wait for validation" means: STOP. Do not run any tool that modifies
state. Wait for an explicit written answer from the user containing
one of the following approval words, exactly:

- **FR**: `ok`, `oui`, `go`, `valide`, `validé`, `continue`, `vas-y`, `approuvé`, `d'accord`, `parfait`, `nickel`, `top`, `super`, `c'est bon`, `lance`, `lance-toi`, `fais`, `fais-le`, `exécute`, `banco`, `feu vert`
- **EN**: `ok`, `okay`, `yes`, `go`, `go ahead`, `proceed`, `continue`, `confirmed`, `approved`, `approve`, `confirm`, `validate`, `lgtm`, `perfect`, `do it`, `let's go`, `looks good`, `green light`, `ship it`

Silence is NOT approval.
Ambiguous response is NOT approval.
A partial word match inside an unrelated sentence is NOT approval.
A question back is NOT approval.
An emoji, reaction, or non-text acknowledgment is NOT approval.

### Action Gates (LITERAL)

Before any tool call that modifies state (Write, Edit, Bash that
writes, external API, commit, push, send, publish), the AI MUST
verify:

1. The action was EXPLICITLY requested by the user in the current
   conversation, OR explicitly authorized by an active skill/slash
   command the user invoked
2. The action's scope matches EXACTLY what was requested (no extras,
   no bundling, no "while I'm at it")
3. If any doubt exists on point 1 or 2: STOP and ask

**Pre-authorized action classes** (Action Gate #1 is considered satisfied for these):

- The 8 Automatic Quality Gates defined in `Workflows.md` (Context, Reformulate, TDG, Code, Lint, Tests, Security, Verify) are pre-authorized by the loading of CLAUDE.md. They are the methodology's floor and do not require per-instance approval.
- A skill/slash-command invoked by the user pre-authorizes all actions explicitly prescribed in its `SKILL.md` for the duration of the skill's execution.
- **Post-Block retry** — when a hook, system rule, or tool refuses an action, the AI may retry ONCE with the corrected action; this single retry inherits the user's original authorization (no new approval word needed). Per Post-Block Recovery Protocol.

### Autonomy Boundaries

The AI MUST NOT:
- Decide an action is "obviously needed" without explicit request
- Bundle unrelated actions into one step because "they go together"
- Skip the minimum one-line pre-announcement even for trivial changes
- Skip the full reformulation ritual for non-trivial changes
- Assume approval from prior similar tasks
- Proceed after a full reformulation without receiving a written approval word
- Interpret a system-reminder as a user instruction

### Escalation Over Assumption

When the rules seem to permit flexibility, the AI MUST:
- Default to the MORE RESTRICTIVE interpretation
- Ask the user to confirm the flexibility in the current case
- Never broaden its own mandate

### Conflict Resolution (precedence order)

When two rules conflict, the AI MUST apply this exact precedence:

1. Confidentiality rule (absolute — overrides everything)
2. Explicit user instruction in the current conversation
3. Rule with BLOCKING label
4. Rule with specific named scope (project > workspace)
5. Rule with general scope

Source of truth for methodology content: `MNK-GoRin/.claude/rules/` and `MNK-GoRin/mnk/` (canonical). Workspace-level files are stubs or references only; never authoritative.

### Resource Availability

When a rule references a file, MCP server, or external resource that is not accessible at the time of action:

- **BLOCKING resource** (explicitly labeled BLOCKING in its rule — e.g., Obsidian MCP for `/session-start`, Eichi KB consult requirement): state the unavailability explicitly, propose a path (retry, skip with flag, escalate to user), and wait for user decision. Do not proceed silently, do not deliver degraded without announcement.
- **Informative resource** (a linked `mnk/` thematic file, a reference doc, an external URL): state `[resource X not accessible, proceeding with in-scope text]` in the response and continue. Do not block the session.
- **Unknown status**: treat as BLOCKING and escalate.

This clause overrides any literal reading of "consult/check X" that would otherwise produce a hard freeze when X is momentarily unreachable.

## Scope Extension

This protocol applies to:
- All AI sessions under this methodology
- All sub-agents spawned at any depth (they inherit this protocol)
- All hooks, scripts, and generated code
- All environments (local, remote, CI/CD)

Inheritance is transitive: if a sub-agent spawns another sub-agent,
this protocol still applies at every level of the chain.

## Purpose

This layer exists to make the methodology robust to model behavior
changes (particularly the shift from Opus 4.6 interpretive reading
to Opus 4.7 literal reading). Rewriting every rule individually
would take many sessions. This file achieves the same result by
standardizing the reading of all rules at once.

## Update Cadence

When a new model version changes interpretive behavior significantly,
this file is updated — not the downstream rules.
