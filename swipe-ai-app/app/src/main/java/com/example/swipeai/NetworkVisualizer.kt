package com.example.swipeai

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A class for visualizing the social network graph using D3.js
 */
class NetworkVisualizer(private val context: Context) {
    
    /**
     * Generates an HTML file with a D3.js visualization of the social network
     * 
     * @param users The list of users in the network
     * @param content The list of content items
     * @param interactions The list of user interactions
     * @return The path to the generated HTML file
     */
    fun generateVisualization(
        users: Collection<User>,
        content: Collection<Content>,
        interactions: List<Interaction>
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val directory = File(context.filesDir, "network_visualizations")
        directory.mkdirs()
        
        val htmlFile = File(directory, "network_${timestamp}.html")
        
        // Generate nodes and links for D3.js
        val nodes = mutableListOf<Map<String, Any>>()
        val links = mutableListOf<Map<String, Any>>()
        
        // Add user nodes
        users.forEach { user ->
            nodes.add(mapOf(
                "id" to user.id,
                "name" to user.name,
                "type" to if (user.isAgent) "agent" else "user",
                "interests" to user.interests.joinToString(", ")
            ))
        }
        
        // Add content nodes
        content.forEach { contentItem ->
            nodes.add(mapOf(
                "id" to contentItem.id,
                "name" to contentItem.title,
                "type" to "content",
                "description" to contentItem.content,
                "tags" to contentItem.tags.joinToString(", ")
            ))
        }
        
        // Add links for user connections
        users.forEach { user ->
            user.connections.forEach { connectionId ->
                links.add(mapOf(
                    "source" to user.id,
                    "target" to connectionId,
                    "type" to "connection"
                ))
            }
        }
        
        // Add links for content authorship
        content.forEach { contentItem ->
            links.add(mapOf(
                "source" to contentItem.authorId,
                "target" to contentItem.id,
                "type" to "authored"
            ))
        }
        
        // Add links for interactions
        interactions.forEach { interaction ->
            links.add(mapOf(
                "source" to interaction.userId,
                "target" to interaction.contentId,
                "type" to if (interaction.liked) "liked" else "disliked"
            ))
        }
        
        // Convert to JSON
        val nodesJson = nodes.joinToString(",\n") { node ->
            """
            {
                "id": "${node["id"]}",
                "name": "${node["name"]}",
                "type": "${node["type"]}",
                ${if (node["type"] == "content") "\"description\": \"${node["description"]}\", \"tags\": \"${node["tags"]}\"" else "\"interests\": \"${node["interests"]}\""}
            }
            """.trimIndent()
        }
        
        val linksJson = links.joinToString(",\n") { link ->
            """
            {
                "source": "${link["source"]}",
                "target": "${link["target"]}",
                "type": "${link["type"]}"
            }
            """.trimIndent()
        }
        
        // Create the HTML content with D3.js visualization
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Social Network Visualization</title>
                <script src="https://d3js.org/d3.v7.min.js"></script>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 0;
                        padding: 20px;
                    }
                    #graph {
                        width: 100%;
                        height: 800px;
                        border: 1px solid #ccc;
                    }
                    .node {
                        stroke: #fff;
                        stroke-width: 2px;
                    }
                    .link {
                        stroke-opacity: 0.6;
                    }
                    .node-label {
                        font-size: 12px;
                        pointer-events: none;
                    }
                    .tooltip {
                        position: absolute;
                        background-color: rgba(255, 255, 255, 0.9);
                        border: 1px solid #ddd;
                        border-radius: 5px;
                        padding: 10px;
                        font-size: 14px;
                        max-width: 300px;
                        z-index: 10;
                        visibility: hidden;
                    }
                    h1 {
                        color: #333;
                    }
                    .stats {
                        margin-bottom: 20px;
                        padding: 10px;
                        background-color: #f5f5f5;
                        border-radius: 5px;
                    }
                </style>
            </head>
            <body>
                <h1>Social Network Visualization</h1>
                
                <div class="stats">
                    <p><strong>Users:</strong> ${users.size}</p>
                    <p><strong>Content Items:</strong> ${content.size}</p>
                    <p><strong>Interactions:</strong> ${interactions.size}</p>
                    <p><strong>Generated:</strong> ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}</p>
                </div>
                
                <div id="graph"></div>
                <div id="tooltip" class="tooltip"></div>
                
                <script>
                    // Graph data
                    const nodes = [
                        $nodesJson
                    ];
                    
                    const links = [
                        $linksJson
                    ];
                    
                    // Create the visualization
                    const width = document.getElementById('graph').clientWidth;
                    const height = document.getElementById('graph').clientHeight;
                    
                    const svg = d3.select("#graph")
                        .append("svg")
                        .attr("width", width)
                        .attr("height", height);
                    
                    const tooltip = d3.select("#tooltip");
                    
                    // Define node colors based on type
                    const nodeColors = {
                        "user": "#4285F4",  // Blue for real users
                        "agent": "#34A853", // Green for AI agents
                        "content": "#FBBC05" // Yellow for content
                    };
                    
                    // Define link colors based on type
                    const linkColors = {
                        "connection": "#999999", // Gray for connections
                        "authored": "#4285F4",   // Blue for authorship
                        "liked": "#34A853",      // Green for likes
                        "disliked": "#EA4335"    // Red for dislikes
                    };
                    
                    // Create a force simulation
                    const simulation = d3.forceSimulation(nodes)
                        .force("link", d3.forceLink(links).id(function(d) { return d.id; }).distance(100))
                        .force("charge", d3.forceManyBody().strength(-300))
                        .force("center", d3.forceCenter(width / 2, height / 2))
                        .force("collision", d3.forceCollide().radius(30));
                    
                    // Create links
                    const link = svg.append("g")
                        .selectAll("line")
                        .data(links)
                        .enter()
                        .append("line")
                        .attr("class", "link")
                        .attr("stroke", function(d) { return linkColors[d.type] || "#999"; })
                        .attr("stroke-width", 2);
                    
                    // Create nodes
                    const node = svg.append("g")
                        .selectAll("circle")
                        .data(nodes)
                        .enter()
                        .append("circle")
                        .attr("class", "node")
                        .attr("r", function(d) { return d.type === "content" ? 10 : 15; })
                        .attr("fill", function(d) { return nodeColors[d.type] || "#999"; })
                        .call(d3.drag()
                            .on("start", dragstarted)
                            .on("drag", dragged)
                            .on("end", dragended));
                    
                    // Add labels to nodes
                    const label = svg.append("g")
                        .selectAll("text")
                        .data(nodes)
                        .enter()
                        .append("text")
                        .attr("class", "node-label")
                        .attr("text-anchor", "middle")
                        .attr("dy", ".35em")
                        .text(function(d) { return d.name.length > 15 ? d.name.substring(0, 15) + "..." : d.name; });
                    
                    // Add tooltips
                    node.on("mouseover", function(event, d) {
                        let tooltipContent = "<strong>" + d.name + "</strong><br>";
                        
                        if (d.type === "user" || d.type === "agent") {
                            tooltipContent += "Type: " + (d.type === "user" ? "Real User" : "AI Agent") + "<br>";
                            tooltipContent += "Interests: " + d.interests + "<br>";
                        } else if (d.type === "content") {
                            tooltipContent += "Type: Content<br>";
                            tooltipContent += "Description: " + d.description + "<br>";
                            tooltipContent += "Tags: " + d.tags + "<br>";
                        }
                        
                        tooltip
                            .html(tooltipContent)
                            .style("left", (event.pageX + 10) + "px")
                            .style("top", (event.pageY - 10) + "px")
                            .style("visibility", "visible");
                    })
                    .on("mouseout", function() {
                        tooltip.style("visibility", "hidden");
                    });
                    
                    // Update positions on each tick of the simulation
                    simulation.on("tick", function() {
                        link
                            .attr("x1", function(d) { return d.source.x; })
                            .attr("y1", function(d) { return d.source.y; })
                            .attr("x2", function(d) { return d.target.x; })
                            .attr("y2", function(d) { return d.target.y; });
                        
                        node
                            .attr("cx", function(d) { return d.x = Math.max(20, Math.min(width - 20, d.x)); })
                            .attr("cy", function(d) { return d.y = Math.max(20, Math.min(height - 20, d.y)); });
                        
                        label
                            .attr("x", function(d) { return d.x; })
                            .attr("y", function(d) { return d.y - 20; });
                    });
                    
                    // Drag functions
                    function dragstarted(event, d) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        d.fx = d.x;
                        d.fy = d.y;
                    }
                    
                    function dragged(event, d) {
                        d.fx = event.x;
                        d.fy = event.y;
                    }
                    
                    function dragended(event, d) {
                        if (!event.active) simulation.alphaTarget(0);
                        d.fx = null;
                        d.fy = null;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        // Write the HTML content to the file
        htmlFile.writeText(htmlContent)
        
        return htmlFile.absolutePath
    }
}

/**
 * Composable function to display the network visualization in a WebView
 */
@Composable
fun NetworkVisualizationView(htmlFilePath: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl("file://$htmlFilePath")
            }
        }
    )
} 