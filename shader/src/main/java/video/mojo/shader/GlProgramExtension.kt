package video.mojo.shader

/**
 * Use this program and bind its attributes and uniforms after [block]
 * @param block: block where attributes and uniforms can be initialized
 */
fun GlProgram.useAndBind(block: GlProgram.() -> Unit) {
    use()
    block()
    bindAttributesAndUniforms()
}