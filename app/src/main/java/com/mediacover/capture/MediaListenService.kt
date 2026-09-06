package com.mediacover.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

class MediaListenService : android.app.Service() {
    private lateinit var sessionManager: MediaSessionManager
    private var currentController: MediaController? = null
    private var lastTrack: TrackInfo? = null
    private val gson = Gson()
    private val client = OkHttpClient()
    private val ioPool = Executors.newSingleThreadExecutor()

    companion object{
        const val ACTION_TRACK_CHANGED = "com.mediacover.capture.TRACK_CHANGED"
        const val FILE_COVER = "now_cover.jpg"
        const val FILE_JSON = "now_track.json"
        const val FILE_COVER_TMP = "now_cover.jpg.tmp"
        const val FILE_JSON_TMP = "now_track.json.tmp"
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pickBestController(controllers)
    }

    private val metaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(meta: android.media.MediaMetadata?) {
            super.onMetadataChanged(meta)
            handleNewMeta(meta)
        }
    }

    private fun pickBestController(list: MutableList<MediaController>?) {
        currentController?.unregisterCallback(metaCallback)
        currentController = null
        if(list.isNullOrEmpty()) return
        val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val target = playing ?: list.first()
        currentController = target
        target.registerCallback(metaCallback)
        handleNewMeta(target.metadata)
    }

    private fun getOutputRoot():Any {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val uriStr = pref.getString("save_uri",null)
        return if(uriStr.isNullOrEmpty()){
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }else{
            DocumentFile.fromTreeUri(this, Uri.parse(uriStr))!!
        }
    }

    private fun writeToSAF(root:DocumentFile, name:String, tmpName:String, bytes:ByteArray){
        root.findFile(tmpName)?.delete()
        val tmpDoc = root.createFile("*/*",tmpName) ?: return
        contentResolver.openOutputStream(tmpDoc.uri)?.use { os ->
            os.write(bytes)
        }
        root.findFile(name)?.delete()
        tmpDoc.renameTo(name)
    }

    private fun writeToFile(dir:File, name:String, tmpName:String, bytes:ByteArray){
        val tmp = File(dir,tmpName)
        val final = File(dir,name)
        tmp.delete()
        tmp.writeBytes(bytes)
        final.delete()
        tmp.renameTo(final)
    }

    private fun handleNewMeta(meta: android.media.MediaMetadata?) {
        meta ?: return
        val title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
        val album = meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
        val newTrack = TrackInfo(title, artist, album)
        if(newTrack == lastTrack) return
        lastTrack = newTrack

        ioPool.execute {
            val jsonBytes = gson.toJson(newTrack).toByteArray(Charsets.UTF_8)
            var coverBytes:ByteArray? = null

            val artBmp: Bitmap? = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            artBmp?.let { bmp ->
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG,90,bos)
                coverBytes = bos.toByteArray()
            }

            if(coverBytes == null){
                val artUriStr = meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                artUriStr?.let { uriStr ->
                    val uri = Uri.parse(uriStr)
                    when(uri.scheme){
                        "content" -> {
                            try {
                                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver,uri)
                                val bos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG,90,bos
