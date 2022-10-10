package video.mojo.shader.utils

import android.opengl.GLES32
import android.opengl.GLU
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal fun checkGlError() {
    var lastError: Int = GLES32.GL_NO_ERROR
    var error: Int
    while (GLES32.glGetError().also { error = it } != GLES32.GL_NO_ERROR) {
        Log.e("Shader", "glError: " + GLU.gluErrorString(error))
        lastError = error
    }
    if (lastError != GLES32.GL_NO_ERROR) {
        error("glError: " + GLU.gluErrorString(lastError))
    }
}

internal val programIv = IntArray(1)
internal fun glProgramIv(programId: Int, property: Int): Int {
    GLES32.glGetProgramiv(programId, property, programIv, 0)
    return programIv.first()
}

internal val ByteArray.cStringLength: Int
    get() {
        for (i in indices) {
            if (this[i] == '\u0000'.code.toByte()) {
                return i
            }
        }
        return size
    }

internal fun createBuffer(data: FloatArray): FloatBuffer {
    return createBuffer(data.size).put(data).flip() as FloatBuffer
}

internal fun createBuffer(capacity: Int): FloatBuffer {
    val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(capacity * Float.SIZE_BYTES)
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
}

val length = IntArray(1)
val size = IntArray(1)
val type = IntArray(1)
internal fun glActiveAttribute(programId: Int, index: Int): ActiveAttrib {
    val maxLength = glProgramIv(programId, GLES32.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH)
    val nameBytes = ByteArray(maxLength)
    GLES32.glGetActiveAttrib(
        programId,
        index,
        maxLength,
        length,
        /* lengthOffset = */ 0,
        size,
        /* sizeOffset = */ 0,
        type,
        /* typeOffset = */ 0,
        nameBytes,
        /* nameOffset = */ 0
    )
    val name = String(nameBytes, 0, nameBytes.cStringLength)
    return ActiveAttrib(
        length = length.first(),
        size = size.first(),
        type = type.first(),
        name = name,
        location = glAttributeLocation(programId, name)
    )
}

internal fun glAttributeLocation(programId: Int, name: String) =
    GLES32.glGetAttribLocation(programId, name)

internal fun glActiveUniform(programId: Int, index: Int): ActiveUniform {
    val maxLength = glProgramIv(programId, GLES32.GL_ACTIVE_UNIFORM_MAX_LENGTH)
    val nameBytes = ByteArray(maxLength)
    GLES32.glGetActiveUniform(
        programId,
        index,
        maxLength,
        length,
        /* lengthOffset = */ 0,
        size,
        /* sizeOffset = */ 0,
        type,
        /* typeOffset = */ 0,
        nameBytes,
        /* nameOffset = */ 0
    )
    val name = String(nameBytes, 0, nameBytes.cStringLength)
    return ActiveUniform(
        length = length.first(),
        size = size.first(),
        type = type.first(),
        name = name,
        location = glUniformLocation(programId, name)
    )
}

internal fun glUniformLocation(programId: Int, name: String) =
    GLES32.glGetUniformLocation(programId, name)

internal fun bindTexture(textureTarget: Int, texId: Int) {
    GLES32.glBindTexture(textureTarget, texId)
    checkGlError()
    GLES32.glTexParameteri(textureTarget, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
    checkGlError()
    GLES32.glTexParameteri(textureTarget, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
    checkGlError()
    GLES32.glTexParameteri(textureTarget, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
    checkGlError()
    GLES32.glTexParameteri(textureTarget, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)
    checkGlError()
}

internal data class ActiveAttrib(
    val length: Int,
    val size: Int,
    val type: Int,
    val name: String,
    val location: Int
)

internal data class ActiveUniform(
    val length: Int,
    val size: Int,
    val type: Int,
    val name: String,
    val location: Int
)
