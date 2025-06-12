import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Delete
import coil.compose.rememberAsyncImagePainter
import com.example.medpal.ChatMessage
import com.example.medpal.R
import com.example.medpal.ChatViewModel
import com.example.medpal.playSound
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var lastMessageCount by remember { mutableStateOf(0) }

    var showProfileDialog by remember { mutableStateOf(false) }


    var showWaitLabel by remember { mutableStateOf(false) }


    LaunchedEffect(viewModel.isLoading) {
        showWaitLabel = false
        if(viewModel.isLoading){
            delay(20000)
            if(viewModel.isLoading) showWaitLabel = true
        }
    }

    // --- WebSocket: Connect on screen entry ---
    LaunchedEffect(Unit) {
        viewModel.initWebSocket("user123") // Replace with actual user/client id
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.size > lastMessageCount) {
            val lastMsg = viewModel.messages.last()
            if (lastMsg.isUser) {
                playSound(context, R.raw.msg_sent)
            } else {
                playSound(context, R.raw.msg_received)
            }
            lastMessageCount = viewModel.messages.size
        }
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val groupedMessages = viewModel.messages.groupBy { message ->
        dateFormat.format(Date(message.timestamp))
    }

    var pendingFileType by remember { mutableStateOf<String?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pendingFileType != null) {
            val fileName = uri.lastPathSegment ?: "file"
            viewModel.uploadFile(uri)
            viewModel.addFileMessage(
                fileUri = uri.toString(),
                fileType = pendingFileType!!,
                fileName = fileName
            )
            pendingFileType = null
        }
    }

    val requestImagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingFileType = "image"
            filePickerLauncher.launch(arrayOf("image/*"))
        } else {
            Toast.makeText(context, "Image permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val requestVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingFileType = "video"
            filePickerLauncher.launch(arrayOf("video/*"))
        } else {
            Toast.makeText(context, "Video permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.clickable { showProfileDialog = true },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.aidra_profile),
                                contentDescription = "Profile Image",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Text("Aidra", fontWeight = FontWeight.Normal, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF23280F),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1E1E1E) // background color
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {

            // Background Image

            Image(
                painter = painterResource(id = R.drawable.chat_bg), // your image name
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MeditationWidget(viewModel) // Meditation tool

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedMessages.forEach { (date, messagesForDate) ->
                        item { WhatsAppDateHeader(date) }
                        items(messagesForDate.filter { it.text.isNotBlank() || it.fileUrl != null }) { message ->
                            MessageBubble(
                                message = message,
                                onDelete = { messageId ->
                                    viewModel.deleteMessageById(messageId)
                                }
                            )
                        }
                    }
                    if (viewModel.isLoading) {
                        item { TypingBubble() }
                        if(showWaitLabel){
                            item{
                                Text(
                                    text = "Responses may take up to 40 seconds ⏳",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, top = 4.dp, end = 24.dp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                var showAttachmentSheet by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Message Input
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(end = 8.dp),
                        placeholder = { Text("Type your message...") },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF3A2E35),
                            unfocusedContainerColor = Color(0xFF3A2E35),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions.Default,
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendChatMessage(messageText) // <-- WebSocket
                                    messageText = ""
                                }
                            }
                        )
                    )

                    // Attach Button
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier
                            .background(Color(0xFF178582), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = Color.White
                        )
                    }

                    // Send Button
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendChatMessage(messageText) // <-- WebSocket
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank(),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(Color(0xFF178582), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }

                if (showAttachmentSheet) {
                    ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Title at the top
                            Text(
                                text = "Upload Lab Report",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .align(Alignment.Start)
                            )
                            // Image option
                            AttachmentOption(
                                icon = Icons.Default.Image,
                                label = "Upload Report Screenshot",
                                onClick = {
                                    showAttachmentSheet = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val permission =
                                            android.Manifest.permission.READ_MEDIA_IMAGES
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                permission
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            pendingFileType = "image"
                                            filePickerLauncher.launch(arrayOf("image/*"))
                                        } else {
                                            requestImagePermissionLauncher.launch(permission)
                                        }
                                    } else {
                                        pendingFileType = "image"
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    }
                                }
                            )
                            // Document option (no permission needed)
                            AttachmentOption(
                                icon = Icons.Default.PictureAsPdf,
                                label = "Upload Report (PDF)",
                                onClick = {
                                    showAttachmentSheet = false
                                    pendingFileType = "pdf"
                                    filePickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            )

                        }
                    }
                }
            }
        }

        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                confirmButton = {},
                text = {
                    Image(
                        painter = painterResource(id = R.drawable.aidra_profile),
                        contentDescription = "Aidra Profile",
                        modifier = Modifier
                            .size(272.dp)
                            .clip(CircleShape)

                    )
                }
            )
        }
    }
}

// --- Message bubble UI ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage, onDelete: (String)->Unit) {

    val context = LocalContext.current

    val bubbleColor = if (message.isUser) Color(0xFFDCF8C6) else Color.LightGray
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onDoubleClick = {
                playSound(context,R.raw.msg_delete)
                onDelete(message.id)
            }
        ),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = bubbleColor,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Column {
                    when (message.fileType) {
                        "image" -> {
                            message.fileUrl?.let { url ->
                                Image(
                                    painter = rememberAsyncImagePainter(url),
                                    contentDescription = message.fileName ?: "Image",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .defaultMinSize(minWidth = 120.dp, minHeight = 120.dp)
                                        .sizeIn(maxWidth = 220.dp, maxHeight = 320.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        "video" -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .size(80.dp)
                                    .clickable { /* play video logic here */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .padding(8.dp)
                                )
                            }
                        }
                        "pdf", "document" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .heightIn(min = 56.dp)
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                                Spacer(Modifier.width(8.dp))
                                Text(message.fileName ?: "Document", color = Color.Black)
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = Color.Black,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        )
                    }
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingBubble() {
    val bubbleColor = Color.LightGray
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .background(
                        color = bubbleColor,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TypingTypewriter()
            }
        }
    }
}

@Composable
fun TypingTypewriter(
    text: String = "Aidra is thinking...",
    typingDelay: Long = 50L
) {
    var visibleText by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        visibleText = ""
        for (i in 1..text.length) {
            visibleText = text.take(i)
            delay(typingDelay)
        }
    }
    Text(
        text = visibleText,
        color = Color.DarkGray,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    )
}

@Composable
fun WhatsAppDateHeader(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .background(
                    color = Color(0x66000000),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AttachmentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 18.sp)
    }
}


@Composable
fun MeditationWidget(viewModel: ChatViewModel) {
    if (!viewModel.isMeditating) return

    val timeLeft = viewModel.meditationTimeLeft
    val total = viewModel.meditationTotalTime
    val progress = if (total > 0) timeLeft / total.toFloat() else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFF212121), shape = RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF4CAF50), shape = RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Meditation in progress", color = Color.White)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))
            Text("Sound #${viewModel.meditationSoundIndex}", color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.stopMeditation() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Stop Meditation")
            }
        }
    }
}

