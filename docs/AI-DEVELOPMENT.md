# AI-assisted development

HRT Log is a human-directed, AI-implemented project. Product requirements, design decisions,
testing, validation and release decisions are directed by the maintainer, while source code
and portions of the documentation are primarily produced with AI development tools.

## What the maintainer does

- Sets product requirements and priorities, and records them with dates in
  [`REQUIREMENTS.md`](REQUIREMENTS.md).
- Reviews and approves designs before implementation, and decides what is out of scope.
- Uses the app daily, tests builds on real devices and reports problems.
- Decides what is released, when, and under which signature. The official signing key is
  held privately by the maintainer and is never given to CI.
- Makes licensing and governance decisions.

## What AI coding agents do

- Research (product, literature, license status), write design notes, implement code and
  tests, and update the hand-over documents ([`HANDOFF.md`](HANDOFF.md)).
- Their output is checked by unit and regression tests, lint, CI, manifest and permission
  checks, and real-device feedback from the maintainer. Generated code is not accepted on
  trust.

## What this document does not claim

- It does not claim that an AI tool is the legal author or copyright holder of anything.
- It does not claim that the maintainer wrote the code by hand.
- Copyright in AI-assisted output depends on jurisdiction and on the human contribution
  involved; this document is a description of process, not legal advice.

Documents and commits refer to "AI development tools" or "AI coding agents" only; they do
not name specific models or products.

## Rules that apply to AI agents (and to everyone)

- **Design before code.** Significant features start with `docs/design/<feature>.md`,
  reviewed before implementation. Prior art studied for a feature is recorded in
  [`PRIOR_ART.md`](../PRIOR_ART.md).
- **License boundaries when researching other projects.** Reading other projects for ideas
  is allowed; copying is not. GPL/AGPL code is read for requirements only; projects with no
  license or source-available terms are prior art only; MIT/Apache code may legally be
  reused with notices, but independent implementation is preferred. Because the same agent
  may both read and implement, the project does not claim a strict legal clean room.
- No real health data in the repository, tests, screenshots or logs.
- No dose advice, no interpretation of labs, no guessing of import fields.
- Keep the hand-over current: every completed step updates `HANDOFF.md` and is pushed.
