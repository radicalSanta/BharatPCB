# Bharat AI PCB Inspector
## Greenfield Product, Architecture and Implementation Prompt

**For a new AI coding agent starting from an empty repository**  
**Product:** Bharat AI PCB Inspector  
**Platform:** Native Android application  
**Primary stack:** Kotlin, Jetpack Compose, Material 3, CameraX, ONNX Runtime Android, ExecuTorch Android  
**Design reference:** User-supplied dashboard light/dark mockup and six-swatch palette image  
**Source of truth for existing features:** `https://github.com/radicalSanta/pcbApp`

---

# 0. Your assignment

Build **Bharat AI PCB Inspector** as a complete native Android application from scratch.

Do not merely create static screens or a visual prototype. Build a coherent application with:
- the approved visual identity and screen design,
- actual navigation and working user interactions,
- a PCB image acquisition and validation workflow,
- an on-device computer-vision inference boundary,
- deterministic inspection policy evaluation,
- a separate on-device LLM advisory boundary,
- inspection result visualisation,
- searchable inspection history,
- CSV audit logging and export,
- persistent light/dark/system appearance settings,
- clear model readiness and failure states,
- unit and UI tests.

Use the supplied repository as the product specification for the existing feature set, domain behavior, model contracts and edge cases. You are rebuilding the application architecture cleanly, but you must not silently drop any existing feature or weaken an existing safety/integrity rule.

**Do not copy the previous repository's current visual styling.** It contains the older cyan/industrial style. Rebuild the UI using the new reference images and palette defined below.

**Do not pretend prototype integrations are production integrations.** The current source contains mock repository and mock detector implementations. Replace mock behavior with production adapters only when the actual model files, tokenizer/configuration and runtime APIs are available. Until then, keep a clearly separated demo implementation and show the true model state.

---

# 1. Product description

Bharat AI PCB Inspector is an **offline-first Android PCB optical inspection application**.

An operator captures or selects a PCB image. The app validates the image, prepares it for inference, runs a PCB defect detector, evaluates detections using deterministic inspection policy, presents annotated results and optionally generates a technical advisory from an on-device language model.

The system is composed of three distinct decision layers:

1. **Computer Vision (YOLOv8n / ONNX Runtime):** identifies defect classes, confidence values and bounding-box locations.
2. **Deterministic Inspection Policy:** converts detections and image-quality conditions into the official disposition: PASS, REVIEW, REJECT or INCOMPLETE.
3. **LLM Technical Advisory (Llama 3.2 1B Instruct / ExecuTorch):** explains probable root causes and suggests operator actions. It is advisory only and never controls the official disposition.

The product is designed to run locally on an Android phone. Network access must not be required for the core inspection workflow.

### Product goals

- Inspect PCB images on-device.
- Preserve image provenance, inspection metadata and defect localisation.
- Provide clear, traceable official inspection outcomes.
- Explain likely causes and operator actions without allowing an LLM to override deterministic policy.
- Keep an inspection history that can be searched and exported.
- Store CSV inspection audit records locally and support export/share.
- Make model availability, runtime state, processing and failure states visible and honest.

### Explicit scope

This app is for **PCB inspection only**. Do not add steel inspection, transistor inspection, generic object inspection or unrelated industrial workflows.

---

# 2. Evidence from the existing repository: feature and integration inventory

Use this section as the required product feature list. These are features represented in the current repository. Some are implemented as working app logic, while the detection engine and repository are currently mock/demo implementations. The new app must preserve the product behavior but must label the implementation maturity correctly.

## 2.1 Existing app screens and navigation

The current app contains six main screens:

1. Dashboard
2. New Inspection
3. Inspection Results
4. AI Technical Advisory
5. Inspection History
6. Settings

The four primary navigation destinations are:
- Dashboard
- Inspect
- History
- Settings

Results and Advisory are detail destinations reached from the inspection flow and history. They are not primary bottom-navigation destinations.

Recommended route structure:
- `dashboard`
- `inspect`
- `history`
- `settings`
- `results/{recordId}`
- `advisory/{recordId}`

Use Navigation Compose. Preserve route identity when opening and returning from detail screens. Bottom navigation must reflect the actual current top-level destination.

## 2.2 Dashboard features

The Dashboard presents:
- product identity and the “On-device PCB quality inspection” descriptor,
- an Inspection Overview/summary,
- total inspection count,
- PASS count,
- REVIEW count,
- REJECT count,
- Start New Inspection action,
- Inspection History shortcut,
- Inspection Outcomes distribution,
- Recent Inspections,
- model/runtime readiness.

The current Dashboard ViewModel derives counts from inspection records and model state. Do not hardcode demo metrics in production UI.

The current tests expect a state-driven model readiness section and distinguish demonstration records from real inference records. Keep that distinction in the new implementation.

**Trend chart:** The design reference includes a trend chart. The currently audited `DashboardUiState` does not expose a historical weekly trend series. Therefore:
- do not fabricate weekly data,
- do not generate synthetic values for a production screen,
- add a trend chart only after a real trend aggregation is implemented from inspection history,
- otherwise omit the trend chart or show an honest no-data state.

## 2.3 New Inspection / image acquisition

Current capabilities represented in `InspectScreen` and `InspectViewModel`:

- embedded CameraX preview,
- still-photo capture,
- Android camera permission request,
- fallback to the external system camera app,
- Android Photo Picker/gallery image selection,
- sample PCB preset selection,
- persistent app-owned storage for captured images,
- content URI creation through FileProvider,
- cleanup of empty capture files,
- image decoding and validation,
- EXIF orientation handling,
- image width/height resolution,
- low-clarity risk flag,
- selected source tracking (camera/gallery/sample),
- image metadata display,
- clear/retake selection,
- inspection start gate,
- scanning/progress state,
- stage label and progress value,
- validation error and recovery states.

Keep the workflow:
1. User captures, uploads or selects a sample.
2. The app decodes the image and reads EXIF orientation.
3. The app validates usable dimensions and image integrity.
4. The app evaluates low-clarity risk.
5. The app enables Start Inspection only when the image state is valid and not busy.
6. The app preprocesses the image and submits it to the detection engine.
7. The user receives progress and final navigation to Results.

Do not bypass validation or enable inspection while decoding, scanning, camera preview or invalid state is active.

### Sample PCB presets in the current repository

The existing repository provides three sample presets:

| Sample | Description | Dimensions / orientation | Intended scenario |
|---|---|---|---|
| QFP-100 Main Logic PCB | Simulated multi-defect inspection | 1200 × 900, landscape | `multi_defect` |
| I2C Interface Gold-Pad PCB | Clean solder-joint reference | 1200 × 900, landscape | `clean_board` |
| Industrial Controller Module | Edge mouse-bite and copper check | 900 × 1200, portrait | `mouse_bite` |

These are prototype/sample assets and scenario hints. Do not claim they are live camera observations or real model outputs. Keep sample data visibly marked as demonstration/sample data.

## 2.4 Image preparation and preprocessing

The data model includes `PcbImageInput` and `ImagePreprocessingResult`.

The image pipeline must handle:
- decoded bitmap or a valid image source/reference,
- resolved width and height,
- source type,
- optional sample scenario hint for demo/testing only,
- consistent dimensions and usable bitmap checks,
- resource release/bitmap lifecycle,
- preprocessing success/failure,
- preprocessing latency.

Do not retain Activity or UI Context references in the detection engine. The engine should receive an immutable or safely owned inference input and return structured output.

When implementing the production YOLO pipeline, make preprocessing explicit and testable:
1. Decode the image safely.
2. Apply EXIF orientation.
3. Convert to the expected colour channel order.
4. Resize/letterbox to the model input size.
5. Normalize according to the actual export contract.
6. Build the input tensor with the expected dtype and shape.
7. Run inference.
8. Decode model output and apply confidence filtering and NMS if not already included in the export.
9. Map bounding boxes back to original image coordinates.
10. Release tensors, buffers and native resources safely.

**Critical:** Do not assume input tensor dimensions, output dimensions, class order, normalization, quantization or NMS behavior based only on model family. Read the actual final ONNX export metadata and verify with a known image before implementing the decoder.

## 2.5 Computer-vision detection engine

The existing abstraction is `PcbDetectionEngine`, with:
- `engineLabel`,
- `requiresBitmap`,
- `isDemonstrationEngine`,
- `suspend fun detect(input: PcbImageInput): DetectionOutcome`.

Retain this separation in the new architecture. Do not make Compose screens call ONNX Runtime directly.

### Target detector

- Model family: **YOLOv8n**
- Format: **ONNX**
- Android runtime: **ONNX Runtime Android**
- Execution: on-device/offline
- Output must resolve into structured detections.

The repository's initial model registry identifies the PCB model as YOLOv8n with ONNX Runtime, but explicitly says weights are not bundled in the UI prototype. Therefore the runtime must only become READY after a real model artifact has been loaded and an inference session initialized successfully.

### Expected detection taxonomy

The existing policy implementation defines 11 classes:

| Index | Dataset label | Canonical label | Documented severity |
|---:|---|---|---|
| 0 | `open` | Open Circuit | CRITICAL |
| 1 | `short` | Short Circuit | CRITICAL |
| 2 | `mouse_bite` | Mouse Bite | MEDIUM |
| 3 | `spur` | Spur | MEDIUM |
| 4 | `spurious_copper` | Spurious Copper | MEDIUM |
| 5 | `missing_copper` | Missing Copper | CRITICAL |
| 6 | `copper_burr` | Copper Burr | Undocumented; manual review |
| 7 | `pin_hole` | Pin Hole | MEDIUM |
| 8 | `hole_breakout` | Hole Breakout | Undocumented; manual review |
| 9 | `scratch` | Scratch | Undocumented; manual review |
| 10 | `copper` | Copper | Undocumented; manual review |

Class names must be normalized and resolved explicitly. The policy currently maps by normalized class name and intentionally does not infer severity from class index. Unknown or unsupported class names must not silently map to a known class.

### Detection result fields

A detection should preserve:
- unique detection ID,
- class index from the model,
- raw class name,
- canonical class name where resolved,
- confidence,
- bounding box,
- optional code,
- description,
- severity policy metadata,
- manual-review requirement.

Bounding boxes should support normalized image-space coordinates and pixel coordinates where needed. Preserve original-image coordinate mapping. Do not resize the displayed PCB image independently of its overlay transform.

### Detection outcome

Support at least:
- Success with zero or more detections and stage timings.
- Failure with an explicit error, partial stage timings and no false success.

Track the following latency stages separately where measured:
- image preprocessing,
- YOLO inference,
- output decoding/NMS,
- policy evaluation,
- LLM advisory,
- total wall-clock duration.

Missing/unexecuted timings must remain null/empty. Do not serialize unexecuted stages as `0`.

### Model states

Support honest model states:
- NOT_LOADED
- LOADING
- READY
- ERROR

Do not report READY unless a real runtime session has initialized successfully. A bundled file existing on disk is not itself proof that a session is usable.

## 2.6 Deterministic inspection policy

The existing repository contains `InspectionPolicyEngine`. This policy is a core domain feature and must remain separate from the detector and LLM.

Policy configuration represented in the existing model:
- profile name, defaulting to `IPC-A-610 Class 2`,
- reject on critical defect,
- review on medium defect,
- review on low defect,
- require manual review when zero defects and image clarity is low,
- minimum image width,
- minimum image height.

Use the configured policy. Do not invent standards compliance or silently change policy defaults.

### Existing policy invariants — mandatory

1. **Inference failure guard:** inference failure always produces `INCOMPLETE`, never PASS.
2. **Confidence/severity separation:** model confidence must never alter defect severity or manual-review state.
3. **Class-name safety:** severity is resolved from the normalized class name, not an assumed class index.
4. **Unknown class safety:** unknown or unsupported classes do not silently map to a known class.
5. **Undocumented severity:** copper burr, hole breakout, scratch, copper and unknown classes require manual review because the existing policy has no documented severity for them.
6. **Critical defect escalation:** critical defects lead to REJECT when configured; if critical auto-rejection is disabled, they still cannot become PASS and are escalated to REVIEW for manual sign-off.
7. **Image clarity gate:** low-clarity risk may force REVIEW even with zero localized defects when the policy setting is enabled.
8. **Accurate rationale:** when tolerated low-severity defects exist and the result is PASS, the explanation must report the actual defect count rather than claiming zero defects.
9. **LLM independence:** LLM `suggestedDisposition` is informational only and cannot override the deterministic policy result.
10. **Zero detections is not proof of a good board:** PASS may only be returned when all configured quality and image-quality conditions are satisfied.

### Official outcomes

- PASS
- REVIEW
- REJECT
- INCOMPLETE

Show the official outcome and policy rationale clearly on the Results screen. INCOMPLETE must not be visually conflated with REJECT or PASS.

## 2.7 AI Technical Advisory

The current data model includes `AiAdvisory` fields:
- availability,
- demonstration-data flag,
- severity classification,
- probable root cause,
- recommended operator action,
- relevant standard,
- suggested disposition,
- unavailable reason,
- inference latency.

### Target LLM

- Model target: **Meta Llama 3.2 1B Instruct**
- Android runtime target: **ExecuTorch Android**
- Operation: local/offline
- Purpose: technical explanation and operator guidance.

**Important version distinction:** the audited repository's mock registry currently labels its advisory model as **Llama 3.2 2B Instruct**, while the current project target specified for this rebuild is **Llama 3.2 1B Instruct**. Implement the new target as 1B, but verify the actual model artifact, tokenizer, prompt format, exported ExecuTorch program and runtime compatibility. Do not claim that the old repository already contains a working 1B runtime.

### LLM input and output contract

The LLM may receive:
- official inspection disposition,
- defect classes and counts,
- defect severity and manual-review flags,
- confidence values as supporting context,
- board/image metadata,
- policy rationale,
- validated reference material supplied to the app.

It may generate:
- probable root cause,
- recommended operator action,
- explanation of the observed defect pattern,
- relevant standards only when the source/reference is actually available and verified.

The LLM must not:
- create detections,
- change confidence,
- change bounding boxes,
- invent a standard or clause,
- claim certification/compliance without evidence,
- set or override official disposition,
- mark a failed inspection as PASS.

If the LLM is unavailable, show the official CV/policy result normally and display an advisory-unavailable state. Advisory failure must not fail the inspection result.

### Prompting and grounding

Use a strict structured output schema. Validate the generated response before rendering. Treat the model output as untrusted content. Use only supplied/validated reference documents for standards. No internet access is required for core advisory behavior.

## 2.8 Inspection Results

The Results screen must answer:

1. What is the official inspection disposition?
2. Which defects were detected?
3. Where are those defects on the PCB?

Required elements:
- inspection ID,
- board label,
- timestamp,
- official disposition,
- total defect count,
- image dimensions/source as secondary metadata,
- original/annotated image selector,
- large annotated PCB viewport,
- selectable bounding boxes,
- defect list,
- confidence and severity,
- expandable technical details,
- policy rationale,
- inference failure/zero-detection states,
- navigation to AI Technical Advisory.

### PCB viewport behavior

- Preserve source image aspect ratio.
- Support pan and zoom.
- Support fit/reset zoom.
- Display annotated and original modes.
- Keep bounding boxes aligned with image transformations.
- Selecting a box highlights the corresponding defect row.
- Selecting a defect row highlights the corresponding box.
- Labels may be toggled where the current UX supports them.
- Do not allow overlays to drift when letterboxing, zooming, panning or changing orientation.

Technical coordinates and raw metadata should be secondary/expandable rather than dominating the screen.

## 2.9 Inspection History

The existing History screen supports:
- All, PASS, REVIEW and REJECT filters,
- newest-first sort,
- oldest-first sort,
- most-defects sort,
- highest-severity sort,
- filtered and sorted record list,
- opening a selected record,
- starting a new inspection,
- individual CSV export,
- export-all CSV,
- share/save intents and status feedback.

Preserve these features. History should be backed by structured records, not reconstructed by parsing CSV.

## 2.10 CSV logging and export

The existing `InspectionCsvLogger` is a substantive feature. Rebuild it as a reliable independent audit/export service.

Default file name:
- `bharat_ai_inspection_logs.csv`

Individual record export filename:
- `bharat_ai_<sanitized-inspection-id>.csv`

Existing CSV header contract (preserve these names and order unless a deliberate schema migration is designed and documented):

```csv
inspection_id,timestamp,board_name,overall_disposition,total_defects,defect_id,defect_class,confidence,severity,bbox_x,bbox_y,bbox_width,bbox_height,policy_disposition,inspection_standard,root_cause,operator_action,is_demonstration,image_preprocessing_ms,yolo_inference_ms,output_decoding_nms_ms,policy_evaluation_ms,llm_advisory_ms,total_duration_ms
```

Requirements:
- One row per detection, repeating inspection-level data.
- A zero-detection inspection must still create a valid inspection row with empty detection-specific fields and `total_defects=0`.
- Failed inspections must be recorded with `overall_disposition=INCOMPLETE` and appropriate failure/policy fields.
- Missing/unexecuted timings must be empty fields, not zero.
- Preserve demonstration flag (`is_demonstration`).
- Correct RFC 4180 CSV escaping: commas, quotes, CR/LF and multiline fields.
- Duplicate log protection by inspection ID.
- Non-blocking IO on a background dispatcher.
- Safe file handling and clear error outcomes.
- Export all or one record using Android Storage Access Framework.
- Share exported content through Android intents/FileProvider as appropriate.
- Clear-log behavior should remain consistent with the app's history-clearing semantics.
- Do not make CSV parsing the primary data store.

## 2.11 Settings and appearance

Settings should include:
- Appearance: System Default, Light, Dark.
- Model/runtime readiness.
- Inspection policy settings/profile information.
- storage usage/status,
- CSV export and relevant clear-history action,
- app version/about.

Persist appearance using DataStore Preferences. Apply changes immediately. System Default follows Android system appearance. Update system bar icon appearance correctly.

## 2.12 Offline behavior and external integrations

### Core app must be offline-first

The following must work without an internet connection:
- Dashboard from local state,
- camera capture,
- gallery/photo picker selection,
- sample presets stored locally,
- image validation,
- local preprocessing,
- on-device YOLO inference once the model runtime/artifact is installed,
- deterministic inspection policy,
- on-device LLM advisory once its runtime/artifact is installed,
- annotated results,
- local history,
- local CSV log,
- CSV export/share to local providers,
- theme preferences.

### Android external APIs / platform services represented in current app

- Camera permission.
- CameraX preview and image capture.
- Android system camera fallback via `ActivityResultContracts.TakePicture`.
- Photo Picker via `ActivityResultContracts.PickVisualMedia`.
- FileProvider for app-owned capture images and content sharing.
- DataStore Preferences for theme state.
- Storage Access Framework for exporting files.
- Android share sheet / ACTION_CHOOSER for sharing exports.
- Android lifecycle integration for CameraX preview and resource cleanup.
- Android system bar appearance and edge-to-edge rendering.

These are Android platform integrations, not cloud services. Implement them as native Android capabilities.

### Cloud/external backend status

The audited repository does not establish a working cloud backend for inspection. Core app behavior must not depend on Firebase, Firestore, network APIs or a remote LLM. Dependencies for Firebase, Retrofit, OkHttp and other services exist in the version catalog but are not currently active dependencies in the app's main runtime path. Do not invent a cloud workflow. Do not add cloud sync or authentication unless a separate explicit product requirement is provided.

---

# 3. New visual identity — mandatory

Use the uploaded dashboard comparison image and uploaded palette swatches as the authoritative visual references.

The dashboard image defines:
- light and dark visual hierarchy,
- compact header,
- Inspection Overview anchor,
- paired primary actions,
- compact outcome distribution,
- restrained trend chart treatment when data exists,
- recent inspection rows,
- pill-shaped bottom navigation and selected indicator.

The palette image defines the six base palette colours.

Do not reuse the previous cyan palette or the previous pure grayscale palette. The chosen identity is now a restrained **deep navy / blue-grey / pale grey** engineering palette.

## 3.1 Exact palette

| Role | Hex |
|---|---|
| Deepest navy | `#06141B` |
| Deep navy | `#11212D` |
| Blue-grey | `#253745` |
| Slate | `#4A5C6A` |
| Muted grey | `#9BA8AB` |
| Pale grey | `#CCD0CF` |

Also use `#FFFFFF` for clean light surfaces where appropriate.

### Light theme
- Canvas: near-white/pale cool neutral.
- Main surfaces: white.
- Secondary surfaces: subtle pale grey derived from `#CCD0CF`.
- Primary text: deep navy/near-black.
- Secondary text: slate.
- Dividers: very light neutral.
- Inspection Overview anchor: deep navy/graphite with white text.
- Selected primary action: deep navy with white text.
- Bottom navigation: light/off-white container.
- Selected navigation pill: deep navy with white icon and label.

### Dark theme
- Canvas: `#06141B`.
- Main surfaces: `#11212D`.
- Elevated surfaces: `#253745`.
- Dividers/outlines: restrained values derived from `#4A5C6A`.
- Primary text: white.
- Secondary text: `#CCD0CF`.
- Muted text/icons: `#9BA8AB` where contrast permits.
- Inspection Overview anchor: pale grey/off-white with deep navy text.
- Selected primary action: pale grey/off-white with deep navy text.
- Bottom navigation container: dark navy/blue-grey.
- Selected navigation pill: pale grey/off-white with deep navy icon and label.

### Critical inversion rule

The following three elements share one anchor role and invert together:
1. Inspection Overview.
2. Selected primary action.
3. Selected bottom navigation destination.

Light:
- anchor surface = deep navy
- anchor content = white

Dark:
- anchor surface = pale grey/off-white
- anchor content = deep navy

The bottom navigation container itself follows the overall theme:
- light: off-white container
- dark: dark navy/blue-grey container

Never use a white bottom navigation container in dark mode. Never leave a dark selected pill in dark mode. Selected state must always reflect the actual selected navigation destination.

Use Material 3 semantic colour roles and central semantic theme tokens, not raw hex values scattered across composables.

### Status colours
- PASS: green.
- REVIEW: amber.
- REJECT: red.
- INCOMPLETE: neutral slate.

Status colors are used only for semantics. They must remain legible in both themes and always be paired with labels/icons.

## 3.2 Typography

Use **Inter** as the primary UI typeface, with consistent weights and size roles. Use **JetBrains Mono** only for technical identifiers, coordinates and diagnostic values. These fonts already exist in the old repository, but for a greenfield project either package licensed font files locally or use a well-supported system fallback. Do not reference files from the old repository unless they are explicitly copied into the new project.

Typography rules:
- strong but restrained screen titles,
- readable section headings,
- medium-weight list titles,
- calm body text,
- compact secondary metadata,
- tabular numerals for counts and metrics,
- no decorative/futuristic fonts,
- no excessive all-caps labels or letter spacing,
- no oversized headings that waste vertical space.

## 3.3 Design proportions

- 75% minimalism.
- 15% soft surface depth.
- 10% motion design.

Interpretation:
- Prefer whitespace, alignment, typography and dividers.
- Use rounded tonal surfaces where they aid grouping.
- Avoid a separate card for every section.
- No neon, glow, gradients, glassmorphism or heavy shadows.
- Use motion for state changes and feedback, not decoration.

## 3.4 Material 3 and Android UI rules

Build native Android using Jetpack Compose and Material 3. Do not copy iOS status bars or Cupertino component behavior from a visual reference.

Use:
- Material 3 Scaffold,
- TopAppBar variants,
- NavigationBar / NavigationBarItem on compact screens,
- NavigationRail on suitable larger windows,
- Button, OutlinedButton, TextButton and IconButton,
- SegmentedButton for original/annotated mode,
- FilterChip for history filters,
- ListItem or equivalent structured list rows,
- HorizontalDivider,
- AlertDialog,
- ModalBottomSheet,
- Material 3 progress indicators,
- Material 3 typography, shapes and semantic colours.

Use:
- minimum 48dp touch targets,
- 4dp/8dp spacing rhythm,
- generally 16–20dp screen margins,
- proper edge-to-edge insets,
- readable system bar icons,
- support for font scaling,
- landscape, resizing, multi-window and foldables,
- constrained content width on large screens.

Bottom navigation can be a compact rounded/pill-like container inspired by the reference, but it must be a working accessible Material 3 navigation pattern, not a decorative mockup.

---

# 4. Screen-by-screen product specification

## 4.1 Dashboard

### Header
- Product identity: Bharat AI / PCB Inspector.
- Compact device/PCB icon or product mark.
- Optional notification/profile actions only if they are functional.
- No time-of-day greeting.
- Avoid a tall header.

### Inspection Overview
- Main visual anchor.
- Total inspections.
- PASS / REVIEW / REJECT counts.
- Optional period filter only if real date-range filtering exists.
- Metrics aligned in one region with subtle dividers, not individual cards.

### Primary actions
- Start Inspection.
- Inspection History.
- Use theme anchor contrast for the selected/primary action.
- Preserve minimum target size and responsive layout.

### Inspection Outcomes
- Compact segmented horizontal bar.
- Legend with label, count and percentage.
- Do not print numeric values inside the bar.
- Use real repository data.
- Show an empty state when no records exist.

### Defect Trend
- Render only with real data.
- No fabricated points or numerical labels printed along the line.
- Use day/date labels only.
- If no trend series exists, show a restrained no-data state or omit the section.

### Recent Inspections
- Compact PCB image thumbnail.
- Inspection ID.
- Board name.
- Timestamp.
- Official disposition.
- Defect count if known.
- “See all” opens History.
- Use flat rows and dividers.

### Model Readiness
- PCB Detection: YOLOv8n / ONNX Runtime.
- AI Advisory: Llama 3.2 1B Instruct / ExecuTorch.
- Show the true state: Not loaded, Loading, Ready, Error.
- Never claim Ready without a usable runtime session.
- Distinguish model file availability from initialized session readiness.

## 4.2 New Inspection

Create one coherent image-acquisition workflow, not a collection of isolated cards.

- Compact title: New Inspection.
- Supporting text: acquire and validate PCB image.
- One dominant PCB preview area.
- Empty state: scan/camera icon, “No PCB image selected”, short instruction.
- Capture PCB and Upload PCB as inline equal-weight actions beneath the preview.
- Separate actions with whitespace or a subtle divider; do not give each a large standalone card.
- Full-width Start Inspection action, disabled until image is valid and ready.
- Sample PCB boards as compact list rows with thumbnail, full board name, metadata and trailing affordance.
- When an image is selected, show it in the same preview area with aspect ratio preserved; provide clear/retake controls contextually.
- Keep all camera, gallery, validation, sample selection, progress and error states.

## 4.3 Results

- Compact Results header and inspection identifier.
- Official disposition and defect count visible immediately.
- Large annotated PCB image.
- Original / Annotated segmented selector.
- Zoom, pan, fit/reset.
- Bounding boxes selectable and synchronized with defect list.
- Defects shown as compact rows.
- Class, confidence and severity.
- Expandable technical details for coordinates and metadata.
- Policy rationale available without dominating the top of the screen.
- Clear zero-defect and inference-failure states.
- Advisory entry point.

## 4.4 AI Technical Advisory

A structured technical report, not a chat UI.

- Official result clearly attributed to policy engine.
- Probable Root Cause.
- Recommended Operator Action.
- Relevant Standard, only if supplied/validated.
- Model/runtime/latency as secondary metadata.
- Advisory unavailable state.
- No duplicate copy of the full Results screen.
- LLM cannot set or override official disposition.

## 4.5 History

- Search.
- Filter chips: All / PASS / REVIEW / REJECT.
- Sort: Newest First / Oldest First / Most Defects / Highest Severity.
- Flat record rows with PCB thumbnail, ID, board, time, disposition and defect count.
- Selecting a record opens Results.
- CSV export one record or all.
- Android save/share flows.

## 4.6 Settings

- Appearance: System Default / Light / Dark.
- Model/runtime details.
- Inspection policy configuration.
- Storage status.
- CSV export and clear-history action.
- App version and about.
- Clear-history confirmation.
- Use Material 3 setting/list patterns, not a wall of cards.

---

# 5. Domain architecture

Build a modular architecture with clear dependency direction. Suggested package structure:

```text
app/
  src/main/
    java/<application-package>/
      MainActivity.kt
      app/
        PcbInspectorApplication.kt
        AppContainer.kt
      navigation/
        AppNavGraph.kt
        AppRoutes.kt
      core/
        common/
        dispatchers/
        error/
        image/
        permissions/
        result/
      data/
        local/
          database/
          files/
          preferences/
        repository/
        csv/
      domain/
        model/
        repository/
        usecase/
        policy/
      inference/
        cv/
          PcbDetectionEngine.kt
          OnnxPcbDetectionEngine.kt
          YoloPreprocessor.kt
          YoloOutputDecoder.kt
          NonMaxSuppression.kt
        llm/
          AdvisoryEngine.kt
          ExecuTorchAdvisoryEngine.kt
          AdvisoryPromptBuilder.kt
          AdvisoryOutputParser.kt
      ui/
        theme/
          Color.kt
          Theme.kt
          Type.kt
          Shapes.kt
        components/
        screens/
          dashboard/
          inspect/
          results/
          advisory/
          history/
          settings/
        viewmodel/
```

You may improve this structure, but preserve the separation:
- UI does not run inference.
- ViewModels coordinate screen/workflow state.
- Domain defines official models and policy.
- Data implements persistence and local file/CSV services.
- Inference layer wraps ONNX Runtime and ExecuTorch.
- LLM advisory is independent from policy evaluation.

Use dependency injection or a clear app container. Avoid service locators and global mutable state in domain logic.

### Core domain entities

Create explicit models for:
- `InspectionDisposition`: PASS, REVIEW, REJECT, INCOMPLETE.
- `DefectSeverity`: CRITICAL, MEDIUM, LOW, UNKNOWN/manual review.
- `PcbImageSource`: CAMERA, GALLERY, SAMPLE.
- `BoundingBox`.
- `DetectedDefect`.
- `InspectionRecord`.
- `InspectionPolicyConfig`.
- `PolicyEvaluationResult`.
- `ImageValidationResult`.
- `DetectionOutcome`.
- `ModelInfo` / runtime state.
- `AiAdvisory`.
- `InspectionLatencyMetrics`.
- `SamplePcbPreset`.

Use stable unique IDs. Make timestamps timezone-aware for display and store epoch/Instant data for sorting.

---

# 6. Computer vision implementation plan

## 6.1 Model/runtime integration boundary

Create a `PcbDetectionEngine` interface with a contract equivalent to:

```kotlin
interface PcbDetectionEngine {
    val engineLabel: String
    val requiresBitmap: Boolean
    val isDemonstrationEngine: Boolean

    suspend fun initialize(): ModelInitializationResult
    suspend fun detect(input: PcbImageInput): DetectionOutcome
    suspend fun close()
}
```

The exact signatures may be adjusted to the selected runtime library, but lifecycle and error handling must be explicit.

Create:
- `OnnxPcbDetectionEngine` for the real model.
- `MockPcbDetectionEngine` only for local tests/demo/sample flows.

Do not silently use the mock engine when the real model fails to load. Surface an explicit runtime error and allow demo mode only when the user knowingly selects a sample/demo workflow.

## 6.2 YOLOv8n ONNX contract

Known project target:
- YOLOv8n.
- ONNX Runtime Android.
- 11 PCB classes listed above.

Do not assume the actual exported input/output shape or post-processing behavior. The implementation task must include:
- inspect ONNX model metadata,
- validate tensor names, shapes, dtypes,
- validate class index ordering against the final labels file,
- establish preprocessing normalization and image sizing,
- establish whether NMS is embedded or external,
- run known validation images,
- compare Kotlin output with reference Python inference,
- confirm bounding-box mapping to original image pixels.

A previously discussed export was said to use an input such as `[1,3,416,416]` and output such as `[1,15,3549]`, but this is historical and must **not** be hardcoded without inspecting the final artifact. Treat this only as a hypothesis to verify.

## 6.3 Inference output

Each detection must preserve:
- model class index,
- raw class name,
- confidence,
- box coordinates,
- generated or model-supplied detection ID.

Use a typed failure for:
- model missing,
- session initialization failure,
- invalid input tensor,
- runtime execution failure,
- output shape mismatch,
- decoder failure,
- resource allocation failure.

Every failure path must be reflected in the final record as INCOMPLETE, never PASS.

---

# 7. Deterministic policy implementation plan

Create a standalone, pure Kotlin policy module with unit tests. Do not place policy logic inside Compose, the detector or the LLM.

### Inputs
- inference success/failure,
- detected defect list,
- image clarity risk,
- configured policy settings.

### Outputs
- official disposition,
- detailed rationale,
- resolved defect severity/manual-review status.

### Mandatory invariants
- inference failure => INCOMPLETE.
- confidence never changes severity.
- normalized class name, not model class index, determines documented severity.
- unknown/undocumented classes require manual REVIEW.
- critical defects follow configured critical rejection policy; disabling auto-reject cannot permit PASS.
- low image clarity may force REVIEW even with no detections.
- zero detections do not automatically produce PASS.
- LLM result cannot change policy disposition.

Write unit tests for every invariant and for combinations of defects/policy settings.

---

# 8. LLM advisory implementation plan

Create a separate `AdvisoryEngine` interface with:
- initialization/readiness,
- generation request,
- structured response,
- error/unavailable result,
- close/release.

Target:
- Meta Llama 3.2 1B Instruct.
- ExecuTorch Android.
- Offline inference.

Do not claim working integration until the real `.pte`/ExecuTorch program, tokenizer and required configuration assets are available and a device test succeeds.

### Advisory request should include only grounded facts
- official policy outcome,
- class names/counts,
- severity/manual-review information,
- confidence as supporting signal,
- image/board context,
- policy rationale,
- approved reference excerpts if any.

### Required structured output
- probable root cause,
- recommended operator action,
- relevant standard and clause only if grounded,
- limitations/uncertainty,
- optional advisory latency.

Validate JSON/schema and reject invalid or unsupported output. Make absence of standards references explicit instead of inventing one.

The advisory must not:
- change official disposition,
- add or remove detections,
- change severity,
- claim compliance/certification without supplied evidence.

If unavailable, the app still shows Results normally.

---

# 9. Local persistence, history and CSV

## 9.1 Structured local storage

Use Room or another robust local structured storage option for:
- inspection records,
- defects,
- inspection metadata,
- policy result,
- advisory fields,
- image URI/file path,
- latency metrics,
- demo/real record flag.

Do not make CSV the main database.

Use transactions to persist the inspection record and its detections consistently. Preserve captured images in app-owned storage with lifecycle-safe cleanup. Do not delete an image still referenced by a historical inspection record.

## 9.2 CSV audit/export

Use the exact schema documented in Section 2.10. Keep CSV code in its own service/repository.

Requirements:
- one row per detection,
- one row for zero defects,
- explicit INCOMPLETE/failure records,
- RFC 4180 escaping,
- duplicate protection by inspection ID,
- IO dispatcher/background operations,
- safe append/rewrite,
- individual and all-record export,
- Android Storage Access Framework,
- share sheet,
- empty missing timings,
- demonstration flag.

Test:
- comma/quote/newline escaping,
- zero-detection inspection,
- multi-detection inspection,
- duplicate logging,
- failed inference,
- partial latency metrics,
- export one/all,
- URI write failure.

---

# 10. Security, privacy and reliability

- Core inspection runs locally.
- Do not send PCB images to a remote service.
- Do not upload images or inspection records by default.
- Request camera permission only when needed.
- Use Photo Picker rather than broad storage permissions.
- Use FileProvider/content URIs for sharing.
- Keep model files in a suitable app-private location or packaged asset mechanism.
- Avoid logging raw images, credentials, tokens or sensitive user content.
- Handle large image decoding without blocking the main thread.
- Release bitmaps, ONNX tensors, sessions and ExecuTorch resources correctly.
- Avoid holding an Activity or View context in repositories/inference engines.
- Make cancellation and lifecycle cleanup explicit.
- Prevent duplicate inspection submissions.
- Preserve partial latency metrics if a later stage fails.
- Use typed errors and actionable operator messages.

---

# 11. Target device and performance

Primary target device:
- Nothing Phone (2a).
- MediaTek Dimensity 7200 Pro.
- 8 GB physical RAM.

Design and profile for an Android phone with constrained memory and sustained thermal limits.

Performance guidance:
- Decode large images efficiently.
- Use sampled decoding for validation previews.
- Avoid holding multiple full-resolution bitmap copies.
- Release native memory promptly.
- Keep UI responsive while inference runs.
- Use a background dispatcher for image processing, model execution and CSV IO.
- Show stage progress only when there is measurable/real progress.
- Avoid inventing percentage progress if runtime does not expose it.
- Benchmark actual model latency on target device.
- Handle out-of-memory and native runtime errors safely.
- Consider thermal throttling and repeated inference.

Do not make unverified performance promises.

---

# 12. Testing plan

Build tests from the start, not as a final afterthought.

## Unit tests
- class-name normalization and taxonomy mapping,
- severity mapping,
- unknown class handling,
- policy outcome matrix,
- inference failure => INCOMPLETE,
- low-clarity review gate,
- confidence/severity separation,
- bounding-box transforms,
- image validation and EXIF orientation,
- preprocessing tensor contract,
- output decoder and NMS,
- CSV serialization/escaping/deduplication,
- repository mapping,
- advisory output validation.

## Instrumented / Robolectric / Compose tests
- app navigation,
- bottom-nav selected state,
- theme mode persistence,
- Dashboard states (empty/demo/real/error),
- camera permission path,
- gallery selection,
- sample preset selection,
- invalid image state,
- start-inspection gating,
- progress and completion,
- Results image modes,
- defect selection and box synchronization,
- advisory unavailable state,
- History filters/sort/export,
- Settings theme and clear-history behavior,
- font scaling and small/large widths.

## Runtime integration tests
- ONNX session can initialize from the actual bundled/downloaded local artifact.
- Input tensor contract matches the model.
- Known PCB test image produces plausible structured outputs.
- Kotlin inference output matches reference output within defined tolerances.
- ExecuTorch model loads and generates schema-valid advisory output.
- Model absence is represented as NOT_LOADED/ERROR, never READY.

Do not weaken safety tests to make UI tests pass.

---

# 13. Suggested implementation phases

## Phase 1 — Product and domain foundation
- Define domain models.
- Implement inspection policy and tests.
- Define repository interfaces.
- Define detection/advisory engine interfaces.
- Define error and result types.

## Phase 2 — Local infrastructure
- CameraX and Photo Picker.
- Persistent capture storage/FileProvider.
- Image validation and EXIF.
- Local structured persistence.
- CSV logger/export.
- Theme preference storage.

## Phase 3 — Inference
- Integrate actual YOLOv8n ONNX artifact.
- Verify metadata/preprocessing/output.
- Build decoder/NMS/box mapping.
- Test on known PCB images.
- Integrate Llama 3.2 1B ExecuTorch artifact and tokenizer.
- Validate structured advisory output.

If artifacts are not yet available, provide explicit mock/demo adapters and keep the production adapter unready. Do not fabricate real runtime success.

## Phase 4 — UI foundation
- Theme palette and semantic tokens.
- Typography and shapes.
- Material 3 Scaffold and navigation.
- Shared action buttons, status components, list rows, dialogs.
- Adaptive layout foundations.

## Phase 5 — Screens
1. Dashboard.
2. New Inspection.
3. Results.
4. AI Technical Advisory.
5. History.
6. Settings.

## Phase 6 — Integration and verification
- Connect ViewModels to real repositories.
- Verify camera/gallery-to-results flow.
- Verify official policy and advisory separation.
- Verify history and CSV.
- Verify both themes on all screens.
- Profile target device.
- Run complete test suite.

---

# 14. Deliverables

Create:
1. Complete Android Studio project.
2. Kotlin/Compose application code.
3. Material 3 light and dark themes using the approved palette.
4. Six fully implemented screens.
5. Working navigation.
6. CameraX and gallery acquisition.
7. Image validation and preprocessing.
8. Detection-engine abstraction and production ONNX adapter.
9. Deterministic inspection policy with tests.
10. ExecuTorch advisory abstraction and production adapter.
11. Local persistence and history.
12. CSV logger/export/share.
13. Model readiness and failure states.
14. Unit, Compose and integration tests.
15. README with build instructions, model asset placement, architecture, testing and known limitations.
16. Clear distinction between functioning production adapters and demo/mock adapters.

---

# 15. Final acceptance criteria

The application is not complete merely because six screens render.

It must satisfy:

### Product
- [ ] PCB-only scope.
- [ ] Dashboard, Inspect, Results, Advisory, History and Settings.
- [ ] Offline-first workflow.
- [ ] CameraX, system camera fallback and gallery acquisition.
- [ ] Sample PCB selection.
- [ ] Image validation and EXIF orientation.
- [ ] YOLO detection contract.
- [ ] Deterministic policy output.
- [ ] LLM advisory independent of official result.
- [ ] Annotated image and synchronized defect selection.
- [ ] Search/filter/sort history.
- [ ] CSV logs and export.
- [ ] Persisted System/Light/Dark settings.

### Safety and truthfulness
- [ ] Inference failure never produces PASS.
- [ ] Zero detections do not automatically produce PASS.
- [ ] Confidence never controls severity.
- [ ] Unknown/undocumented classes require manual review.
- [ ] LLM cannot override deterministic policy.
- [ ] Model status READY only after a real session is initialized.
- [ ] Mock/demo results are clearly marked.
- [ ] No fake latency, trends, model states or standards references.

### Design
- [ ] Uses the supplied navy/blue-grey/pale-grey palette.
- [ ] No cyan or saturated blue accent.
- [ ] Uses Inter consistently; JetBrains Mono only for technical metadata.
- [ ] Native Android Material 3 components.
- [ ] Minimal, calm, professional engineering aesthetic.
- [ ] Consistent light/dark hierarchy across all six screens.
- [ ] Inspection Overview, primary selected action and selected navigation destination invert as specified.
- [ ] Bottom nav container follows theme; selected pill inverts.
- [ ] No card for every section.
- [ ] New Inspection capture/upload controls are one compact action row.
- [ ] Responsive and accessible.

### Engineering
- [ ] Clean separation of UI, domain, data and inference.
- [ ] No inference on the main thread.
- [ ] Correct resource lifecycle.
- [ ] Structured local persistence.
- [ ] CSV is separate from source-of-truth storage.
- [ ] Tests cover policy, preprocessing, inference adapters, CSV, navigation and themes.
- [ ] App builds and installs.
- [ ] README documents exact model artifact/configuration requirements.

---

# 16. Instructions to the coding agent

You are starting a new project, not applying a patch to the old repository. You may choose a clean package/module structure and improve the architecture.

However:
- use the existing repository as the functional specification,
- preserve every substantive user-facing capability listed in this document,
- preserve all policy invariants,
- preserve the CSV contract,
- do not represent mock implementations as production integrations,
- use the supplied images as the design authority,
- do not invent unsupported features,
- do not silently remove complex functionality to simplify the build,
- do not deliver a static Figma-like prototype in place of a working Android app.

Start by creating a short architecture plan and a file/module skeleton, then implement in the phases above. Maintain a buildable project at each phase. If a model artifact or tokenizer is missing, implement the interface and a clearly marked unavailable/demo state, document the exact missing asset, and continue with the rest of the application.

**The final product must be a working offline-first PCB inspection Android application with a polished native Material 3 interface—not just a UI mockup.**
