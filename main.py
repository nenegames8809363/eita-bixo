import glfw
from OpenGL.GL import *

if not glfw.init():
    raise RuntimeError("Falha ao iniciar GLFW")

glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR, 2)
glfw.window_hint(glfw.CONTEXT_VERSION_MINOR, 1)

window = glfw.create_window(
    800, 600,
    "Minecraft 2 - Alpha",
    None, None
)

if not window:
    glfw.terminate()
    raise RuntimeError("Falha ao criar janela")

glfw.make_context_current(window)
glfw.swap_interval(1)

print("OpenGL:", glGetString(GL_VERSION))
print("GPU:", glGetString(GL_RENDERER))

while not glfw.window_should_close(window):

    glClearColor(0.45, 0.70, 0.95, 1.0)
    glClear(GL_COLOR_BUFFER_BIT)

    glfw.swap_buffers(window)
    glfw.poll_events()

glfw.terminate()
