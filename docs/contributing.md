# Contributing to Cadence

## Process

1. **Jira first.** Every change has a Jira ticket. Use the key in all names below.
2. **Branch from `develop`.** Name the branch `feature-STEPW-<n>`.
3. **Commit.** Use the format `STEPW-<n> #comment <summary>`, and end the message
   with the trailer `Co-Authored-By: <name> <email>` for any AI or human co-author.
4. **Test.** Run locally before pushing:
   - `./gradlew :app:testDebugUnitTest`
   - `./gradlew :app:lintDebug :app:assembleDebug`
   (On Windows, use `gradlew.bat`.)
5. **Pull request into `develop`.** Title `STEPW-<n>: <summary>`. Describe what
   changed, how it was tested, and link the Jira ticket.
6. **CI must be green** on Ubuntu, Windows and macOS before merge.
7. **Jira after merge.** Comment with the summary and PR link, then move the
   ticket to Done.

Protected branches (`develop`, `qa`, `staging`, `release`, `master`) change only
through pull requests. Promotion runs `develop` -> `qa` -> `staging` ->
`release` -> `master`, one PR per step, merged with a merge commit.

## Quality rules

- **Tests are required** for every behaviour and every bug fix. A bug fix
  includes a regression test that fails before the fix.
- **Keep docs in step.** A user-visible change updates `CHANGELOG.md` under
  `## Unreleased` and `docs/user-guide.md`.
- **Process changes** update this file.
- Review your own diff before committing: no debug code, no secrets, no unused
  code.

## Secrets

Never commit keystores, `keystore.properties`, tokens or `.env` files. The
`.gitignore` covers the common cases; check `git status` before every commit.

## Branches

- `develop`: integration branch for all feature work.
- `qa`, `staging`, `release`, `master`: promotion stages, updated only by PR.
- `feature-STEPW-<n>`: one per Jira ticket. Delete after it merges.
