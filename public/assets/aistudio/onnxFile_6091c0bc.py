import onnx

models = [
    "models/unified_pcb_640.onnx",
    "models/unified_pcb_416_int8.onnx",
    "models/patchcore_transistor.onnx",
]

for path in models:
    model = onnx.load(path)
    print(f"\n===== {path} =====")

    for inp in model.graph.input:
        dims = [d.dim_value if d.dim_value else d.dim_param
                for d in inp.type.tensor_type.shape.dim]
        print("INPUT:", inp.name, dims)

    for out in model.graph.output:
        dims = [d.dim_value if d.dim_value else d.dim_param
                for d in out.type.tensor_type.shape.dim]
        print("OUTPUT:", out.name, dims)