# Scope: Milestone 2: StateReduxHardening

## Architecture
- State Management: `core/src/main/kotlin/com/areslib/state`
- Redux Engine: `core/src/main/kotlin/com/areslib/reducer`

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 2 | StateReduxHardening | Deep immutability audit of `RobotState` sub-states. Ensure reducers are pure functions discarding invalid `RobotAction`s safely. | none | IN_PROGRESS |

## Interface Contracts
### State ↔ Reducers
- `RobotState` sub-states must use immutable collections (e.g. `List`, `Map` interfaces or immutable implementations). No `MutableList`, `ArrayList`, `HashMap`, etc.
- Reducers must not throw exceptions on invalid actions, but instead return the previous state.
