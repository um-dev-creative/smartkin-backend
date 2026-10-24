# Achievement catalog (for the product team)

Migration V5 created three **placeholder** achievements so the mechanism could be tested. The real catalog is a product decision. This page explains how achievements work, what a rule can express, and proposes a catalog to react to. Nothing here is applied automatically.

## How an achievement is earned

An achievement is a row of `smartkin.achievements` with a JSON rule in `requirements`. After a child passes a quiz, completes an activity or earns points, the service counts what the rule asks for and awards every achievement whose count reached its `threshold`, once per child. Awarding books the achievement's `points_value`, notifies the child and may in turn unlock a `POINTS_REACHED` achievement.

| Rule `type` | Counts | Example |
| --- | --- | --- |
| `QUIZZES_PASSED` | distinct lessons whose quiz the child passed | `{"type":"QUIZZES_PASSED","threshold":3}` |
| `ACTIVITIES_COMPLETED` | distinct activities the child completed | `{"type":"ACTIVITIES_COMPLETED","threshold":5}` |
| `POINTS_REACHED` | the child's point balance | `{"type":"POINTS_REACHED","threshold":100}` |

Limits to keep in mind when designing: a rule is a **single** count with a threshold (no "pass a quiz with 100%" or "three days in a row" yet), and repeating the same lesson does not count twice. A rule that is not valid JSON, has an unknown type or a threshold below 1 is ignored (logged, never awarded).

## Proposal

`docs/db/achievements_catalog_proposal.sql` has eight achievements built only from those three rules, with text in Spanish for children:

| Name | Rule | Points |
| --- | --- | --- |
| Primer quiz aprobado | 1 quiz passed | 10 |
| Explorador | 3 activities completed | 25 |
| Cien puntos | 100 points | 20 |
| Detective de contraseñas | 3 quizzes passed | 30 |
| Guardián digital | 5 quizzes passed | 50 |
| Constante | 5 activities completed | 40 |
| Experto en seguridad | 10 activities completed | 75 |
| Quinientos puntos | 500 points | 60 |

The names that mention passwords or security are suggestions: rename them to match the topics the catalog really has. The first three keep the ids of the V5 placeholders, so running the script **updates** them instead of duplicating.

## Applying it

1. Edit `docs/db/achievements_catalog_proposal.sql` (text, icon, points, thresholds). Keep the JSON rule valid: `AchievementCatalogProposalTest` checks the file in the repository build.
2. Run it once against the QA database: `psql ... -f docs/db/achievements_catalog_proposal.sql`. It runs in one transaction and can be repeated.
3. The `icon` is stored as text, but the current frontend screens do not show it yet (they show the name, description and points), so any value is safe today; agree on a naming scheme before the frontend starts drawing icons.
4. The service counts totals, so a child who already meets a new rule earns it the next time they pass a quiz, complete an activity or earn points, not at the moment the row is added.

To retire an achievement, delete its row only if nobody earned it; otherwise leave it (children keep what they won).
