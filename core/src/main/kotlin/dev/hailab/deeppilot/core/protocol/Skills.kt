package dev.hailab.deeppilot.core.protocol

import kotlinx.serialization.Serializable

/** 只承载会话可调用技能的元数据，正文由主机在发送后按需加载。 */
@Serializable
data class SkillSummary(val name: String, val description: String, val modelInvocable: Boolean)

@Serializable
data class SessionSkillsRequest(val sessionId: String)

@Serializable
data class SessionSkills(val sessionId: String, val skills: List<SkillSummary>)
