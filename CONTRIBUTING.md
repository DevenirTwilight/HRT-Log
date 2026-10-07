# Contributing to HRT Log

Thank you for your interest. Contributions written by people and contributions made with
AI development tools are both welcome. Code is only one kind of contribution: review,
testing on real devices, accessibility checks, translation, security analysis, bug reports
and documentation all count.

## License of contributions

HRT Log is currently released under the [MIT License](LICENSE). By submitting a
contribution, you agree that it is provided under the project's current license (MIT),
and that you have the right to submit it under that license.

MIT is the current starting point for collaboration, not a promise that the license will
never be discussed again. If people who contribute and maintain the project over the long
term want to reconsider it, that is a governance question to be discussed openly. Versions
already released under MIT stay available under MIT. Once code from several copyright
holders is included, changing its license requires those holders' permission; it cannot
be decided unilaterally.

There is no Contributor License Agreement. The project will not ask contributors to assign
their copyright unless that has been discussed separately and the contributor explicitly
agrees.

## Hard rules

- **Never include real health data** (exports, backups, screenshots, logs) in issues, pull
  requests, tests or fixtures. Use synthetic data only.
- No dose advice and no interpretation of lab results in the app.
- Do not copy code, text, icons, colours, screenshots or translations from other projects.
  In particular:
  - MIT / Apache-2.0 projects: reuse is legally possible with notices, but the project
    prefers study → written design → independent implementation. Any real reuse must be
    listed file by file with its license and notice.
  - GPL / AGPL projects (for example Featherline, Mona): you may read them for ideas and
    requirements; implement independently and do not copy.
  - Projects with no license, or with source-available terms: treat as prior art only.
  - Do not decompile apps or extract their resources.
- Import formats are implemented from documented or user-exported files; do not guess
  field meanings.
- The official app has no ads, analytics, telemetry or INTERNET permission. Changes that
  add network access or tracking will not be accepted into the official app.

## Process

1. For a significant feature, first add a design note under `docs/design/` (problem,
   constraints, data model, UX rationale, prior art and what is not reused, validation).
   See [`PRIOR_ART.md`](PRIOR_ART.md).
2. In the pull request, say which outside material you consulted and what is independent.
   Do not claim "entirely original" if you cannot back it up.
3. Run the checks in the README's Build section for the modules you changed.

Using AI development tools is fine. You remain responsible for what you submit: review it,
test it, and make sure it does not reproduce someone else's code. See
[`docs/AI-DEVELOPMENT.md`](docs/AI-DEVELOPMENT.md).
