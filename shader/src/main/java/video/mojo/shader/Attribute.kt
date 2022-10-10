package video.mojo.shader

import android.opengl.GLES32
import video.mojo.shader.utils.checkGlError
import video.mojo.shader.utils.createBuffer
import video.mojo.shader.utils.glActiveAttribute
import java.nio.FloatBuffer

class Attribute private constructor(
    val name: String,
    val index: Int,
    val location: Int
) {
    private var buffer: FloatBuffer? = null
    private var size = 0

    fun setBuffer(buffer: FloatArray, size: Int) {
        this.buffer = createBuffer(buffer)
        this.size = size
    }

    fun bind() {
        val buffer = requireNotNull(buffer) {
            "setBuffet needs to be called before bind"
        }
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, /* buffer = */ 0)
        GLES32.glVertexAttribPointer(
            location,
            size,
            GLES32.GL_FLOAT,
            /* normalized = */false,
            /* stride = */0,
            buffer
        )
        GLES32.glEnableVertexAttribArray(index)
        checkGlError()
    }

    companion object {
        fun create(programId: Int, index: Int): Attribute {
            val glAttribute = glActiveAttribute(programId, index)

            return Attribute(glAttribute.name, index, glAttribute.location)
        }
    }

}