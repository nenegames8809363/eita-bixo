import glfw
import os

print("DISPLAY:", os.environ.get("DISPLAY"))

def error_callback(code, description):
    print(f"GLFW ERROR {code}: {description}")

glfw.set_error_callback(error_callback)

print("Inicializando GLFW...")

result = glfw.init()

print("Resultado:", result)

if result:
    print("GLFW inicializado!")
    glfw.terminate()
else:
    print("Falha na inicialização!")
