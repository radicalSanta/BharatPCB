
import onnx
from collections import Counter

path = "models/unified_pcb_640.onnx"
model = onnx.load(path, load_external_data=False)

print("IR version:", model.ir_version)
print("Graph nodes:", len(model.graph.node))
print("Initializers:", len(model.graph.initializer))

print("\nInput/output types:")
for value in list(model.graph.input) + list(model.graph.output):
    tensor = value.type.tensor_type
    print(value.name, "element_type =", tensor.elem_type)

print("\nOperators:")
for op, count in sorted(
    Counter(node.op_type for node in model.graph.node).items()
):
    print(f"{op}: {count}")

print("\nExternal tensor references:")
external = []
for tensor in model.graph.initializer:
    if tensor.data_location == onnx.TensorProto.EXTERNAL:
        external.append((tensor.name, tensor.external_data))

print("Count:", len(external))
for name, metadata in external[:10]:
    print(name, [(x.key, x.value) for x in metadata])
