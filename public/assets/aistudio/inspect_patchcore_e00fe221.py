import onnx

path = "models/patchcore_transistor.onnx"

model = onnx.load(path, load_external_data=False)

print("INPUTS:")
for inp in model.graph.input:
    dims = [
        d.dim_value if d.dim_value else d.dim_param
        for d in inp.type.tensor_type.shape.dim
    ]
    print(inp.name, dims)

print("\nOUTPUTS:")
for out in model.graph.output:
    dims = [
        d.dim_value if d.dim_value else d.dim_param
        for d in out.type.tensor_type.shape.dim
    ]
    print(out.name, dims)

print("\nEXTERNAL DATA REFERENCES:")
for initializer in model.graph.initializer:
    if initializer.data_location == onnx.TensorProto.EXTERNAL:
        print(initializer.name, initializer.external_data)
