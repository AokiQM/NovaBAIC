package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SkillStep(
    val tool: String,
    val argsJson: String = "{}",
)

@Serializable
data class SkillToolDef(
    val id: String,
    val description: String,
    val steps: List<SkillStep>,
)

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val version: Int = 1,
    val description: String = "",
    val instructions: String = "",
    val permissions: List<String> = emptyList(),
    val tools: List<SkillToolDef> = emptyList(),
)
