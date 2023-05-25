package com.sdcardstoryteller

import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.sdcardstoryteller.model.StageNode
import com.sdcardstoryteller.model.StoryPack
import com.sdcardstoryteller.ui.theme.SDCardStoryTellerTheme
import java.io.File

fun getSubStringBeforeLastMark(str: String, mark: String?): String {
    val l = str.lastIndexOf(mark!!)
    return if (l == -1 || l == 0) "" else str.substring(0, l)
}

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadStories()
    }
    @RequiresApi(Build.VERSION_CODES.R)
    fun changeStage(node : StageNode) {
        println("changeStage ${node.uuid}")
        setContent {
            SDCardStoryTellerTheme {
                Surface(
                    modifier= Modifier
                        .fillMaxSize()
                        .verticalScroll(state = rememberScrollState()) ,
                    color = Color.Transparent //MaterialTheme.colorScheme.background
                ) {
                    PlayNode(node = node, activity = this)
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    fun reloadStories() {
        val ctx = this
        setContent {
            SDCardStoryTellerTheme {
                Surface(modifier= Modifier
                    .fillMaxSize()
                    .verticalScroll(state = rememberScrollState()),
                    color = Color.Transparent
                ) {
                    var contentfound = false
                    for(dir in getExternalFilesDirs(null)) {
                        val path = File(getSubStringBeforeLastMark(dir.absolutePath,"/Android/") +
                                        File.separator + ".content")
                        println("Searching on $path")
                        if (path.exists()) {
                            println("Found content on $path")
                            contentfound = true
                            if (Environment.isExternalStorageManager()) {
                                val files: Array<out File>? = path.listFiles()
                                if (files == null) {
                                    Text("Répertoire .content trouvé mais aucune histoire à l'intérieur", color = Color.Yellow)
                                } else {
                                    Column() {
                                        Text(
                                            "Appui court pour écouter le titre, appui long pour jouer l'histoire",
                                            color = Color.White
                                        )
                                        for (file in files) {
                                            Story(file, activity = ctx)
                                        }
                                    }
                                }
                            } else {
                                Column() {
                                    Text("L'application a besoin des droits d'accès à la SDCard, merci de changer ses paramètres", color=Color.White)
                                    Button(onClick = { reloadStories() }) {
                                        Text("Réessayer")
                                    }
                                }
                                val intent = Intent()
                                intent.action =
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                val uri = Uri.fromParts("package", this.packageName, null)
                                intent.data = uri
                                startActivity(intent)
                            }
                        }
                    }
                    if (!contentfound) {
                        Column() {
                            Text("Merci d'insérer une carte SD avec du contenu compatible", color=Color.White)
                            Button(onClick = { reloadStories() }) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayNode(node : StageNode, activity: MainActivity, forceMenuItem: Boolean = false)  {
    // Option: display image and play sound on click, change stage on long click
    if ((forceMenuItem)||((node.image!=null)&&(node.audio!=null))) {
        println("Play one option ${node.uuid} -> ${node.okTransition.actionNode.options[0].uuid}")
        val bmp = if (node.image!=null) {
            BitmapFactory.decodeByteArray(node.image.rawData, 0, node.image.rawData.size)
                .asImageBitmap()
        } else {
            BitmapFactory.decodeResource(activity.resources, R.drawable.no_image).asImageBitmap()
        }
        Image(painter = BitmapPainter(bmp), contentDescription = "Option ${node.uuid}", modifier = Modifier
            .combinedClickable(
                onClick = {
                    val mediaSource = AudioAssetMediaDataSource(node.audio)
                    MediaPlayer().apply {
                        setDataSource(mediaSource)
                        setOnCompletionListener { release() }
                        prepareAsync()
                        setOnPreparedListener { start() }
                    }
                },
                onLongClick = {
                    activity.changeStage(node.okTransition.actionNode.options[0])
                }
            )
            .size(320.dp, 240.dp))
    } 
    // Menu: display options
    else if(node.image==null && node.audio!=null && !node.controlSettings.isPauseEnabled) {
        println("Play options of ${node.uuid}")
        Column {
            for (opt in node.okTransition.actionNode.options) {
                PlayNode(node = opt, activity, forceMenuItem = true)
            }
            Button(onClick = {
                if (node.homeTransition != null) {
                    activity.changeStage(node.homeTransition.actionNode.options[0])
                } else {
                    activity.reloadStories()
                }
            }) {
                    Text("RETOUR")
                }
        }
        val mediaSource = AudioAssetMediaDataSource(node.audio)
        MediaPlayer().apply {
            setDataSource(mediaSource)
            setOnCompletionListener { release() }
            prepareAsync()
            setOnPreparedListener { start() }
        }
    }
    // Story
    else if(node.image==null && node.audio!=null && node.controlSettings.isPauseEnabled) {
        Column {
            Text("Histoire en cours (uuid:${node.uuid})", color=Color.White)
            val mediaSource = AudioAssetMediaDataSource(node.audio)
            val mp = MediaPlayer()
            mp.apply {
                setDataSource(mediaSource)
                setOnCompletionListener { release() }
                prepareAsync()
                setOnPreparedListener { start() }
            }
            Button(onClick = {
                if (mp.isPlaying) {
                    mp.pause()
                } else {
                    mp.start()
                }
            }) {
                Text("Pause/Reprendre")
            }
            Button(onClick = {
                mp.stop()
                if (node.homeTransition != null) {
                    activity.changeStage(node.homeTransition.actionNode.options[0])
                } else {
                    activity.reloadStories()
                }
            }) {
                Text("RETOUR")
            }
        }
    } else {
        Text("Unknown node type")
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun Story(dir: File, activity: MainActivity) {
    val reader = FsStoryPackReader()
    val storyPack = try {
        reader.read(dir)
    } catch (e: Throwable) {
        Text("Cannot load $dir : $e", color= Color.Yellow)
        StoryPack.EMPTY
    }
    if (storyPack != StoryPack.EMPTY) {
        PlayNode(node = storyPack.stageNodes[0], activity)
        //Text("story ${storyPack.uuid}",color=Color.White)
        //if (storyPack.uuid=="B27CE938") {
        //    println("story: ${storyPack.uuid}")
        //    for (node in storyPack.stageNodes) {
        //        println("node: $node")
        //    }
        //}
    }
}
