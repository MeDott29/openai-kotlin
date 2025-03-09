package com.agentic.graph

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.alg.scoring.ClosenessCentrality
import org.jgrapht.alg.scoring.PageRank
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class Concept(
    val id: String,
    val name: String,
    val description: String
)

@Serializable
data class Relationship(
    val source: String,
    val target: String,
    val type: String,
    val description: String
)

@Serializable
data class GraphData(
    val concepts: List<Concept>,
    val relationships: List<Relationship>
)

class GraphReasoner(private val openAI: OpenAI) {
    private val graph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
    private val concepts = mutableMapOf<String, Concept>()
    private val relationships = mutableListOf<Relationship>()
    private val json = Json { prettyPrint = true }
    private val modelId = ModelId("gpt-4o")
    private val maxIterations = 10
    private val visualizer = GraphVisualizer()
    private val initialPrompt = """
        You are an expert in materials science, specifically in self-healing composite materials. 
        Your task is to help me explore and develop knowledge about self-healing composite materials 
        that can autonomously repair damage.
        
        Please identify key concepts related to self-healing composite materials, including:
        1. Materials components (e.g., polymer matrices, reinforcement materials, healing agents)
        2. Mechanisms of self-healing
        3. Sensing and activation methods
        4. Applications and use cases
        
        For each concept, provide a brief description. Also identify relationships between these concepts.
    """.trimIndent()
    
    suspend fun run() {
        println("Initializing knowledge graph...")
        
        var currentPrompt = initialPrompt
        var iteration = 1
        
        while (iteration <= maxIterations) {
            println("\nIteration $iteration of $maxIterations")
            println("Generating new concepts and relationships...")
            
            val response = generateResponse(currentPrompt)
            val (newConcepts, newRelationships) = parseResponse(response)
            
            updateGraph(newConcepts, newRelationships)
            
            // Analyze graph metrics
            analyzeGraph()
            
            // Save the current state of the graph
            saveGraph(iteration)
            
            // Generate visualization
            visualizer.generateVisualization(concepts.values, relationships, iteration)
            
            // Generate the next prompt based on the current graph
            currentPrompt = generateNextPrompt()
            
            iteration++
            delay(1000) // Small delay to avoid rate limiting
        }
        
        println("\nKnowledge graph generation complete.")
        println("Final graph has ${concepts.size} concepts and ${relationships.size} relationships.")
        
        // Generate a final summary
        val summaryPrompt = """
            Based on the knowledge graph we've developed about self-healing composite materials, 
            please provide a comprehensive summary of the key insights, emerging patterns, and 
            potential novel applications or research directions.
            
            Here are the concepts and relationships in our knowledge graph:
            
            Concepts:
            ${concepts.values.joinToString("\n") { "- ${it.name}: ${it.description}" }}
            
            Relationships:
            ${relationships.joinToString("\n") { "- ${concepts[it.source]?.name} ${it.type} ${concepts[it.target]?.name}: ${it.description}" }}
        """.trimIndent()
        
        println("\nGenerating final summary...")
        val summary = generateResponse(summaryPrompt)
        println("\nFinal Summary:\n$summary")
    }
    
    private suspend fun generateResponse(prompt: String): String {
        val chatCompletionRequest = ChatCompletionRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are an expert in materials science and knowledge graph construction. Your responses should be detailed, accurate, and focused on identifying key concepts and relationships."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
                )
            )
        )
        
        val response = openAI.chatCompletion(chatCompletionRequest)
        return response.choices.first().message.content ?: ""
    }
    
    private fun parseResponse(response: String): Pair<List<Concept>, List<Relationship>> {
        // This is a simplified parsing logic. In a real implementation, you might want to use
        // a more sophisticated approach, possibly involving another LLM call to structure the data.
        val newConcepts = mutableListOf<Concept>()
        val newRelationships = mutableListOf<Relationship>()
        
        // Use another LLM call to parse the response into structured data
        runBlocking {
            val parsingPrompt = """
                Parse the following text into a structured format of concepts and relationships.
                For each concept, extract:
                1. A unique identifier (short name with no spaces)
                2. The concept name
                3. A brief description
                
                For each relationship, extract:
                1. The source concept identifier
                2. The target concept identifier
                3. The type of relationship (e.g., "contains", "influences", "enables")
                4. A brief description of the relationship
                
                Format your response as JSON with the following structure:
                {
                  "concepts": [
                    {"id": "concept_id", "name": "Concept Name", "description": "Description of the concept"}
                  ],
                  "relationships": [
                    {"source": "source_concept_id", "target": "target_concept_id", "type": "relationship_type", "description": "Description of the relationship"}
                  ]
                }
                
                Here's the text to parse:
                
                $response
            """.trimIndent()
            
            val parsedResponse = generateResponse(parsingPrompt)
            
            try {
                // Extract the JSON part from the response
                val jsonRegex = """\{[\s\S]*\}""".toRegex()
                val jsonMatch = jsonRegex.find(parsedResponse)
                
                if (jsonMatch != null) {
                    val jsonString = jsonMatch.value
                    val graphData = json.decodeFromString<GraphData>(jsonString)
                    
                    newConcepts.addAll(graphData.concepts)
                    newRelationships.addAll(graphData.relationships)
                } else {
                    println("Failed to extract JSON from the parsed response.")
                }
            } catch (e: Exception) {
                println("Error parsing response: ${e.message}")
                println("Raw response: $parsedResponse")
            }
        }
        
        return Pair(newConcepts, newRelationships)
    }
    
    private fun updateGraph(newConcepts: List<Concept>, newRelationships: List<Relationship>) {
        // Add new concepts to the graph
        for (concept in newConcepts) {
            if (!concepts.containsKey(concept.id)) {
                concepts[concept.id] = concept
                graph.addVertex(concept.id)
                println("Added concept: ${concept.name}")
            }
        }
        
        // Add new relationships to the graph
        for (relationship in newRelationships) {
            // Ensure both source and target concepts exist
            if (concepts.containsKey(relationship.source) && concepts.containsKey(relationship.target)) {
                // Check if this relationship is new
                val isNew = relationships.none { 
                    it.source == relationship.source && 
                    it.target == relationship.target && 
                    it.type == relationship.type 
                }
                
                if (isNew) {
                    relationships.add(relationship)
                    
                    // Add the edge to the graph if it doesn't exist
                    if (!graph.containsEdge(relationship.source, relationship.target)) {
                        graph.addEdge(relationship.source, relationship.target)
                        println("Added relationship: ${concepts[relationship.source]?.name} ${relationship.type} ${concepts[relationship.target]?.name}")
                    }
                }
            } else {
                println("Skipped relationship: Source or target concept not found")
            }
        }
    }
    
    private fun analyzeGraph() {
        println("\nAnalyzing graph metrics...")
        
        // Calculate various centrality measures
        val betweennessCentrality = BetweennessCentrality(graph)
        val closenessCentrality = ClosenessCentrality(graph)
        val pageRank = PageRank(graph)
        
        // Find the most central concepts
        val topBetweenness = graph.vertexSet()
            .sortedByDescending { betweennessCentrality.getVertexScore(it) }
            .take(5)
        
        val topCloseness = graph.vertexSet()
            .sortedByDescending { closenessCentrality.getVertexScore(it) }
            .take(5)
        
        val topPageRank = graph.vertexSet()
            .sortedByDescending { pageRank.getVertexScore(it) }
            .take(5)
        
        println("Top concepts by betweenness centrality:")
        topBetweenness.forEach { 
            println("- ${concepts[it]?.name}: ${betweennessCentrality.getVertexScore(it)}") 
        }
        
        println("\nTop concepts by closeness centrality:")
        topCloseness.forEach { 
            println("- ${concepts[it]?.name}: ${closenessCentrality.getVertexScore(it)}") 
        }
        
        println("\nTop concepts by PageRank:")
        topPageRank.forEach { 
            println("- ${concepts[it]?.name}: ${pageRank.getVertexScore(it)}") 
        }
        
        // Analyze connectivity
        val connectivityInspector = ConnectivityInspector(graph)
        val connectedComponents = connectivityInspector.connectedSets()
        
        println("\nNumber of connected components: ${connectedComponents.size}")
        println("Largest component size: ${connectedComponents.maxOfOrNull { it.size } ?: 0}")
    }
    
    private fun generateNextPrompt(): String {
        // Identify potential areas for expansion
        val connectivityInspector = ConnectivityInspector(graph)
        val connectedComponents = connectivityInspector.connectedSets()
        
        // Find concepts with few connections
        val conceptsWithFewConnections = graph.vertexSet()
            .filter { graph.degreeOf(it) < 2 }
            .take(3)
            .mapNotNull { concepts[it] }
        
        // Find the most central concepts
        val pageRank = PageRank(graph)
        val centralConcepts = graph.vertexSet()
            .sortedByDescending { pageRank.getVertexScore(it) }
            .take(3)
            .mapNotNull { concepts[it] }
        
        // Generate a prompt that focuses on expanding the graph
        return """
            Based on our current knowledge graph about self-healing composite materials, I'd like to expand our understanding further.
            
            Here are some key concepts we've identified:
            ${centralConcepts.joinToString("\n") { "- ${it.name}: ${it.description}" }}
            
            These concepts have fewer connections and might need further exploration:
            ${conceptsWithFewConnections.joinToString("\n") { "- ${it.name}: ${it.description}" }}
            
            We have ${connectedComponents.size} disconnected components in our graph. 
            
            Please help me:
            1. Identify new concepts related to these existing ones
            2. Explore potential connections between disconnected parts of our knowledge graph
            3. Deepen our understanding of the concepts with fewer connections
            4. Identify any emerging patterns or potential novel applications
            
            Focus on being specific and detailed in your descriptions of concepts and relationships.
        """.trimIndent()
    }
    
    private fun saveGraph(iteration: Int) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val directory = File("graph_data")
        directory.mkdirs()
        
        val graphData = GraphData(concepts.values.toList(), relationships)
        val jsonString = json.encodeToString(GraphData.serializer(), graphData)
        
        File(directory, "graph_iteration_${iteration}_$timestamp.json").writeText(jsonString)
        println("Saved graph data for iteration $iteration")
    }
} 