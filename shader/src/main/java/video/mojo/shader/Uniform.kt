package video.mojo.shader

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES32
import android.util.Log
import video.mojo.shader.utils.bindTexture
import video.mojo.shader.utils.checkGlError
import video.mojo.shader.utils.glActiveUniform


class Uniform private constructor(
    val name: String,
    private val location: Int,
    private val type: Type
) {
    private val value: FloatArray = FloatArray(16)

    private var textureId: Int? = null
    private var textureUnitIndex: Int? = null

    fun setSamplerTextureId(textureId: Int, textureUnitIndex: Int) {
        this.textureId = textureId
        this.textureUnitIndex = textureUnitIndex
    }

    fun setFloat(value: Float) {
        this.value[0] = value
    }

    fun setFloats(value: FloatArray) {
        value.copyInto(this.value)
    }

    fun bind() {
        when (type) {
            Type.Float -> {
                GLES32.glUniform1fv(location,  /* count = */1, value,  /* offset = */0)
                checkGlError()
            }
            Type.Vector2 -> {
                GLES32.glUniform2fv(location,  /* count = */1, value,  /* offset = */0)
                checkGlError()
            }
            Type.Vector3 -> {
                GLES32.glUniform3fv(location,  /* count = */1, value,  /* offset = */0)
                checkGlError()
            }
            Type.Vector4 -> {
                GLES32.glUniform3fv(location,  /* count = */1, value,  /* offset = */0)
                checkGlError()
            }
            Type.Matrix3 -> {
                GLES32.glUniformMatrix3fv(
                    location,  /* count = */1,  /* transpose = */false, value,  /* offset = */0
                )
                checkGlError()
            }
            Type.Matrix4 -> {
                GLES32.glUniformMatrix4fv(
                    location,  /* count = */1,  /* transpose = */false, value,  /* offset = */0
                )
                checkGlError()
            }
            Type.Sampler2d -> {
                bindSampler2d(GLES20.GL_TEXTURE_2D)
            }
            Type.Sampler2dOES -> {
                bindSampler2d(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            }
        }
    }

    private fun bindSampler2d(textureTarget: Int) {
        val textureId = requireNotNull(textureId) {
            "No call to setSamplerTexId() before bind."
        }
        val textureUnitIndex = requireNotNull(textureUnitIndex) {
            "No call to setSamplerTexId() before bind."
        }
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0 + textureUnitIndex)
        checkGlError()
        bindTexture(
            textureTarget,
            textureId
        )
        GLES32.glUniform1i(location, textureUnitIndex)
        checkGlError()
    }

    enum class Type {
        Float,
        Vector2,
        Vector3,
        Vector4,
        Matrix3,
        Matrix4,
        Sampler2d,
        Sampler2dOES;

        companion object {
            internal fun fromGl(name: String, glType: Int) = when (glType) {
                GLES32.GL_FLOAT -> Float
                GLES32.GL_FLOAT_VEC2 -> Vector2
                GLES32.GL_FLOAT_VEC3 -> Vector3
                GLES32.GL_FLOAT_VEC4 -> Vector4
                GLES32.GL_FLOAT_MAT3 -> Matrix3
                GLES32.GL_FLOAT_MAT4 -> Matrix4
                GLES32.GL_SAMPLER_2D -> Sampler2d
                GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT,
                GLES11Ext.GL_SAMPLER_EXTERNAL_OES -> Sampler2dOES
                else -> error("Unexpected uniform type: $glType ($name)")
            }
        }
    }

    companion object {
        // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
        private const val GL_SAMPLER_EXTERNAL_2D_Y2Y_EXT = 0x8BE7
        fun create(programId: Int, index: Int): Uniform {
            val glUniform = glActiveUniform(programId, index)
            Log.d("Shader", "Uniform: $glUniform")
            return Uniform(
                name = glUniform.name,
                location = glUniform.location,
                type = Type.fromGl(glUniform.name, glUniform.type)
            )
        }
    }
}