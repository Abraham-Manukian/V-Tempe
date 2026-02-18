package com.vtempe.server.features.ai.data.llm.repair

data class RepairResult(
    val fixed: String,
    val fixes: Set<String>
)
