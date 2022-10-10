package video.mojo.shader

import android.util.Log
import video.mojo.shader.utils.checkGlError
import video.mojo.shader.utils.glAttributeLocation
import video.mojo.shader.utils.glProgramIv
import video.mojo.shader.utils.glUniformLocation
import android.opengl.GLES32 as GL


class GlProgram(
    vertexShaderGlsl: String,
    fragmentShaderGlsl: String
) {
    private val programId: Int = GL.glCreateProgram()
    private val attributes = mutableListOf<Attribute>()
    private val attributeByName: Map<String, Attribute>
        get() = attributes.associateBy { it.name }

    private val uniforms = mutableListOf<Uniform>()
    private val uniformByName: Map<String, Uniform>
        get() = uniforms.associateBy { it.name }

    init {
        checkGlError()

        // add vertex and fragment shader
        addShader(programId, GL.GL_VERTEX_SHADER, vertexShaderGlsl)
        addShader(programId, GL.GL_FRAGMENT_SHADER, fragmentShaderGlsl)

        GL.glLinkProgram(programId)
        if (!isLinked(programId)) {
            error("Unable to link shader program: \n${GL.glGetProgramInfoLog(programId)}")
        }
        GL.glUseProgram(programId)

        val attributeCount = getActiveAttributes(programId)
        repeat(attributeCount) { index ->
            val attribute = Attribute.create(programId = programId, index = index)
            attributes.add(attribute)

        }

        val uniformsCount = getActiveUniforms(programId)
        repeat(uniformsCount) { index ->
            val uniform = Uniform.create(programId, index)
            uniforms.add(uniform)
        }
        checkGlError()
        Log.d("Shader", "${uniforms.map { it.name }}")
        Log.d("Shader", "${attributes.map { it.name }}")
    }

    /**
     * Get the location of a [Attribute]
     */
    fun getAttributeLocation(name: String) = glAttributeLocation(programId, name)

    /**
     * Get the location of a [Uniform]
     */
    fun getUniformLocation(name: String) = glUniformLocation(programId, name)

    /**
     * Uses the program.
     *
     *
     * Call this in the rendering loop to switch between different programs.
     */
    fun use() {
        GL.glUseProgram(programId)
        checkGlError()
    }

    /** Deletes the program. Deleted programs cannot be used again.  */
    fun delete() {
        GL.glDeleteProgram(programId)
        checkGlError()
    }

    /**
     * Returns the location of an [Attribute], which has been enabled as a vertex attribute
     * array.
     */
    fun getAttributeArrayLocationAndEnable(attributeName: String): Int {
        val location = getAttributeLocation(attributeName)
        GL.glEnableVertexAttribArray(location)
        checkGlError()
        return location
    }

    /** Sets a float buffer type attribute.  */
    fun setBufferAttribute(name: String, values: FloatArray, size: Int) {
        requireNotNull(attributeByName[name]).setBuffer(values, size)
    }

    /**
     * Sets a texture sampler type uniform.
     *
     * @param name The uniform's name.
     * @param textureId The texture identifier.
     * @param textureUnitIndex The texture unit index. Use a different index (0, 1, 2, ...) for each
     * texture sampler in the program.
     */
    fun setSamplerTexIdUniform(name: String, textureId: Int, textureUnitIndex: Int) {
        requireNotNull(uniformByName[name]).setSamplerTextureId(textureId, textureUnitIndex)
    }

    /** Sets a float type uniform.  */
    fun setFloatUniform(name: String, value: Float) {
        requireNotNull(uniformByName[name]).setFloat(value)
    }

    /** Sets a float array type uniform.  */
    fun setFloatsUniform(name: String, value: FloatArray) {
        requireNotNull(uniformByName[name]) {
            name
        }.setFloats(value)
    }

    /** Binds all attributes and uniforms in the program.  */
    fun bindAttributesAndUniforms() {
        for (attribute in attributes) {
            attribute.bind()
        }
        for (uniform in uniforms) {
            uniform.bind()
        }
        checkGlError()
    }

    companion object {

        private fun addShader(programId: Int, type: Int, glsl: String) {
            val shader: Int = GL.glCreateShader(type)
            GL.glShaderSource(shader, glsl)
            GL.glCompileShader(shader)
            val result = intArrayOf(GL.GL_FALSE)
            GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, result,  /* offset = */0)
            if (result[0] != GL.GL_TRUE) {
                error(GL.glGetShaderInfoLog(shader) + ", source: " + glsl)
            }
            GL.glAttachShader(programId, shader)
            GL.glDeleteShader(shader)
            checkGlError()
        }

        private fun isLinked(programId: Int): Boolean {
            return glProgramIv(programId, GL.GL_LINK_STATUS) == GL.GL_TRUE
        }

        private fun getActiveAttributes(programId: Int): Int {
            return glProgramIv(
                programId,
                GL.GL_ACTIVE_ATTRIBUTES,
            )
        }

        private fun getActiveUniforms(programId: Int): Int {
            return glProgramIv(
                programId,
                GL.GL_ACTIVE_UNIFORMS,
            )
        }
    }
}
