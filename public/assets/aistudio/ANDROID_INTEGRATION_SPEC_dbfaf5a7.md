# Nothing Phone (2a) Android Integration Contract

## 1. Supervised PCB / Surface Model (models/unified_pcb_640.onnx)
- Input Tensor: float32[1, 3, 640, 640], RGB channel order, normalized [0.0, 1.0], letterbox-padded with RGB (114, 114, 114).
- Output Tensor: float32[1, 15, 8400] -> transpose to [8400, 15] ([cx, cy, w, h, score_0..score_10]).
- Thresholds: conf_thresh = 0.33, iou_thresh = 0.45.
- Border-Crop Guard: Ignore detections touching within 8px of image edges when conf < 0.50 and area_pct < 2.5%.
- 11 Class IDs: 0=open, 1=short, 2=mouse_bite, 3=spur, 4=spurious_copper, 5=missing_copper, 6=copper_burr, 7=pin_hole, 8=hole_breakout, 9=scratch, 10=copper.
- Populated Green PCB Sub-Classifier (when cid == 9 and green solder-mask pixels >= 15%):
  * If ROI dark/charred pixels (V < 65) >= 16% -> remap to 5 (missing_copper)
  * Else if ROI bright metallic solder pixels (V > 165 and S < 75) >= 25% -> remap to 1 (short)
  * Else keep 9 (scratch)

## 2. Unsupervised Transistor Anomaly Model (models/patchcore_transistor.onnx)
- Input Tensor: float32[2, 3, 256, 256] (static batch size 2 — duplicate the single 256x256 frame across batch index 0 and 1).
- CRITICAL Normalization: RGB [0.0, 1.0] ONLY. Do NOT subtract ImageNet mean/std (baked inside the ONNX graph).
- Outputs: outputs[0][0] = raw anomaly score (is_anomalous = raw_score >= 39.5), outputs[2][0, 0] = 256x256 spatial anomaly heatmap.

## 3. On-Device ExecuTorch Llama Advisory (models/smollm2_360m/model.pte)
- Runtime: Meta ExecuTorch Android AAR (org.pytorch:executorch-android) loading model.pte + tokenizer.json.
- Prompt Format: ChatML (<|im_start|>system ... <|im_end|>). See reference_python/advisory_engine.py for the full 16-class IPC-A-610 / ISO-9443 SOP dictionary and JSON fallback parser.
