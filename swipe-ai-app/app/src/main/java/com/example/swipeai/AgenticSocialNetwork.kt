package com.example.swipeai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.scoring.PageRank
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.alg.scoring.ClosenessCentrality
import org.jgrapht.alg.connectivity.ConnectivityInspector
import java.util.UUID
import kotlin.random.Random
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Represents a user in the social network, which can be either a real user or a simulated agent
 */
data class User(
    val id: String,
    val name: String,
    val isAgent: Boolean = false,
    val interests: MutableList<String> = mutableListOf(),
    val connections: MutableList<String> = mutableListOf(),
    val influence: Double = 0.0,
    val expertise: Map<String, Double> = mapOf()
)

/**
 * Represents content that can be swiped on in the app
 */
data class Content(
    val id: String,
    val title: String,
    val content: String,
    val authorId: String,
    val tags: List<String>,
    val likes: MutableList<String> = mutableListOf(),
    val dislikes: MutableList<String> = mutableListOf(),
    val timestamp: Long = System.currentTimeMillis(),
    val relevanceScore: Double = 0.0,
    val qualityScore: Double = 0.0
)

/**
 * Represents a user's interaction with content
 */
data class Interaction(
    val userId: String,
    val contentId: String,
    val liked: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val interactionStrength: Double = if (liked) 1.0 else -1.0
)

/**
 * Represents an insight derived from graph analysis
 */
data class GraphInsight(
    val id: String,
    val description: String,
    val relatedUserIds: List<String>,
    val relatedContentIds: List<String>,
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Main class that manages the agentic social network with deep graph reasoning
 */
class AgenticSocialNetwork(private val openAI: OpenAI) {
    // Graph representation of the social network
    private val socialGraph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
    
    // Data storage
    private val users = mutableMapOf<String, User>()
    private val content = mutableMapOf<String, Content>()
    private val interactions = mutableListOf<Interaction>()
    private val insights = mutableListOf<GraphInsight>()
    
    // The real user's ID
    private var realUserId: String? = null
    
    // GPT-4o model ID
    private val modelId = ModelId("gpt-4o")
    
    // Network visualizer
    private var networkVisualizer: NetworkVisualizer? = null
    
    // State flows for reactive UI updates
    private val _insightsFlow = MutableStateFlow<List<GraphInsight>>(emptyList())
    val insightsFlow: StateFlow<List<GraphInsight>> = _insightsFlow.asStateFlow()
    
    private val _graphMetricsFlow = MutableStateFlow<Map<String, Double>>(emptyMap())
    val graphMetricsFlow: StateFlow<Map<String, Double>> = _graphMetricsFlow.asStateFlow()
    
    /**
     * Sets the context for visualization
     */
    fun setContext(context: Context) {
        networkVisualizer = NetworkVisualizer(context)
    }
    
    /**
     * Generates a visualization of the social network
     */
    fun generateVisualization(): String? {
        return networkVisualizer?.generateVisualization(
            users = users.values,
            content = content.values,
            interactions = interactions
        )
    }
    
    /**
     * Initializes the social network with a real user and simulated agents
     */
    suspend fun initialize(realUserName: String, numAgents: Int = 10) {
        // Create the real user
        val userId = UUID.randomUUID().toString()
        realUserId = userId
        val realUser = User(
            id = userId,
            name = realUserName,
            isAgent = false
        )
        users[userId] = realUser
        socialGraph.addVertex(userId)
        
        // Create simulated agents
        createSimulatedAgents(numAgents)
        
        // Generate initial content
        generateInitialContent()
        
        // Perform initial graph analysis
        analyzeGraph()
    }
    
    /**
     * Creates simulated agents with different personalities and interests
     */
    private suspend fun createSimulatedAgents(numAgents: Int) {
        val agentPrompt = """
            Create a simulated social media user with a unique personality, interests, and expertise areas.
            Format the response as JSON with the following structure:
            {
              "name": "Agent's name",
              "interests": ["interest1", "interest2", "interest3"],
              "expertise": {
                "topic1": 0.8,
                "topic2": 0.6,
                "topic3": 0.9
              }
            }
            Expertise values should be between 0 and 1, where 1 represents maximum expertise.
        """.trimIndent()
        
        repeat(numAgents) {
            val response = generateResponse(agentPrompt)
            
            try {
                // Extract JSON from response
                val jsonRegex = """\{[\s\S]*\}""".toRegex()
                val jsonMatch = jsonRegex.find(response)
                
                if (jsonMatch != null) {
                    val jsonString = jsonMatch.value
                    val jsonObject = org.json.JSONObject(jsonString)
                    
                    val agentId = UUID.randomUUID().toString()
                    val agentName = jsonObject.getString("name")
                    val interests = mutableListOf<String>()
                    val expertise = mutableMapOf<String, Double>()
                    
                    val interestsArray = jsonObject.getJSONArray("interests")
                    for (i in 0 until interestsArray.length()) {
                        interests.add(interestsArray.getString(i))
                    }
                    
                    val expertiseObj = jsonObject.getJSONObject("expertise")
                    val expertiseKeys = expertiseObj.keys()
                    while (expertiseKeys.hasNext()) {
                        val key = expertiseKeys.next()
                        expertise[key] = expertiseObj.getDouble(key)
                    }
                    
                    val agent = User(
                        id = agentId,
                        name = agentName,
                        isAgent = true,
                        interests = interests,
                        expertise = expertise
                    )
                    
                    users[agentId] = agent
                    socialGraph.addVertex(agentId)
                    
                    // Connect agents randomly to create a social network
                    users.keys.filter { it != agentId }.shuffled().take(Random.nextInt(1, 5)).forEach { otherId ->
                        agent.connections.add(otherId)
                        users[otherId]?.connections?.add(agentId)
                        
                        if (!socialGraph.containsEdge(agentId, otherId)) {
                            socialGraph.addEdge(agentId, otherId)
                        }
                        
                        if (!socialGraph.containsEdge(otherId, agentId)) {
                            socialGraph.addEdge(otherId, agentId)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error creating agent: ${e.message}")
            }
        }
    }
    
    /**
     * Generates initial content from the simulated agents
     */
    private suspend fun generateInitialContent() {
        users.values.filter { it.isAgent }.forEach { agent ->
            generateContentForAgent(agent)
        }
    }
    
    /**
     * Generates content for a specific agent based on their interests and expertise
     */
    private suspend fun generateContentForAgent(agent: User) {
        val interests = agent.interests.joinToString(", ")
        val expertise = agent.expertise.entries.joinToString(", ") { "${it.key} (${it.value})" }
        
        val contentPrompt = """
            You are ${agent.name}, a social media user interested in $interests with expertise in $expertise.
            Create a short social media post (1-3 sentences) about one of your areas of expertise.
            Make the content quality proportional to your expertise level in the topic.
            
            Format the response as JSON with the following structure:
            {
              "title": "Post title",
              "content": "Post content",
              "tags": ["tag1", "tag2"],
              "topic": "The main topic of your post",
              "quality": 0.8 // A self-assessment of the quality (0-1)
            }
        """.trimIndent()
        
        val response = generateResponse(contentPrompt)
        
        try {
            // Extract JSON from response
            val jsonRegex = """\{[\s\S]*\}""".toRegex()
            val jsonMatch = jsonRegex.find(response)
            
            if (jsonMatch != null) {
                val jsonString = jsonMatch.value
                val jsonObject = org.json.JSONObject(jsonString)
                
                val contentId = UUID.randomUUID().toString()
                val title = jsonObject.getString("title")
                val contentText = jsonObject.getString("content")
                val qualityScore = jsonObject.getDouble("quality")
                
                val tags = mutableListOf<String>()
                val tagsArray = jsonObject.getJSONArray("tags")
                for (i in 0 until tagsArray.length()) {
                    tags.add(tagsArray.getString(i))
                }
                
                val newContent = Content(
                    id = contentId,
                    title = title,
                    content = contentText,
                    authorId = agent.id,
                    tags = tags,
                    qualityScore = qualityScore
                )
                
                content[contentId] = newContent
                
                // Simulate other agents interacting with the content
                simulateAgentInteractions(newContent)
            }
        } catch (e: Exception) {
            println("Error generating content: ${e.message}")
        }
    }
    
    /**
     * Simulates other agents interacting with content based on their interests and the content quality
     */
    private fun simulateAgentInteractions(content: Content) {
        users.values.filter { it.isAgent && it.id != content.authorId }.forEach { agent ->
            // Calculate probability of interaction based on interest overlap and content quality
            val interestOverlap = agent.interests.count { interest -> 
                content.tags.any { tag -> tag.contains(interest, ignoreCase = true) } 
            }
            
            val interactionProbability = (interestOverlap * 0.2 + content.qualityScore * 0.8).coerceIn(0.0, 1.0)
            
            if (Random.nextDouble() < interactionProbability) {
                // Determine if the agent likes the content
                val expertiseInTopic = content.tags.maxOfOrNull { tag ->
                    agent.expertise.entries.maxOfOrNull { (topic, level) ->
                        if (tag.contains(topic, ignoreCase = true)) level else 0.0
                    } ?: 0.0
                } ?: 0.0
                
                // Agents with higher expertise are more critical
                val likeProbability = (content.qualityScore * 0.7 + (1 - expertiseInTopic) * 0.3).coerceIn(0.1, 0.9)
                val liked = Random.nextDouble() < likeProbability
                
                // Calculate interaction strength based on expertise and content quality
                val interactionStrength = if (liked) {
                    (0.5 + expertiseInTopic * 0.5).coerceIn(0.1, 1.0)
                } else {
                    -(0.5 + expertiseInTopic * 0.5).coerceIn(0.1, 1.0)
                }
                
                if (liked) {
                    content.likes.add(agent.id)
                } else {
                    content.dislikes.add(agent.id)
                }
                
                interactions.add(
                    Interaction(
                        userId = agent.id,
                        contentId = content.id,
                        liked = liked,
                        interactionStrength = interactionStrength
                    )
                )
            }
        }
    }
    
    /**
     * Records a real user's interaction with content
     */
    fun recordUserInteraction(contentId: String, liked: Boolean) {
        realUserId?.let { userId ->
            val contentItem = content[contentId]
            
            if (contentItem != null) {
                if (liked) {
                    contentItem.likes.add(userId)
                } else {
                    contentItem.dislikes.add(userId)
                }
                
                // Calculate interaction strength based on user's past behavior
                val userInteractions = interactions.filter { it.userId == userId }
                val averageInteractionStrength = if (userInteractions.isNotEmpty()) {
                    userInteractions.sumOf { it.interactionStrength } / userInteractions.size
                } else {
                    0.5
                }
                
                val interactionStrength = if (liked) {
                    (0.5 + averageInteractionStrength * 0.5).coerceIn(0.1, 1.0)
                } else {
                    -(0.5 + averageInteractionStrength * 0.5).coerceIn(0.1, 1.0)
                }
                
                interactions.add(
                    Interaction(
                        userId = userId,
                        contentId = contentId,
                        liked = liked,
                        interactionStrength = if (liked) interactionStrength else -interactionStrength
                    )
                )
                
                // Update user interests based on interaction
                if (liked) {
                    contentItem.tags.forEach { tag ->
                        if (!users[userId]?.interests?.contains(tag)!!) {
                            users[userId]?.interests?.add(tag)
                        }
                    }
                }
                
                // Re-analyze the graph after significant user interaction
                if (interactions.filter { it.userId == userId }.size % 5 == 0) {
                    analyzeGraph()
                }
            }
        }
    }
    
    /**
     * Analyzes the social graph to derive insights and update metrics
     */
    private fun analyzeGraph() {
        // Skip analysis if the graph is too small
        if (socialGraph.vertexSet().size < 5) return
        
        // Calculate centrality metrics
        val pageRank = PageRank(socialGraph, 0.85, 100, 0.0001)
        val betweennessCentrality = BetweennessCentrality(socialGraph)
        val closenessCentrality = ClosenessCentrality(socialGraph)
        
        // Update user influence based on centrality metrics
        users.values.forEach { user ->
            val prScore = pageRank.getVertexScore(user.id)
            val bcScore = betweennessCentrality.getVertexScore(user.id)
            val ccScore = closenessCentrality.getVertexScore(user.id)
            
            // Combine metrics to calculate influence
            val influence = (prScore * 0.5 + bcScore * 0.3 + ccScore * 0.2)
            
            // Update user with new influence score
            users[user.id] = user.copy(influence = influence)
        }
        
        // Update content relevance scores based on author influence and interactions
        content.values.forEach { contentItem ->
            val authorInfluence = users[contentItem.authorId]?.influence ?: 0.0
            val interactionScore = interactions
                .filter { it.contentId == contentItem.id }
                .sumOf { it.interactionStrength }
            
            val relevanceScore = (authorInfluence * 0.3 + interactionScore * 0.7).coerceIn(0.0, 1.0)
            
            // Update content with new relevance score
            content[contentItem.id] = contentItem.copy(relevanceScore = relevanceScore)
        }
        
        // Update graph metrics flow
        val metrics = mapOf(
            "userCount" to users.size.toDouble(),
            "contentCount" to content.size.toDouble(),
            "interactionCount" to interactions.size.toDouble(),
            "averageUserInfluence" to users.values.map { it.influence }.average(),
            "averageContentRelevance" to content.values.map { it.relevanceScore }.average()
        )
        _graphMetricsFlow.value = metrics
        
        // Generate insights from the graph structure
        CoroutineScope(Dispatchers.IO).launch {
            generateGraphInsights()
        }
    }
    
    /**
     * Generates insights from the graph structure using LLM reasoning
     */
    private suspend fun generateGraphInsights() {
        // Identify influential users
        val influentialUsers = users.values
            .sortedByDescending { it.influence }
            .take(3)
        
        // Identify popular content
        val popularContent = content.values
            .sortedByDescending { it.relevanceScore }
            .take(3)
        
        // Identify common interest patterns
        val interestFrequency = users.values
            .flatMap { it.interests }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        
        // Prepare data for LLM analysis
        val insightPrompt = """
            Analyze this social network data and generate 3 key insights about the network dynamics.
            
            Influential Users:
            ${influentialUsers.joinToString("\n") { "- ${it.name}: Influence ${it.influence}, Interests: ${it.interests.joinToString(", ")}" }}
            
            Popular Content:
            ${popularContent.joinToString("\n") { "- ${it.title}: Relevance ${it.relevanceScore}, Tags: ${it.tags.joinToString(", ")}, Likes: ${it.likes.size}, Dislikes: ${it.dislikes.size}" }}
            
            Common Interests:
            ${interestFrequency.joinToString("\n") { "- ${it.first}: ${it.second} users" }}
            
            Format your response as JSON with the following structure:
            {
              "insights": [
                {
                  "description": "Detailed insight description",
                  "relatedUsers": ["user_id1", "user_id2"],
                  "relatedContent": ["content_id1", "content_id2"],
                  "confidence": 0.85
                }
              ]
            }
        """.trimIndent()
        
        val response = generateResponse(insightPrompt)
        
        try {
            // Extract JSON from response
            val jsonRegex = """\{[\s\S]*\}""".toRegex()
            val jsonMatch = jsonRegex.find(response)
            
            if (jsonMatch != null) {
                val jsonString = jsonMatch.value
                val jsonObject = org.json.JSONObject(jsonString)
                
                val newInsights = mutableListOf<GraphInsight>()
                
                val insightsArray = jsonObject.getJSONArray("insights")
                for (i in 0 until insightsArray.length()) {
                    val insightObj = insightsArray.getJSONObject(i)
                    
                    val description = insightObj.getString("description")
                    val confidence = insightObj.getDouble("confidence")
                    
                    val relatedUsers = mutableListOf<String>()
                    val relatedUsersArray = insightObj.getJSONArray("relatedUsers")
                    for (j in 0 until relatedUsersArray.length()) {
                        val userId = relatedUsersArray.getString(j)
                        if (users.containsKey(userId)) {
                            relatedUsers.add(userId)
                        }
                    }
                    
                    val relatedContent = mutableListOf<String>()
                    val relatedContentArray = insightObj.getJSONArray("relatedContent")
                    for (j in 0 until relatedContentArray.length()) {
                        val contentId = relatedContentArray.getString(j)
                        if (content.containsKey(contentId)) {
                            relatedContent.add(contentId)
                        }
                    }
                    
                    val insight = GraphInsight(
                        id = UUID.randomUUID().toString(),
                        description = description,
                        relatedUserIds = relatedUsers,
                        relatedContentIds = relatedContent,
                        confidence = confidence
                    )
                    
                    newInsights.add(insight)
                }
                
                // Update insights
                insights.clear()
                insights.addAll(newInsights)
                _insightsFlow.value = newInsights
            }
        } catch (e: Exception) {
            println("Error generating insights: ${e.message}")
        }
    }
    
    /**
     * Generates personalized content for the real user based on their interests, interactions, and graph insights
     */
    suspend fun generatePersonalizedContent(): List<ContentCard> {
        val userId = realUserId ?: return emptyList()
        val user = users[userId] ?: return emptyList()
        
        // Get user's interests and liked content
        val interests = user.interests.joinToString(", ")
        val likedContentIds = interactions.filter { it.userId == userId && it.liked }.map { it.contentId }
        val likedContent = likedContentIds.mapNotNull { content[it] }
        
        // Extract common themes from liked content
        val likedTags = likedContent.flatMap { it.tags }.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.joinToString(", ")
        
        // Get network insights
        val networkInsights = insights.joinToString("\n") { "- ${it.description}" }
        
        // Find influential users with similar interests
        val similarInfluentialUsers = users.values
            .filter { it.id != userId && it.influence > 0.5 }
            .filter { user -> user.interests.any { interest -> user.interests.contains(interest) } }
            .take(3)
            .joinToString("\n") { "- ${it.name}: ${it.interests.joinToString(", ")}" }
        
        val contentPrompt = """
            Generate 3 personalized social media posts for a user with the following interests: $interests.
            The user has previously liked content with these themes: $likedTags.
            
            Consider these network insights:
            $networkInsights
            
            And these influential users with similar interests:
            $similarInfluentialUsers
            
            Format the response as JSON with the following structure:
            {
              "posts": [
                {
                  "title": "Post title",
                  "content": "Post content",
                  "tags": ["tag1", "tag2"],
                  "relevance": 0.85
                }
              ]
            }
            
            Make the content engaging, informative, and relevant to the user's interests.
            Each post should be 1-3 sentences long.
            The relevance score (0-1) should reflect how well the post matches the user's interests and network context.
        """.trimIndent()
        
        val response = generateResponse(contentPrompt)
        val result = mutableListOf<ContentCard>()
        
        try {
            // Extract JSON from response
            val jsonRegex = """\{[\s\S]*\}""".toRegex()
            val jsonMatch = jsonRegex.find(response)
            
            if (jsonMatch != null) {
                val jsonString = jsonMatch.value
                val jsonObject = org.json.JSONObject(jsonString)
                
                val postsArray = jsonObject.getJSONArray("posts")
                for (i in 0 until postsArray.length()) {
                    val postObj = postsArray.getJSONObject(i)
                    
                    val title = postObj.getString("title")
                    val contentText = postObj.getString("content")
                    val relevance = postObj.getDouble("relevance")
                    
                    val tags = mutableListOf<String>()
                    val tagsArray = postObj.getJSONArray("tags")
                    for (j in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(j))
                    }
                    
                    // Select an agent author based on expertise in the tags
                    val potentialAuthors = users.values
                        .filter { it.isAgent }
                        .map { agent ->
                            val expertiseScore = tags.sumOf { tag ->
                                agent.expertise.entries.maxOfOrNull { (topic, level) ->
                                    if (tag.contains(topic, ignoreCase = true)) level else 0.0
                                } ?: 0.0
                            }
                            Pair(agent, expertiseScore)
                        }
                        .sortedByDescending { it.second }
                    
                    val selectedAuthor = if (potentialAuthors.isNotEmpty() && potentialAuthors.first().second > 0.3) {
                        potentialAuthors.first().first
                    } else {
                        users.values.filter { it.isAgent }.random()
                    }
                    
                    val contentId = UUID.randomUUID().toString()
                    val newContent = Content(
                        id = contentId,
                        title = title,
                        content = contentText,
                        authorId = selectedAuthor.id,
                        tags = tags,
                        relevanceScore = relevance,
                        qualityScore = selectedAuthor.expertise.entries.maxOfOrNull { (topic, level) ->
                            if (tags.any { tag -> tag.contains(topic, ignoreCase = true) }) level else 0.0
                        } ?: 0.5
                    )
                    
                    content[contentId] = newContent
                    
                    // Convert to ContentCard for UI
                    result.add(
                        ContentCard(
                            id = contentId,
                            title = title,
                            content = contentText,
                            author = selectedAuthor.name,
                            tags = tags
                        )
                    )
                    
                    // Simulate other agents interacting with the content
                    simulateAgentInteractions(newContent)
                }
            }
        } catch (e: Exception) {
            println("Error generating personalized content: ${e.message}")
        }
        
        return result
    }
    
    /**
     * Gets content for the user to swipe on, using graph-based recommendation
     */
    suspend fun getContentForSwiping(count: Int = 5): List<ContentCard> {
        // If we have less than 10 content items, generate more
        if (content.size < 10) {
            users.values.filter { it.isAgent }.shuffled().take(5).forEach { agent ->
                generateContentForAgent(agent)
            }
        }
        
        // Get personalized content if the user has interactions
        val userId = realUserId
        val hasInteractions = userId != null && interactions.any { it.userId == userId }
        
        return if (hasInteractions) {
            // Mix of personalized and trending content
            val personalized = generatePersonalizedContent()
            val trending = getTrendingContent(count - personalized.size)
            (personalized + trending).shuffled()
        } else {
            // Just trending content for new users
            getTrendingContent(count)
        }
    }
    
    /**
     * Gets trending content based on graph analysis
     */
    private fun getTrendingContent(count: Int): List<ContentCard> {
        // Use a combination of likes, relevance score, and author influence
        return content.values
            .map { contentItem ->
                val authorInfluence = users[contentItem.authorId]?.influence ?: 0.0
                val score = contentItem.likes.size * 0.4 + contentItem.relevanceScore * 0.4 + authorInfluence * 0.2
                Pair(contentItem, score)
            }
            .sortedByDescending { it.second }
            .take(count)
            .map { 
                ContentCard(
                    id = it.first.id,
                    title = it.first.title,
                    content = it.first.content,
                    author = users[it.first.authorId]?.name ?: "Unknown",
                    tags = it.first.tags
                )
            }
    }
    
    /**
     * Gets insights about the social network
     */
    fun getNetworkInsights(): List<String> {
        return insights.map { it.description }
    }
    
    /**
     * Helper function to generate responses from GPT-4o
     */
    private suspend fun generateResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val chatCompletionRequest = ChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "You are a helpful assistant that generates content for a social media platform with agentic graph reasoning capabilities. Your responses should be concise, engaging, and formatted as requested."
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = prompt
                        )
                    )
                )
                
                val response = openAI.chatCompletion(chatCompletionRequest)
                response.choices.first().message?.content ?: "Error generating response"
            } catch (e: Exception) {
                println("Error calling OpenAI API: ${e.message}")
                "Error: ${e.message}"
            }
        }
    }
} 