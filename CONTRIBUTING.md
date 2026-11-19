# Contributing

## Branches (GitFlow)

- `main`: version deployed on Render. Only receives pull requests from `develop`.
- `develop`: integration branch of the sprint.
- `feature/<name>`: one branch per member or task, created from `develop`.

Every change goes to `develop` through a pull request reviewed by the leader of the aspect.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/): `feat`, `fix`, `test`, `docs`, `ci`, `chore`, `refactor`. Write the user story in the body when it applies (for example `TS03`).

## Before opening a pull request

```bash
mvn verify
```

All the tests must pass.
