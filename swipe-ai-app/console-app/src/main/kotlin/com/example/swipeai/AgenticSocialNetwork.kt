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
import java.util.UUID
import kotlin.random.Random

/**
 * Represents a user in the social network, which can be either a real user or a simulated agent
 */
data class User(
    val id: String,
    val name: String,
    val isAgent: Boolean = false,
    val interests: MutableList<String> = mutableListOf(),
    val connections: MutableList<String> = mutableListOf()
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
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a user's interaction with content
 */
data class Interaction(
    val userId: String,
    val contentId: String,
    val liked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents content displayed to users
 */
data class ContentCard(
    val id: String,
    val title: String,
    val content: String,
    val author: String,
    val tags: List<String> = emptyList()
)

/**
 * Main class that manages the agentic social network
 */
class AgenticSocialNetwork(private val openAI: OpenAI) {
    // Graph representation of the social network
    private val socialGraph = DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge::class.java)
    
    // Data storage
    private val users = mutableMapOf<String, User>()
    private val content = mutableMapOf<String, Content>()
    private val interactions = mutableListOf<Interaction>()
    
    // The real user's ID
    private var realUserId: String? = null
    
    // GPT-4o model ID
    private val modelId = ModelId("gpt-4o")
    
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
    }
    
    /**
     * Creates simulated agents with different personalities and interests
     */
    private suspend fun createSimulatedAgents(numAgents: Int) {
        val agentPrompt = """
            Create a simulated social media user with a unique personality and interests.
            Format the response as JSON with the following structure:
            {
              "name": "Agent's name",
              "interests": ["interest1", "interest2", "interest3"]
            }
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
                    
                    val interestsArray = jsonObject.getJSONArray("interests")
                    for (i in 0 until interestsArray.length()) {
                        interests.add(interestsArray.getString(i))
                    }
                    
                    val agent = User(
                        id = agentId,
                        name = agentName,
                        isAgent = true,
                        interests = interests
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
     * Generates content for a specific agent based on their interests
     */
    private suspend fun generateContentForAgent(agent: User) {
        val interests = agent.interests.joinToString(", ")
        
        val contentPrompt = """
            You are ${agent.name}, a social media user interested in $interests.
            Create a short social media post (1-3 sentences) about one of your interests.
            Format the response as JSON with the following structure:
            {
              "title": "Post title",
              "content": "Post content",
              "tags": ["tag1", "tag2"]
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
                    tags = tags
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
     * Simulates other agents interacting with content
     */
    private fun simulateAgentInteractions(content: Content) {
        users.values.filter { it.isAgent && it.id != content.authorId }.shuffled().take(Random.nextInt(0, 5)).forEach { agent ->
            val liked = Random.nextBoolean()
            
            if (liked) {
                content.likes.add(agent.id)
            } else {
                content.dislikes.add(agent.id)
            }
            
            interactions.add(
                Interaction(
                    userId = agent.id,
                    contentId = content.id,
                    liked = liked
                )
            )
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
                
                interactions.add(
                    Interaction(
                        userId = userId,
                        contentId = contentId,
                        liked = liked
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
            }
        }
    }
    
    /**
     * Generates personalized content for the real user based on their interests and interactions
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
        
        val contentPrompt = """
            Generate 3 personalized social media posts for a user with the following interests: $interests.
            The user has previously liked content with these themes: $likedTags.
            
            Format the response as JSON with the following structure:
            {
              "posts": [
                {
                  "title": "Post title",
                  "content": "Post content",
                  "tags": ["tag1", "tag2"]
                }
              ]
            }
            
            Make the content engaging, informative, and relevant to the user's interests.
            Each post should be 1-3 sentences long.
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
                    
                    val tags = mutableListOf<String>()
                    val tagsArray = postObj.getJSONArray("tags")
                    for (j in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(j))
                    }
                    
                    // Select a random agent as the author
                    val randomAgent = users.values.filter { it.isAgent }.random()
                    
                    val contentId = UUID.randomUUID().toString()
                    val newContent = Content(
                        id = contentId,
                        title = title,
                        content = contentText,
                        authorId = randomAgent.id,
                        tags = tags
                    )
                    
                    content[contentId] = newContent
                    
                    // Convert to ContentCard for UI
                    result.add(
                        ContentCard(
                            id = contentId,
                            title = title,
                            content = contentText,
                            author = randomAgent.name,
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
     * Gets content for the user to swipe on
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
     * Gets trending content based on likes
     */
    private fun getTrendingContent(count: Int): List<ContentCard> {
        return content.values
            .sortedByDescending { it.likes.size }
            .take(count)
            .map { 
                ContentCard(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    author = users[it.authorId]?.name ?: "Unknown",
                    tags = it.tags
                )
            }
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
                            content = "You are a helpful assistant that generates content for a social media platform. Your responses should be concise, engaging, and formatted as requested."
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