# Change: Hide Control Internals

## Why
Standard JavaFX controls like `TextField`, `Button`, and `Label` expose their internal skin implementation (e.g., `Pane`, `Text`, `Path`) in the scene graph snapshot. This adds noise and consumes tokens without providing value to the LLM, which usually cares about the control itself and its data, not its internal composition.

## What Changes
- Modify `SceneGraphSnapshotter` to treat specific standard controls as "leaves" in the snapshot tree.
- Prevent recursion into children for:
  - `TextInputControl` (TextField, TextArea, PasswordField)
  - `Labeled` (Button, Label, CheckBox, RadioButton, ToggleButton)
  - `Slider`, `ProgressBar`, `ProgressIndicator`, `ScrollBar`, `Separator`
  - `ComboBox`, `ChoiceBox`, `Spinner`, `ColorPicker`, `DatePicker`
- This behavior should be the default, but potentially configurable via `SnapshotOptions` if deep inspection is needed.

## Impact
- Affected specs: `mcp-ui`
- Affected code: `SceneGraphSnapshotter.java`
- Snapshots will be significantly more compact for forms and standard UIs.
