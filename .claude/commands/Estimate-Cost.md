# /estimate-cost

Estimate Claude Code credit cost for a task.

## Steps
1. Analyze the task scope (files to read, write, tests to run)
2. Estimate token usage: input (context) + output (code/responses)
3. Calculate approximate cost based on model:
   - Opus: ~$15/MTok input, ~$75/MTok output
   - Sonnet: ~$3/MTok input, ~$15/MTok output
   - Haiku: ~$0.25/MTok input, ~$1.25/MTok output
4. Present estimate with breakdown
5. Suggest optimizations (use Sonnet subagents, limit context)
