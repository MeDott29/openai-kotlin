package com.example.swipeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.swipeai.ui.theme.SwipeAITheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.aallam.openai.client.OpenAI
import com.aallam.openai.api.http.Timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.IntOffset

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Swipe : Screen("swipe")
    object Visualization : Screen("visualization")
    object Insights : Screen("insights")
}

class MainViewModel(private val openAI: OpenAI) : ViewModel() {
    // Create the agentic social network
    private val socialNetwork = AgenticSocialNetwork(openAI)
    
    // State for content cards
    private val _contentState = MutableStateFlow<List<ContentCard>>(emptyList())
    val contentState: StateFlow<List<ContentCard>> = _contentState.asStateFlow()
    
    // State for loading indicator
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Current visualization path
    private var _currentVisualizationPath = MutableStateFlow<String?>(null)
    val currentVisualizationPath: StateFlow<String?> = _currentVisualizationPath.asStateFlow()
    
    // Access to the social network
    fun getSocialNetwork() = socialNetwork
    
    // Initialize the social network
    suspend fun initializeNetwork(username: String) {
        _isLoading.value = true
        socialNetwork.initialize(username)
        loadNextContent()
        _isLoading.value = false
    }
    
    // Load next content for swiping
    fun loadNextContent() {
        viewModelScope.launch {
            _isLoading.value = true
            val newContent = socialNetwork.getContentForSwiping()
            _contentState.value = newContent
            _isLoading.value = false
        }
    }
    
    // Record user interaction
    fun recordInteraction(contentId: String, liked: Boolean) {
        socialNetwork.recordUserInteraction(contentId, liked)
        loadNextContent()
    }
    
    // Generate network visualization
    suspend fun generateVisualization(): String? {
        _isLoading.value = true
        val path = socialNetwork.generateVisualization()
        _currentVisualizationPath.value = path
        _isLoading.value = false
        return path
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    // Create OpenAI client
    private val openAI by lazy {
        OpenAI(
            token = System.getenv("OPENAI_API_KEY") ?: "",
            timeout = Timeout(socket = 60.seconds)
        )
    }
    
    // ViewModel
    private lateinit var viewModel: MainViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModel
        viewModel = MainViewModel(openAI)
        
        // Set context for visualization
        viewModel.getSocialNetwork().setContext(this)
        
        setContent {
            SwipeAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val contentState = viewModel.contentState.collectAsState()
                    val isLoading = viewModel.isLoading.collectAsState()
                    val visualizationPath = viewModel.currentVisualizationPath.collectAsState()
                    
                    NavHost(navController = navController, startDestination = Screen.Welcome.route) {
                        composable(Screen.Welcome.route) {
                            WelcomeScreen(
                                onStartClick = { username ->
                                    // Initialize the social network with the username
                                    CoroutineScope(Dispatchers.Main).launch {
                                        viewModel.initializeNetwork(username)
                                        navController.navigate(Screen.Swipe.route)
                                    }
                                }
                            )
                        }
                        
                        composable(Screen.Swipe.route) {
                            SwipeScreen(
                                contentState = contentState.value,
                                isLoading = isLoading.value,
                                onSwipeLeft = { contentId ->
                                    viewModel.recordInteraction(contentId, false)
                                },
                                onSwipeRight = { contentId ->
                                    viewModel.recordInteraction(contentId, true)
                                },
                                onVisualizationClick = {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        viewModel.generateVisualization()
                                        navController.navigate(Screen.Visualization.route)
                                    }
                                },
                                onInsightsClick = {
                                    navController.navigate(Screen.Insights.route)
                                }
                            )
                        }
                        
                        composable(Screen.Visualization.route) {
                            VisualizationScreen(
                                isLoading = isLoading.value,
                                visualizationPath = visualizationPath.value,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable(Screen.Insights.route) {
                            val insights by viewModel.getSocialNetwork().insightsFlow.collectAsState()
                            val metrics by viewModel.getSocialNetwork().graphMetricsFlow.collectAsState()
                            
                            InsightsScreen(
                                insights = insights.map { it.description },
                                metrics = metrics,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ContentCard(
    val id: String,
    val title: String,
    val content: String,
    val author: String,
    val tags: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onStartClick: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Swipe AI",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Text(
            text = "Experience a social network powered by agentic deep graph reasoning",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Enter your name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        
        Button(
            onClick = { 
                if (username.isNotBlank()) {
                    onStartClick(username)
                }
            },
            enabled = username.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Exploring")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    contentState: List<ContentCard>,
    isLoading: Boolean,
    onSwipeLeft: (String) -> Unit,
    onSwipeRight: (String) -> Unit,
    onVisualizationClick: () -> Unit,
    onInsightsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App bar
        CenterAlignedTopAppBar(
            title = { Text("Swipe AI") },
            actions = {
                IconButton(onClick = onVisualizationClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Visualization"
                    )
                }
                IconButton(onClick = onInsightsClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Insights"
                    )
                }
            }
        )
        
        if (isLoading) {
            // Loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (contentState.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No content available. Try again later.")
            }
        } else {
            // Content card for swiping
            val currentCard = contentState.first()
            
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Card that can be swiped
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (offsetX > 100) {
                                        // Swiped right (like)
                                        onSwipeRight(currentCard.id)
                                    } else if (offsetX < -100) {
                                        // Swiped left (dislike)
                                        onSwipeLeft(currentCard.id)
                                    }
                                    // Reset position
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            )
                        }
                ) {
                    // Card content
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = currentCard.title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                text = "By ${currentCard.author}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Text(
                                text = currentCard.content,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // Tags
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                currentCard.tags.forEach { tag ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { },
                                        label = { Text(tag) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Swipe instructions
                            Text(
                                text = "Swipe left to dislike, right to like",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // Overlay indicators for swipe direction
                    when {
                        offsetX > 50 -> {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "LIKE",
                                    color = Color.Green,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                        offsetX < -50 -> {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "DISLIKE",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizationScreen(
    isLoading: Boolean,
    visualizationPath: String?,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App bar
        CenterAlignedTopAppBar(
            title = { Text("Social Network Visualization") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        
        if (isLoading) {
            // Loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    Text("Generating visualization...")
                }
            }
        } else if (visualizationPath == null) {
            // Error state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Failed to generate visualization")
            }
        } else {
            // Display the visualization
            NetworkVisualizationView(htmlFilePath = visualizationPath)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    insights: List<String>,
    metrics: Map<String, Double>,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App bar
        CenterAlignedTopAppBar(
            title = { Text("Network Insights") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Network metrics section
            item {
                Text(
                    text = "Network Metrics",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        metrics.forEach { (key, value) ->
                            val formattedValue = when {
                                key.contains("Count") -> value.toInt().toString()
                                else -> String.format("%.2f", value)
                            }
                            
                            val displayKey = key.replace(Regex("([A-Z])"), " $1")
                                .replaceFirstChar { it.uppercase() }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = displayKey)
                                Text(
                                    text = formattedValue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            if (key != metrics.keys.last()) {
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            
            // Network insights section
            item {
                Text(
                    text = "Network Insights",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                )
            }
            
            items(insights) { insight ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = insight)
                    }
                }
            }
            
            // If no insights available
            if (insights.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No insights available yet. Interact with more content to generate insights.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
} 