package com.example.swipeai

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * A simple console application that demonstrates the core functionality of the agentic social network.
 */
fun main() = runBlocking {
    println("SwipeAI - Agentic Social Network Demo")
    println("======================================")
    
    // Create OpenAI client
    val openAI = OpenAI(
        token = System.getenv("OPENAI_API_KEY") ?: "",
        timeout = Timeout(socket = 60.seconds)
    )
    
    // Create the agentic social network
    val socialNetwork = AgenticSocialNetwork(openAI)
    
    println("Initializing social network with simulated agents...")
    socialNetwork.initialize("ConsoleUser", numAgents = 5)
    
    println("\nGenerating initial content...")
    val initialContent = socialNetwork.getContentForSwiping(3)
    
    // Display the initial content
    println("\nInitial Content:")
    initialContent.forEachIndexed { index, card ->
        println("\n[$index] ${card.title}")
        println("Author: ${card.author}")
        println("Content: ${card.content}")
        println("Tags: ${card.tags.joinToString(", ")}")
    }
    
    // Simulate user interactions
    println("\nSimulating user interactions...")
    initialContent.forEachIndexed { index, card ->
        val liked = index % 2 == 0 // Like every other card
        socialNetwork.recordUserInteraction(card.id, liked)
        println("${if (liked) "Liked" else "Disliked"}: ${card.title}")
    }
    
    // Generate personalized content
    println("\nGenerating personalized content based on interactions...")
    val personalizedContent = socialNetwork.getContentForSwiping(3)
    
    // Display the personalized content
    println("\nPersonalized Content:")
    personalizedContent.forEachIndexed { index, card ->
        println("\n[$index] ${card.title}")
        println("Author: ${card.author}")
        println("Content: ${card.content}")
        println("Tags: ${card.tags.joinToString(", ")}")
    }
    
    println("\nDemo completed!")
} 