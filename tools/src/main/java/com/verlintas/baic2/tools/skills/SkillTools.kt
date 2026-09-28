package com.verlintas.baic2.tools.skills

import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.Skill
import com.verlintas.baic2.core.model.SkillStep
import com.verlintas.baic2.core.model.SkillToolDef
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import com.verlintas.baic2.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Replays a skill tool's step recipe through the registry. */
class CompositeSkillTool(
    private val skill: Skill,
    private val definition: SkillToolDef,
    private val registry: () -> ToolRegistry,
) : DeviceTool {

    override val spec = ToolSpec(
        name = definition.id,
        description = "${definition.description} (skill: ${skill.name})",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val registryRef = registry()
        val lines = mutableListOf<String>()
        for ((index, step) in definition.steps.withIndex()) {
            if (step.tool == definition.id) {
                return ToolResult.Failure("Skill step ${index + 1} would recurse into itself; fix the recipe.")
            }
            val delegate = registryRef.tool(step.tool)
                ?: return ToolResult.Failure(
                    "Step ${index + 1} references unknown tool '${step.tool}'. " +
                        "Available: ${registryRef.toolNames.joinToString()}",
                )
            val args = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(step.argsJson) as? JsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            val result = delegate.execute(args, context)
            lines += when (result) {
                is ToolResult.Success -> "✓ ${step.tool}: ${result.output.take(200)}"
                is ToolResult.Failure -> {
                    lines += "✗ ${step.tool}: ${result.reason}"
                    return ToolResult.Failure(
                        "Skill '${skill.name}' stopped at step ${index + 1}:\n" + lines.joinToString("\n"),
                    )
                }
                is ToolResult.Denied -> return ToolResult.Denied(
                    "Skill '${skill.name}' step ${index + 1} denied: ${result.reason}",
                )
            }
        }
        return ToolResult.Success("Skill '${skill.name}' finished:\n" + lines.joinToString("\n"))
    }
}

/** Loads an installed skill and registers its composite tools. */
class LoadSkillTool(
    private val repository: SkillRepository,
    private val registry: dagger.Lazy<ToolRegistry>,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "load_skill",
        description = "Load an installed skill by id or name; its tools become available and its " +
            "instructions are returned. Call list-friendly ids from installed skills.",
        parametersJson = """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val id = (arguments["id"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'id' argument")
        val skill = repository.find(id)
            ?: return ToolResult.Failure(
                "No installed skill matches '$id'. Ask the user to import one in Library → Skills.",
            )
        if (skill.tools.isEmpty()) {
            return ToolResult.Failure("Skill '${skill.name}' has no tools to load.")
        }
        val toolRegistry = registry.get()
        toolRegistry.registerDynamic(
            skill.tools.map { definition ->
                CompositeSkillTool(skill = skill, definition = definition) { toolRegistry }
            },
        )
        return ToolResult.Success(
            buildString {
                append("Loaded skill '${skill.name}' v${skill.version}.\n")
                append("Tools now available: ${skill.tools.joinToString { it.id }}\n")
                if (skill.instructions.isNotBlank()) {
                    append("\nInstructions:\n${skill.instructions.take(2_000)}")
                }
            },
        )
    }
}
