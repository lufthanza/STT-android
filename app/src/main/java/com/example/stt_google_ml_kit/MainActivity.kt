// MainActivity.kt
package com.example.videosubtitlegenerator // Ganti dengan package name Anda

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.videosubtitlegenerator.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Task
import com.google.mlkit.speech.RecognitionOptions
import com.google.mlkit.speech.RecognitionRequest
import com.google.mlkit.speech.RecognitionResult
import com.google.mlkit.speech.Speech
import com.google.mlkit.speech.SpeechRecognizerOptions
import com.google.mlkit.speech.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Launcher untuk meminta izin
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showToast("Izin diberikan.")
                openVideoPicker()
            } else {
                showToast("Izin diperlukan untuk memilih video.")
            }
        }

    // Launcher untuk membuka pemilih file
    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch {
                        processVideo(uri)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectVideo.setOnClickListener {
            checkAndRequestPermission()
        }
    }

    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openVideoPicker()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
        }
        pickVideoLauncher.launch(intent)
    }

    private suspend fun processVideo(videoUri: Uri) {
        setLoading(true, "Memulai proses...")

        // Dapatkan nama file video asli
        val videoFileName = getFileName(videoUri) ?: "video_${System.currentTimeMillis()}"
        val baseName = videoFileName.substringBeforeLast(".")

        // Lokasi file audio sementara
        val audioOutputFile = File(cacheDir, "temp_audio.wav")

        try {
            // ----- Langkah 1: Ekstrak Audio dengan FFmpeg-Kit -----
            setLoading(true, "1/4: Mengekstrak audio dari video...")
            val session = FFmpegKit.execute("-y -i \"${getFilePathFromUri(videoUri)}\" -vn -acodec pcm_s16le -ar 16000 -ac 1 \"${audioOutputFile.absolutePath}\"")
            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw Exception("FFmpeg gagal mengekstrak audio. Log: ${session.allLogsAsString}")
            }

            // ----- Langkah 2: Transkripsi dengan ML Kit -----
            setLoading(true, "2/4: Mentranskripsi audio (Bahasa Indonesia)...")
            // Pilih bahasa: "id-ID" untuk Indonesia, "en-US" untuk Inggris
            val transcriptionResult = transcribeAudio(Uri.fromFile(audioOutputFile), "id-ID")

            // ----- Langkah 3: Hasilkan Konten SRT -----
            setLoading(true, "3/4: Membuat file subtitle (.srt)...")
            val srtContent = generateSrtContent(transcriptionResult)
            if (srtContent.isBlank()) {
                throw Exception("Hasil transkripsi kosong.")
            }

            // ----- Langkah 4: Simpan File SRT -----
            setLoading(true, "4/4: Menyimpan file .srt...")
            val srtUri = saveSrtToFile(baseName, srtContent)

            setLoading(false, "Selesai! Subtitle disimpan di:\n${srtUri?.path}")
            showToast("Proses berhasil!")

        } catch (e: Exception) {
            setLoading(false, "Error: ${e.message}")
            e.printStackTrace()
        } finally {
            // Hapus file audio sementara
            if (audioOutputFile.exists()) {
                audioOutputFile.delete()
            }
        }
    }

    private suspend fun transcribeAudio(audioUri: Uri, languageCode: String): RecognitionResult {
        val recognitionOptions = RecognitionOptions.Builder()
            .setRecognizerMode(SpeechRecognizerOptions.RECOGNIZER_MODE_OFFLINE)
            .setSpeechLanguage(languageCode)
            .setEnableWordTimeOffsets(true) // Ini kunci untuk mendapatkan timestamp!
            .build()

        val speechRecognizer = Speech.createRecognizer(this, recognitionOptions)
        val request = RecognitionRequest.Builder()
            .setAudio(audioUri)
            .build()

        return speechRecognizer.recognize(request).await()
    }

    private fun generateSrtContent(result: RecognitionResult): String {
        val srtBuilder = StringBuilder()
        var segmentIndex = 1

        result.alternatives.firstOrNull()?.words?.let { words ->
            if (words.isNotEmpty()) {
                // Kelompokkan kata-kata menjadi segmen subtitle
                // Di sini kita buat segmen baru setiap ~8 kata untuk contoh sederhana
                words.chunked(8).forEach { segmentWords ->
                    val firstWord = segmentWords.first()
                    val lastWord = segmentWords.last()
                    val startTime = formatSrtTimestamp(firstWord.startOffsetMillis)
                    val endTime = formatSrtTimestamp(lastWord.endOffsetMillis)
                    val text = segmentWords.joinToString(" ") { it.word }

                    srtBuilder.append("$segmentIndex\n")
                    srtBuilder.append("$startTime --> $endTime\n")
                    srtBuilder.append("$text\n\n")
                    segmentIndex++
                }
            }
        }
        return srtBuilder.toString()
    }

    private suspend fun saveSrtToFile(baseFileName: String, content: String): Uri? {
        return withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseFileName.srt")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/MySubtitles") // Simpan di folder Dokumen/MySubtitles
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
            }
            uri
        }
    }

    // --- Helper Functions ---

    private fun formatSrtTimestamp(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val milliseconds = millis % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        // FFmpegKit membutuhkan path file absolut, bukan content URI
        return when {
            // Cek apakah URI adalah file, jika ya langsung gunakan pathnya
            "file".equals(uri.scheme, ignoreCase = true) -> uri.path
            else -> {
                // Salin file dari content URI ke file sementara di cache
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val tempFile = File(cacheDir, "temp_video_input.mp4")
                    val outputStream = tempFile.outputStream()
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    tempFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path?.let { File(it).name }
        }
        return fileName
    }

    private fun setLoading(isLoading: Boolean, status: String) {
        binding.tvStatus.text = "Status: $status"
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSelectVideo.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSelectVideo.isEnabled = true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}