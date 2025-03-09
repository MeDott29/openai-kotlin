package com.agentic.graph

import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val token = requireNotNull(apiKey) { "OPENAI_API_KEY environment variable must be set." }
    val openAI = OpenAI(token = token, logging = LoggingConfig(LogLevel.All))
    
    println("Starting Agentic Deep Graph Reasoning System")
    
    val graphReasoner = GraphReasoner(openAI)
    graphReasoner.run()
} 