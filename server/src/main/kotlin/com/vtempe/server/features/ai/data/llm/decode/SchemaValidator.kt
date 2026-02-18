package com.vtempe.server.features.ai.data.llm.decode

fun interface SchemaValidator<T> {
    fun validate(value: T): List<String>
}

