package com.agentic.graph

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A simple graph visualizer that generates HTML files with D3.js visualizations
 * of the knowledge graph.
 */
class GraphVisualizer {
    
    /**
     * Generates an HTML file with a D3.js visualization of the knowledge graph.
     * 
     * @param concepts The list of concepts in the graph
     * @param relationships The list of relationships in the graph
     * @param iteration The current iteration number
     */
    fun generateVisualization(concepts: Collection<Concept>, relationships: List<Relationship>, iteration: Int) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val directory = File("graph_visualizations")
        directory.mkdirs()
        
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Knowledge Graph Visualization - Iteration $iteration</title>
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
                        fill: #69b3a2;
                        stroke: #fff;
                        stroke-width: 2px;
                    }
                    .link {
                        stroke: #999;
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
                <h1>Knowledge Graph Visualization - Iteration $iteration</h1>
                
                <div class="stats">
                    <p><strong>Concepts:</strong> ${concepts.size}</p>
                    <p><strong>Relationships:</strong> ${relationships.size}</p>
                    <p><strong>Generated:</strong> ${LocalDateTime.now()}</p>
                </div>
                
                <div id="graph"></div>
                <div id="tooltip" class="tooltip"></div>
                
                <script>
                    // Graph data
                    const nodes = ${generateNodesJson(concepts)};
                    const links = ${generateLinksJson(relationships)};
                    
                    // Create the visualization
                    const width = document.getElementById('graph').clientWidth;
                    const height = document.getElementById('graph').clientHeight;
                    
                    const svg = d3.select("#graph")
                        .append("svg")
                        .attr("width", width)
                        .attr("height", height);
                    
                    // Create a tooltip
                    const tooltip = d3.select("#tooltip");
                    
                    // Create a force simulation
                    const simulation = d3.forceSimulation(nodes)
                        .force("link", d3.forceLink(links).id(d => d.id).distance(150))
                        .force("charge", d3.forceManyBody().strength(-300))
                        .force("center", d3.forceCenter(width / 2, height / 2))
                        .force("collide", d3.forceCollide().radius(60));
                    
                    // Create the links
                    const link = svg.append("g")
                        .selectAll("line")
                        .data(links)
                        .enter()
                        .append("line")
                        .attr("class", "link")
                        .attr("stroke-width", 2);
                    
                    // Create the nodes
                    const node = svg.append("g")
                        .selectAll("circle")
                        .data(nodes)
                        .enter()
                        .append("circle")
                        .attr("class", "node")
                        .attr("r", d => Math.max(5, Math.min(20, 5 + d.connections * 2)))
                        .on("mouseover", function(event, d) {
                            tooltip.style("visibility", "visible")
                                .html(`<strong>${d.name}</strong><br>${d.description}`)
                                .style("left", (event.pageX + 10) + "px")
                                .style("top", (event.pageY - 10) + "px");
                        })
                        .on("mouseout", function() {
                            tooltip.style("visibility", "hidden");
                        })
                        .call(d3.drag()
                            .on("start", dragstarted)
                            .on("drag", dragged)
                            .on("end", dragended));
                    
                    // Add labels to the nodes
                    const labels = svg.append("g")
                        .selectAll("text")
                        .data(nodes)
                        .enter()
                        .append("text")
                        .attr("class", "node-label")
                        .attr("text-anchor", "middle")
                        .attr("dy", 4)
                        .text(d => d.name.length > 15 ? d.name.substring(0, 15) + "..." : d.name);
                    
                    // Update positions on each tick of the simulation
                    simulation.on("tick", () => {
                        link
                            .attr("x1", d => d.source.x)
                            .attr("y1", d => d.source.y)
                            .attr("x2", d => d.target.x)
                            .attr("y2", d => d.target.y);
                        
                        node
                            .attr("cx", d => d.x = Math.max(20, Math.min(width - 20, d.x)))
                            .attr("cy", d => d.y = Math.max(20, Math.min(height - 20, d.y)));
                        
                        labels
                            .attr("x", d => d.x)
                            .attr("y", d => d.y - 15);
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
        
        File(directory, "graph_visualization_${iteration}_$timestamp.html").writeText(htmlContent)
        println("Generated visualization for iteration $iteration")
    }
    
    /**
     * Generates a JSON representation of the nodes for D3.js.
     */
    private fun generateNodesJson(concepts: Collection<Concept>): String {
        val nodesList = concepts.map { concept ->
            """
                {
                    "id": "${concept.id}",
                    "name": "${escapeJson(concept.name)}",
                    "description": "${escapeJson(concept.description)}",
                    "connections": 0
                }
            """.trimIndent()
        }
        
        return "[\n${nodesList.joinToString(",\n")}\n]"
    }
    
    /**
     * Generates a JSON representation of the links for D3.js.
     */
    private fun generateLinksJson(relationships: List<Relationship>): String {
        val linksList = relationships.map { relationship ->
            """
                {
                    "source": "${relationship.source}",
                    "target": "${relationship.target}",
                    "type": "${escapeJson(relationship.type)}"
                }
            """.trimIndent()
        }
        
        return "[\n${linksList.joinToString(",\n")}\n]"
    }
    
    /**
     * Escapes special characters in JSON strings.
     */
    private fun escapeJson(text: String): String {
        return text.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
} 